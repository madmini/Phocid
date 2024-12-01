@file:OptIn(UnstableApi::class)

package org.sunsetware.phocid.data

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.*
import org.sunsetware.phocid.utils.*

@Serializable
@Immutable
data class PlayerState(
    /** To restore the unshuffled play queue: `(0..<length).map { actualPlayQueue[it] }` */
    val unshuffledPlayQueueMapping: List<Int>? = null,
    val actualPlayQueue: List<Long> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
)

@Immutable data class PlayerTransientState(val version: Long = -1, val isPlaying: Boolean = false)

/** This method should work even if values of [UNSHUFFLED_INDEX_KEY] are discontinuous. */
private fun MediaController.capturePlayerState(): PlayerState {
    val shuffle = sessionExtras.getBoolean(SHUFFLE_KEY, false)
    val mediaItems = (0..<mediaItemCount).map { getMediaItemAt(it) }
    fun getUnshuffledPlayQueueMapping(): List<Int> {
        return mediaItems
            .mapIndexedNotNull { index, mediaItem ->
                val unshuffledIndex =
                    mediaItem.mediaMetadata.extras?.getInt(UNSHUFFLED_INDEX_KEY, -1)
                if (unshuffledIndex != null && unshuffledIndex >= 0) Pair(index, unshuffledIndex)
                else null
            }
            .sortedBy { it.second }
            .map { it.first }
    }
    val actualPlayQueue = mediaItems.map { it.mediaId.toLong() }
    return PlayerState(
        if (shuffle) getUnshuffledPlayQueueMapping() else null,
        actualPlayQueue,
        currentMediaItemIndex,
        shuffle,
        repeatMode,
        playbackParameters.speed,
        playbackParameters.pitch,
    )
}

private fun MediaController.restorePlayerState(
    state: PlayerState,
    unfilteredTrackIndex: UnfilteredTrackIndex,
) {
    setMediaItems(
        state.actualPlayQueue.mapIndexed { index, id ->
            unfilteredTrackIndex.tracks[id]!!.getMediaItem(
                state.unshuffledPlayQueueMapping?.indexOf(index)
            )
        }
    )
    seekTo(state.currentIndex, 0)
    sendCustomCommand(
        SessionCommand(SET_SHUFFLE_COMMAND, Bundle.EMPTY),
        bundleOf(Pair(SHUFFLE_KEY, state.shuffle)),
    )
    repeatMode = state.repeat
    playbackParameters = PlaybackParameters(state.speed, state.pitch)
}

private fun MediaController.captureTransientState(version: Long): PlayerTransientState {
    return PlayerTransientState(version, playbackState == Player.STATE_READY && playWhenReady)
}

class PlayerWrapper : AutoCloseable {
    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _transientState = MutableStateFlow(PlayerTransientState())
    val transientState = _transientState.asStateFlow()

    private lateinit var mediaController: MediaController
    private lateinit var saveManager: SaveManager<PlayerState>
    private val transientStateVersion = AtomicLong(0)

    val currentPosition: Long
        get() = mediaController.currentPosition

    override fun close() {
        mediaController.release()
        saveManager.close()
    }

    suspend fun initialize(
        context: Context,
        unfilteredTrackIndex: UnfilteredTrackIndex,
        coroutineScope: CoroutineScope,
    ) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        val completed = AtomicBoolean(false)
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                mediaController.prepare()

                if (
                    mediaController.currentTimeline.isEmpty || unfilteredTrackIndex.tracks.isEmpty()
                ) {
                    var state =
                        loadCbor<PlayerState>(context, PLAYER_STATE_FILE_NAME, isCache = false)
                            ?: PlayerState()

                    // Invalidate play queue if any track no longer exists
                    if (
                        state.actualPlayQueue.any { !unfilteredTrackIndex.tracks.containsKey(it) }
                    ) {
                        state =
                            state.copy(
                                unshuffledPlayQueueMapping =
                                    if (state.shuffle) emptyList() else null,
                                actualPlayQueue = emptyList(),
                            )
                    }
                    _state.update { state }

                    mediaController.restorePlayerState(_state.value, unfilteredTrackIndex)
                } else {
                    _state.update { mediaController.capturePlayerState() }
                }
                _transientState.update {
                    mediaController.captureTransientState(transientStateVersion.getAndIncrement())
                }

