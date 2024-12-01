package org.sunsetware.phocid

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.collection.lruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.sunsetware.phocid.data.*
import org.sunsetware.phocid.ui.views.TabInfo
import org.sunsetware.phocid.utils.*

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val trackIndexFile =
        File(application.applicationContext.cacheDir, TRACK_INDEX_FILE_NAME)
    private val initializationMutex = Mutex()
    private val scanMutex = Mutex()

    val playerWrapper: PlayerWrapper = PlayerWrapper()

    val artworkCache =
        lruCache<Long, Nullable<Bitmap>>(
            64,
            create = { Nullable(loadArtwork(application.applicationContext, it)) },
        )

    val lyricsCache = AtomicReference(null as Pair<Long, Lyrics>?)

    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()

    private val _unfilteredTrackIndex = MutableStateFlow(EmptyTrackIndex)
    val unfilteredTrackIndex = _unfilteredTrackIndex.asStateFlow()

    private val _isScanningLibrary = MutableStateFlow(null as Boolean?)
    /**
     * - null: not scanning
     * - true: forced (manual)
     * - false: not forced (auto)
     */
    val isScanningLibrary = _isScanningLibrary.asStateFlow()

    private val _libraryScanProgress = MutableStateFlow(null as Pair<Int, Int>?)
    val libraryScanProgress = _libraryScanProgress.asStateFlow()

    private val _preferences = MutableStateFlow(Preferences())
    val preferences = _preferences.asStateFlow()
    private lateinit var preferencesSaveManager: SaveManager<Preferences>

    // These synchronizations are necessary to prevent data popping on launch due to blacklists
    private val libraryIndexInputReady = MutableStateFlow(false)
    private val libraryIndexOutputReady = AtomicBoolean(false)
    val libraryIndex =
        _unfilteredTrackIndex
            .combine(
                viewModelScope,
                _preferences.map(viewModelScope) {
                    object {
                        val collator = it.sortCollator
                        val blacklist = it.blacklistRegexes
                        val whitelist = it.whitelistRegexes
                    }
                },
            ) { trackIndex, tuple ->
                LibraryIndex.new(trackIndex, tuple.collator, tuple.blacklist, tuple.whitelist)
            }
            .combine(viewModelScope, libraryIndexInputReady, true) { libraryIndex, ready ->
                libraryIndexOutputReady.set(ready)
                libraryIndex
            }

    val playlistManager = PlaylistManager(viewModelScope, libraryIndex)

    val uiManager =
        UiManager(
            application.applicationContext,
            viewModelScope,
            _preferences,
            libraryIndex,
            playlistManager,
        )

    var intentLauncher = WeakReference<IntentLauncher>(null)

    val playlistIoDirectory = MutableStateFlow(null as Uri?)

    override fun onCleared() {
        playerWrapper.close()
        uiManager.close()
        preferencesSaveManager.close()
        playlistManager.close()
        super.onCleared()
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun initialize(onDismissSplashScreen: () -> Unit) {
        viewModelScope
            .launch {
                withContext(Dispatchers.IO) {
                    if (initializationMutex.tryLock()) {
                        try {
                            if (!_initialized.value) {
                                loadCbor<Preferences>(
                                        application.applicationContext,
                                        PREFERENCES_FILE_NAME,
                                        false,
                                    )
                                    ?.upgrade()
                                    ?.let { preferences -> _preferences.update { preferences } }
                                preferencesSaveManager =
                                    SaveManager(
                                        application.applicationContext,
                                        viewModelScope,
                                        _preferences,
                                        PREFERENCES_FILE_NAME,
                                        false,
                                    )

                                val unfilteredTrackIndex =
                                    try {
                                        Cbor.decodeFromByteArray<UnfilteredTrackIndex>(
                                            trackIndexFile.readBytes()
                                        )
                                    } catch (ex: Exception) {
                                        Log.e("Phocid", "Can't read old library index", ex)
                                        EmptyTrackIndex
                                    }
                                _unfilteredTrackIndex.update { unfilteredTrackIndex }

                                playlistManager.initialize(application.applicationContext)

                                playerWrapper.initialize(
                                    application.applicationContext,
                                    unfilteredTrackIndex,
                                    viewModelScope,
                                )

                                libraryIndexInputReady.update { true }
                                while (!libraryIndexOutputReady.get()) {
                                    delay(1.milliseconds)
                                }
                                _initialized.update { true }
                            }
                        } finally {
                            onDismissSplashScreen()
                            initializationMutex.unlock()
                        }
                    }
                }
            }
            .join()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun scanLibrary(force: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (scanMutex.tryLock()) {
                    Log.d("Phocid", "Library scan started")
                    while (!initialized.value) {
                        delay(1)
                    }
                    try {
                        _libraryScanProgress.update { null }
                        _isScanningLibrary.update { force }
                        val newTrackIndex =
                            scanTracks(
                                application.applicationContext,
                                _preferences.value.advancedMetadataExtraction,
                                if (force) null else _unfilteredTrackIndex.value,
                                _preferences.value.artistMetadataSeparators,
                                _preferences.value.artistMetadataSeparatorExceptions,
                            ) { current, total ->
                                _libraryScanProgress.update { current to total }
                            }
                        if (newTrackIndex != null) {
                            _unfilteredTrackIndex.update { newTrackIndex }
                            try {
                                trackIndexFile.writeBytes(Cbor.encodeToByteArray(newTrackIndex))
                            } catch (ex: Exception) {
                                Log.e("Phocid", "Can't write track index", ex)
                            }
                            Log.d("Phocid", "Library scan completed")
                        } else {
                            Log.d("Phocid", "Library scan aborted: permission denied")
                        }
                    } finally {
                        scanMutex.unlock()
                        _isScanningLibrary.update { null }
                    }
                }
            }
        }
    }

    fun updatePreferences(transform: (Preferences) -> Preferences) {
        _preferences.update(transform)
    }

    fun updateTabInfo(index: Int, transform: (TabInfo) -> TabInfo) {
        _preferences.update { preferences ->
            val type = preferences.tabs[index].type
            preferences.copy(
                tabSettings =
                    preferences.tabSettings.mapValues {
                        if (it.key == type) transform(it.value) else it.value
                    }
            )
        }
    }
}
