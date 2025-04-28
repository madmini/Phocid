package org.sunsetware.phocid

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sunsetware.phocid.service.CustomizedBitmapLoader
import org.sunsetware.phocid.utils.Random

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val coroutineScope = MainScope()
    private val timerMutex = Mutex()
    @Volatile private var timerTarget = -1L
    @Volatile private var timerFinishLastTrack = true
    @Volatile private var playOnOutputDeviceConnection = false
    @Volatile private var audioOffloading = true
    @Volatile private var lastIndex = null as Int?
    @Volatile private var reshuffleOnRepeat = false
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo?>?) {
                if (playOnOutputDeviceConnection) {
                    mediaSession?.player?.play()
                }
            }
        }

    // Create your player and media session in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        val player =
            CustomizedPlayer(
                ExoPlayer.Builder(this)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build()
                    .apply {
                        trackSelectionParameters =
                            trackSelectionParameters
                                .buildUpon()
                                .setAudioOffloadPreferences(
                                    AudioOffloadPreferences.Builder()
                                        .setAudioOffloadMode(
                                            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                                        )
                                        .build()
                                )
                                .build()
                    }
            )
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.inner.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (
                        events.containsAny(
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                        )
                    ) {
                        runBlocking {
                            timerMutex.withLock {
                                if (
                                    timerTarget >= 0 &&
                                        SystemClock.elapsedRealtime() >= timerTarget &&
                                        timerFinishLastTrack
                                ) {
                                    player.pause()
                                    timerTarget = -1
                                    mediaSession?.updateSessionExtras {
                                        putLong(TIMER_TARGET_KEY, -1)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    player.updateAudioOffloading(audioOffloading)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (
                        player.currentMediaItemIndex == 0 &&
                            lastIndex == player.mediaItemCount - 1 &&
                            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
                            player.shuffleModeEnabled &&
                            reshuffleOnRepeat &&
                            player.mediaItemCount > 2
                    ) {
                        player.seekTo(Random.nextInt(0, player.mediaItemCount - 1), 0)
                        player.disableShuffle()
                        player.enableShuffle()
                    }
                    lastIndex = player.currentMediaItemIndex
                }
            }
        )
        coroutineScope.launch {
            while (isActive) {
                timerMutex.withLock {
                    if (timerTarget >= 0 && SystemClock.elapsedRealtime() >= timerTarget) {
                        if (!timerFinishLastTrack) {
                            player.pause()
                            timerTarget = -1
                            mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                        } else if (!player.isPlaying) {
                            player.pause()
                            timerTarget = -1
                            mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                        }
                    }
                }

                delay(1.seconds)
            }
        }
        mediaSession =
            MediaSession.Builder(this, player)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        packageManager.getLaunchIntentForPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            when (customCommand.customAction) {
                                SET_TIMER_COMMAND -> {
                                    runBlocking {
                                        timerMutex.withLock {
                                            val target = args.getLong(TIMER_TARGET_KEY, -1)
                                            val finishLastTrack =
                                                args.getBoolean(TIMER_FINISH_LAST_TRACK_KEY, true)
                                            timerTarget = target
                                            timerFinishLastTrack = finishLastTrack
                                            session.updateSessionExtras {
                                                putLong(TIMER_TARGET_KEY, target)
                                                putBoolean(
                                                    TIMER_FINISH_LAST_TRACK_KEY,
                                                    finishLastTrack,
                                                )
                                            }
                                        }
                                    }
                                    return Futures.immediateFuture(
                                        SessionResult(SessionResult.RESULT_SUCCESS)
                                    )
                                }

                                SET_PLAYBACK_PREFERENCE_COMMAND -> {
                                    playOnOutputDeviceConnection =
                                        args.getBoolean(PLAY_ON_OUTPUT_DEVICE_CONNECTION_KEY, false)
                                    player.setAudioAttributes(
                                        player.audioAttributes,
                                        args.getBoolean(PAUSE_ON_FOCUS_LOSS, true),
                                    )
                                    audioOffloading = args.getBoolean(AUDIO_OFFLOADING_KEY, true)
                                    reshuffleOnRepeat =
                                        args.getBoolean(RESHUFFLE_ON_REPEAT_KEY, false)
                                    player.updateAudioOffloading(audioOffloading)
                                    return Futures.immediateFuture(
                                        SessionResult(SessionResult.RESULT_SUCCESS)
                                    )
                                }

                                else ->
                                    return Futures.immediateFuture(
                                        SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                                    )
                            }
                        }

                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): ConnectionResult {
                            return ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(
                                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                        .add(SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY))
                                        .add(
                                            SessionCommand(
                                                SET_PLAYBACK_PREFERENCE_COMMAND,
                                                Bundle.EMPTY,
                                            )
                                        )
                                        .build()
                                )
                                .build()
                        }
                    }
                )
                .setBitmapLoader(CustomizedBitmapLoader(this))
                .build()
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {}

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        getSystemService(AudioManager::class.java)
            .unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        coroutineScope.cancel()
        super.onDestroy()
    }

    private inline fun MediaSession.updateSessionExtras(crossinline action: Bundle.() -> Unit) {
        val bundle = sessionExtras.clone() as Bundle
        action(bundle)
        sessionExtras = bundle
    }

    private fun Player.updateAudioOffloading(audioOffloading: Boolean) {
        trackSelectionParameters =
            trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    if (audioOffloading) {
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                            .setIsSpeedChangeSupportRequired(
                                playbackParameters.speed != 1f || playbackParameters.pitch != 1f
                            )
                            .build()
                    } else {
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                            )
                            .build()
                    }
                )
                .build()
    }
}