                val listener =
                    object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            updateState()
                        }
                    }
                mediaController.addListener(listener)
                saveManager =
                    SaveManager(context, coroutineScope, _state, PLAYER_STATE_FILE_NAME, false)

                completed.set(true)
            },
            ContextCompat.getMainExecutor(context),
        )

        while (!completed.get()) {
            delay(1)
        }
    }

    private fun updateState() {
        _state.update { mediaController.capturePlayerState() }
        _transientState.update {
            mediaController.captureTransientState(transientStateVersion.getAndIncrement())
        }
    }

    fun seekToPrevious() {
        val currentIndex = mediaController.currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1).wrap(
                mediaController.mediaItemCount,
                mediaController.repeatMode != Player.REPEAT_MODE_OFF,
            ) ?: currentIndex
        mediaController.seekTo(previousIndex, 0)
        // Force a state emission for UI recomposition.
        updateState()
        mediaController.play()
    }

    fun seekToPreviousSmart() {
        val currentIndex = mediaController.currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1)
                .wrap(
                    mediaController.mediaItemCount,
                    mediaController.repeatMode != Player.REPEAT_MODE_OFF,
                )
                .takeIf {
                    mediaController.currentPosition <= mediaController.maxSeekToPreviousPosition
                } ?: currentIndex
        mediaController.seekTo(previousIndex, 0)
        // Force a state emission for UI recomposition.
        updateState()
        mediaController.play()
    }

    fun seekToNext() {
        val currentIndex = mediaController.currentMediaItemIndex
        val nextIndex =
            (currentIndex + 1).wrap(
                mediaController.mediaItemCount,
                mediaController.repeatMode != Player.REPEAT_MODE_OFF,
            ) ?: currentIndex
        mediaController.seekTo(nextIndex, 0)
        // Force a state emission for UI recomposition.
        updateState()
        mediaController.play()
    }

    fun seekTo(index: Int) {
        mediaController.seekTo(index, 0)
        mediaController.play()
    }

    fun seekToFraction(fraction: Float) {
        val duration = mediaController.duration
        mediaController.seekTo((duration * fraction).toLong().coerceIn(0, duration))
    }

    fun togglePlay() {
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun setTracks(tracks: List<Track>, index: Int?) {
        val seekIndex: Int
        if (!_state.value.shuffle) {
            mediaController.setMediaItems(tracks.map { it.getMediaItem(null) })
            seekIndex = index ?: 0
        } else {
            val shuffledIndices =
                if (index != null) {
                    listOf(index) + tracks.indices.filter { it != index }.shuffled(Random)
                } else {
                    tracks.indices.shuffled(Random)
                }
            mediaController.setMediaItems(shuffledIndices.map { i -> tracks[i].getMediaItem(i) })
            seekIndex = 0
        }
        mediaController.seekTo(seekIndex, 0)
        mediaController.play()
    }

    fun addTracks(tracks: List<Track>) {
        val firstIndex = _state.value.actualPlayQueue.size
        mediaController.addMediaItems(
            tracks.mapIndexed { i, track ->
                track.getMediaItem(if (!_state.value.shuffle) null else firstIndex + i)
            }
        )
    }

    fun removeTrack(index: Int) {
        // [capturePlayerState] should take care of discontinuous [UNSHUFFLED_INDEX_KEY].
        mediaController.removeMediaItem(index)
    }

    fun clearTracks() {
        mediaController.clearMediaItems()
    }

    fun toggleShuffle(libraryIndex: LibraryIndex) {
        val stateSnapshot = _state.value
        with(stateSnapshot) {
            if (actualPlayQueue.count() > 1) {
                if (shuffle) {
                    // Disable shuffling.
                    val unshuffledIndex = unshuffledPlayQueueMapping!!.indexOf(currentIndex)
                    mediaController.replaceMediaItem(
                        currentIndex,
                        libraryIndex.tracks[actualPlayQueue[currentIndex]]!!.getMediaItem(
                            unshuffledIndex
                        ),
                    )
                    mediaController.replaceMediaItems(
                        currentIndex + 1,
                        actualPlayQueue.size,
                        unshuffledPlayQueueMapping
                            .subList(unshuffledIndex + 1, unshuffledPlayQueueMapping.size)
                            .mapNotNull {
                                libraryIndex.tracks[actualPlayQueue[it]]?.getMediaItem(null)
                            },
                    )
                    mediaController.replaceMediaItems(
                        0,
                        currentIndex,
                        unshuffledPlayQueueMapping.subList(0, unshuffledIndex).mapNotNull {
                            libraryIndex.tracks[actualPlayQueue[it]]?.getMediaItem(null)
                        },
                    )
                } else {
                    // Enable shuffling.
                    val shuffledPlayQueue =
                        actualPlayQueue
                            .mapIndexed { index, id ->
                                Pair(index, libraryIndex.tracks[id]?.getMediaItem(index))
                            }
                            .filter { it.first != currentIndex && it.second != null }
                            .shuffled(Random)
                            .map { it.second!! }
                    mediaController.replaceMediaItems(
                        currentIndex + 1,
                        actualPlayQueue.size,
                        shuffledPlayQueue,
                    )
                    mediaController.removeMediaItems(0, currentIndex)
                    mediaController.replaceMediaItem(
                        0,
                        libraryIndex.tracks[actualPlayQueue[currentIndex]]!!.getMediaItem(
                            currentIndex
                        ),
                    )
                }
            }

            mediaController.sendCustomCommand(
                SessionCommand(SET_SHUFFLE_COMMAND, Bundle.EMPTY),
                bundleOf(Pair(SHUFFLE_KEY, !shuffle)),
            )
            // Media3 will not trigger [onEvents] if the playlist has not not fundamentally changed,
            // so a manual update is required.
            updateState()
        }
    }

    fun toggleRepeat() {
        mediaController.repeatMode =
            when (mediaController.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
    }

    fun getTimerState(): Pair<Long, Boolean>? {
        return mediaController.sessionExtras
            .getLong(TIMER_TARGET_KEY, -1)
            .takeIf { it >= 0 }
            ?.let {
                Pair(
                    it,
                    mediaController.sessionExtras.getBoolean(TIMER_FINISH_LAST_TRACK_KEY, true),
                )
            }
    }

    fun setTimer(settings: PlayerTimerSettings) {
        mediaController.sendCustomCommand(
            SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY),
            bundleOf(
                Pair(
                    TIMER_TARGET_KEY,
                    SystemClock.elapsedRealtime() + settings.duration.inWholeMilliseconds,
                ),
                Pair(TIMER_FINISH_LAST_TRACK_KEY, settings.finishLastTrack),
            ),
        )
    }

    fun cancelTimer() {
        mediaController.sendCustomCommand(
            SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY),
            bundleOf(Pair(TIMER_TARGET_KEY, -1)),
        )
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        mediaController.playbackParameters = PlaybackParameters(speed, pitch)
    }
}

@Immutable
@Serializable
data class PlayerTimerSettings(
    val duration: Duration = 10.minutes,
    val finishLastTrack: Boolean = true,
)
