package org.sunsetware.phocid.data

import kotlinx.serialization.Serializable

@Serializable
data class PersistentUiState(
    val libraryScreenHomeViewPage: Int = 0,
    val playerTimerSettings: PlayerTimerSettings = PlayerTimerSettings(),
    val playlistIoSyncHelpShown: Boolean = false,
)