@UnstableApi
private class CustomizedPlayer(val inner: ExoPlayer) : ForwardingPlayer(inner) {
    private val listeners = mutableSetOf<Player.Listener>()
    private var shuffle = false

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getShuffleModeEnabled(): Boolean {
        return shuffle
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        var raiseEvent = true
        if (shuffleModeEnabled && !shuffle) {
            enableShuffle()
        } else if (!shuffleModeEnabled && shuffle) {
            disableShuffle()
        } else {
            raiseEvent = false
        }

        shuffle = shuffleModeEnabled

        if (raiseEvent) {
            for (listener in listeners) {
                listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
            }
        }
    }

    fun enableShuffle() {
        if (currentTimeline.isEmpty) return

        val currentIndex = currentMediaItemIndex
        val itemCount = mediaItemCount
        val shuffledPlayQueue =
            (0..<itemCount)
                .map { getMediaItemAt(it) }
                .mapIndexed { index, mediaItem -> index to mediaItem.setUnshuffledIndex(index) }
                .filter { it.first != currentIndex }
                .shuffled(Random)
                .map { it.second }
        replaceMediaItems(currentIndex + 1, itemCount, shuffledPlayQueue)
        removeMediaItems(0, currentIndex)
        replaceMediaItem(0, currentMediaItem!!.setUnshuffledIndex(currentIndex))
    }

    fun disableShuffle() {
        if (currentTimeline.isEmpty) return

        val currentIndex = currentMediaItemIndex
        val itemCount = mediaItemCount
        val unshuffledIndex = currentMediaItem!!.getUnshuffledIndex()
        if (unshuffledIndex == null) {
            Log.e("Phocid", "Current track has no unshuffled index when disabling shuffle")
            replaceMediaItems(
                0,
                itemCount,
                (0..<itemCount).map { getMediaItemAt(it).setUnshuffledIndex(null) },
            )
        } else {
            val unshuffledPlayQueue =
                (0..<itemCount)
                    .map { getMediaItemAt(it) }
                    .mapNotNull { mediaItem ->
                        mediaItem.getUnshuffledIndex()?.let { Pair(mediaItem, it) }
                    }
                    .sortedBy { it.second }
                    .map { it.first }
            replaceMediaItem(currentIndex, currentMediaItem!!.setUnshuffledIndex(null))
            replaceMediaItems(
                currentIndex + 1,
                itemCount,
                unshuffledPlayQueue.subList(unshuffledIndex + 1, unshuffledPlayQueue.size).map {
                    it.setUnshuffledIndex(null)
                },
            )
            replaceMediaItems(
                0,
                currentIndex,
                unshuffledPlayQueue.subList(0, unshuffledIndex).map { it.setUnshuffledIndex(null) },
            )
        }
    }
}

fun MediaItem.getUnshuffledIndex(): Int? {
    return mediaMetadata.extras?.getInt(UNSHUFFLED_INDEX_KEY, -1)?.takeIf { it >= 0 }
}

fun MediaItem.setUnshuffledIndex(unshuffledIndex: Int?): MediaItem {
    return buildUpon()
        .setMediaMetadata(
            mediaMetadata
                .buildUpon()
                .setExtras(
                    if (unshuffledIndex == null) bundleOf()
                    else bundleOf(Pair(UNSHUFFLED_INDEX_KEY, unshuffledIndex))
                )
                .build()
        )
        .build()
}
