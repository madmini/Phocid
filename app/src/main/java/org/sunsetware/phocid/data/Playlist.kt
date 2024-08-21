package org.sunsetware.phocid.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.apache.commons.io.FilenameUtils
import org.jetbrains.annotations.NonNls
import org.sunsetware.phocid.PLAYLISTS_FILE_NAME
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.utils.*

enum class SpecialPlaylist(
    /** Version 8 UUID. Guaranteed to not collide with [UUID.randomUUID]. */
    val key: UUID,
    val title: String,
    val order: Int,
    val icon: ImageVector,
    val color: Color,
) {
    FAVORITES(
        UUID.fromString("00000000-0000-8000-8000-000000000000"),
        Strings[R.string.playlist_special_favorites],
        0,
        Icons.Outlined.FavoriteBorder,
        Color(0xffd2849c),
    )
}

val SpecialPlaylistLookup = SpecialPlaylist.entries.associateBy { it.key }

class PlaylistManager(
    private val coroutineScope: CoroutineScope,
    libraryIndex: StateFlow<LibraryIndex>,
) : AutoCloseable {
    private val _playlists = MutableStateFlow(mapOf(SpecialPlaylist.FAVORITES.key to Playlist("")))
    val playlists =
        _playlists.combine(
            coroutineScope,
            libraryIndex.map(coroutineScope) { libraryIndex ->
                libraryIndex.tracks.values.associateBy { it.path }
            },
        ) { playlists, trackIndex ->
            playlists.mapValues { it.value.realize(SpecialPlaylistLookup[it.key], trackIndex) }
        }
    private lateinit var saveManager: SaveManager<Map<String, Playlist>>

    fun initialize(context: Context) {
        loadCbor<Map<String, Playlist>>(context, PLAYLISTS_FILE_NAME, false)?.let { playlists ->
            _playlists.update { playlists.mapKeys { UUID.fromString(it.key) } }
        }
        saveManager =
            SaveManager(
                context,
                coroutineScope,
                _playlists.map(coroutineScope) { playlists ->
                    playlists.mapKeys { it.key.toString() }
                },
                PLAYLISTS_FILE_NAME,
                false,
            )
    }

    override fun close() {
        saveManager.close()
    }

    fun updatePlaylist(key: UUID, transform: (Playlist) -> Playlist) {
        _playlists.update { playlists ->
            if (playlists.containsKey(key)) {
                playlists.mapValues { if (it.key == key) transform(it.value) else it.value }
            } else {
                playlists + Pair(key, transform(Playlist("")))
            }
        }
    }

    /** I won't trust Android JVM's randomness. */
    fun addPlaylist(playlist: Playlist): UUID {
        while (true) {
            val key = UUID.randomUUID()
            var success = false
            _playlists.update {
                if (!it.containsKey(key)) {
                    success = true
                    (it + Pair(key, playlist))
                } else {
                    // Assignment to false is necessary due to [update] might be rerun.
                    success = false
                    it
                }
            }
            if (success) return key
        }
    }

    fun removePlaylist(key: UUID) {
        _playlists.update { it - key }
    }
}

/**
 * Changes to this class should not change types of existing members, and new members must have a
 * default value, or else the user will have their playlists wiped after an app update.
 */
@Immutable
@Serializable
data class Playlist(val name: String, @Required val entries: List<PlaylistEntry> = emptyList()) {
    /** I won't trust Android JVM's randomness. */
    fun addPaths(paths: List<String>): Playlist {
        val existingKeys = entries.map { it.key }.toSet()
        val newKeys = mutableListOf<UUID>()
        repeat(paths.size) {
            var key = UUID.randomUUID()
            while (existingKeys.contains(key) || newKeys.contains(key)) {
                key = UUID.randomUUID()
            }
            newKeys.add(key)
        }
        return copy(
            entries = entries + newKeys.zip(paths) { key, path -> PlaylistEntry(key, path) }
        )
    }

    fun addTracks(tracks: List<Track>): Playlist {
        return addPaths(tracks.map { it.path })
    }
}

private fun Playlist.realize(
    specialType: SpecialPlaylist?,
    trackIndex: Map<String, Track>,
): RealizedPlaylist {
    return RealizedPlaylist(
        specialType,
        name,
        entries.mapIndexed { index, entry ->
            RealizedPlaylistEntry(entry.key, index, trackIndex[entry.path], entry)
        },
    )
}

@Immutable
data class RealizedPlaylist(
    val specialType: SpecialPlaylist?,
    val customName: String,
    val entries: List<RealizedPlaylistEntry> = emptyList(),
) : Searchable, Sortable {
    val displayName
        get() = specialType?.title ?: customName

    val validTracks = entries.mapNotNull { it.track }
    val invalidCount = entries.count { it.track == null }
    val displayStatistics
        get() =
            Strings.separate(
                Strings[R.string.count_track].icuFormat(validTracks.size),
                invalidCount.takeIfNot(0)?.let {
                    Strings[R.string.count_invalid_track].icuFormat(it)
                },
            )

    override val searchableStrings = listOf(displayName)
    override val sortPlaylist
        get() = Pair(specialType, customName)

    companion object {
        @NonNls
        val CollectionSortingOptions =
            mapOf("Name" to SortingOption(R.string.sorting_name, listOf(SortingKey.PLAYLIST)))
        @NonNls
        val TrackSortingOptions =
            mapOf("Custom" to SortingOption(R.string.sorting_custom, emptyList())) +
                Track.SortingOptions
    }
}

/**
 * TODO: Use relative path? (But MediaStore provides no validity guarantee of the relative path, so
 *   it will likely break in a future Android release considering Google's track record)
 */
@Immutable
@Serializable
data class PlaylistEntry(
    @Serializable(with = UUIDSerializer::class) val key: UUID,
    val path: String,
)

@Immutable
data class RealizedPlaylistEntry(
    val key: UUID,
    val index: Int,
    val track: Track?,
    val playlistEntry: PlaylistEntry,
)

@Serializable
data class PlaylistIoSettings(
    val ignoreCase: Boolean = true,
    val ignoreLocation: Boolean = true,
    val removeInvalid: Boolean = true,
)

fun parseM3u(
    name: String,
    m3u: ByteArray,
    libraryTrackPaths: Set<String>,
    settings: PlaylistIoSettings,
    charsetName: String?,
): Playlist {
    val lines =
        m3u.decodeWithCharsetName(charsetName)
            .lines()
            .mapNotNull { it.trimAndNormalize().let { FilenameUtils.separatorsToUnix(it) } }
            .filter { it.isNotBlank() && !it.startsWith('#') }
    val indexLookup =
        libraryTrackPaths
            .associateBy({ if (settings.ignoreLocation) FilenameUtils.getName(it) else it }) {
                setOf(it)
            }
            .let { map ->
                if (settings.ignoreCase)
                    CaseInsensitiveMap(map) { duplicates -> duplicates.flatMap { it }.toSet() }
                else map
            }
    val paths =
        lines.mapNotNull {
            val candidates =
                indexLookup[if (settings.ignoreLocation) FilenameUtils.getName(it) else it]
            val bestMatch =
                candidates?.maxByOrNull { it.commonSuffixWith(it, settings.ignoreCase).length }
            bestMatch ?: if (settings.removeInvalid) null else it
        }
    return Playlist(name).addPaths(paths)
}

fun RealizedPlaylist.toM3u(settings: PlaylistIoSettings): String {
    return entries
        .filter { if (settings.removeInvalid) it.track != null else true }
        .joinToString("\n") { it.playlistEntry.path }
}
