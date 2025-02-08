package org.sunsetware.phocid

import android.os.Build
import androidx.compose.ui.unit.dp

val READ_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        android.Manifest.permission.READ_MEDIA_AUDIO
    else android.Manifest.permission.READ_EXTERNAL_STORAGE

const val PREFERENCES_FILE_NAME = "preferences"
const val PLAYLISTS_FILE_NAME = "playlists"
const val TRACK_INDEX_FILE_NAME = "trackIndex"
const val PLAYER_STATE_FILE_NAME = "playerState"
const val UI_STATE_FILE_NAME = "uiState"

const val UNSHUFFLED_INDEX_KEY = "originalIndex"
const val SET_TIMER_COMMAND = "setTimer"
const val TIMER_TARGET_KEY = "timerTarget"
const val TIMER_FINISH_LAST_TRACK_KEY = "timerFinishLastTrack"
const val SET_PLAYBACK_PREFERENCE_COMMAND = "setPlaybackPreference"
const val PLAY_ON_OUTPUT_DEVICE_CONNECTION_KEY = "playOnOutputDeviceConnection"
const val PAUSE_ON_FOCUS_LOSS = "pauseOnFocusLoss"
const val AUDIO_OFFLOADING_KEY = "audioOffloading"
const val RESHUFFLE_ON_REPEAT_KEY = "reshuffleOnRepeat"

const val UNKNOWN = "<unknown>"

val DRAG_THRESHOLD = 32.dp
const val TNUM = "tnum"

const val DEPENDENCY_INFOS_FILE_NAME = "open_source_licenses.json"
const val LICENSE_MAPPINGS_FILE_NAME = "LicenseMappings.json"
