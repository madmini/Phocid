package org.sunsetware.phocid

import androidx.compose.ui.graphics.Color
import com.ibm.icu.text.Collator
import java.util.Locale
import kotlin.time.Duration
import org.assertj.core.api.Assertions.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sunsetware.phocid.data.AlbumKey
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.UnfilteredTrackIndex
import org.sunsetware.phocid.data.albumKey

@RunWith(RobolectricTestRunner::class)
class LibraryIndexTest {
    val collator = Collator.getInstance(Locale.ROOT).freeze()

    @Test
    fun indexing_noCrash_InvalidMetadata() {
        val index =
            LibraryIndex.new(
                UnfilteredTrackIndex(null, mapOf(InvalidTrack.id to InvalidTrack)),
                collator,
                emptyList(),
                emptyList(),
            )
        assertThat(index.tracks).isEqualTo(mapOf(InvalidTrack.id to InvalidTrack))
    }

    @Test
    fun indexing_noCrash_EmptyLibrary() {
        val index =
            LibraryIndex.new(
                UnfilteredTrackIndex(null, emptyMap()),
                collator,
                emptyList(),
                emptyList(),
            )
        assertThat(index.tracks).isEqualTo(emptyMap<Long, Track>())
    }

    @Test
    fun indexing_album_EmptyLibrary() {
        val index = emptyList<Track>().libraryIndex()
        assertThat(index.albums.keys).isEmpty()
    }

    @Test
    fun indexing_album_SingleTrack() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val index = listOf(track1).libraryIndex()
        assertThat(index.albums.keys).containsExactlyInAnyOrder(AlbumKey("album1", "artist1"))
    }

    @Test
    fun indexing_album_CaseMerging() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val track2 = track(2, album = "ALBUM1", albumArtist = "artist1")
        val track3 = track(3, album = "ALBUM1", albumArtist = "ARTIST1")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(index.albums.keys.map { it.name.toString() to it.albumArtist?.toString() })
            .containsExactlyInAnyOrder("ALBUM1" to "artist1")
    }

    @Test
    fun indexing_album_TracksWithDifferentAlbums() {
        val track1 = track(1, album = "album1", albumArtist = "artist1")
        val track2 = track(2, album = "album2", albumArtist = "artist2")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(AlbumKey("album1", "artist1"), AlbumKey("album2", "artist2"))
    }

    @Test
    fun indexing_album_Nulls() {
        val track1 = track(1, album = null, albumArtist = null)
        val track2 = track(2, album = "album1", albumArtist = null)
        val track3 = track(3, album = null, albumArtist = "artist1")
        val track4 = track(4, album = "album1", albumArtist = "artist1")
        val index = listOf(track1, track2, track3, track4).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(AlbumKey("album1", null), AlbumKey("album1", "artist1"))
    }

    @Test
    fun indexing_album_SameNameDifferentAlbumArtists() {
        val track1 = track(1, album = "album1", albumArtist = null)
        val track2 = track(2, album = "album1", albumArtist = "artist1")
        val track3 = track(3, album = "album1", albumArtist = "artist2")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(index.albums.keys)
            .containsExactlyInAnyOrder(
                AlbumKey("album1", null),
                AlbumKey("album1", "artist1"),
                AlbumKey("album1", "artist2"),
            )
        assertThat(index.albums[AlbumKey("album1", null)]?.albumKey)
            .isEqualTo(AlbumKey("album1", null))
        assertThat(index.albums[AlbumKey("album1", "artist1")]?.albumKey)
            .isEqualTo(AlbumKey("album1", "artist1"))
        assertThat(index.albums[AlbumKey("album1", "artist2")]?.albumKey)
            .isEqualTo(AlbumKey("album1", "artist2"))
    }

    @Test
    fun indexing_folder_EmptyLibrary() {
        val index = emptyList<Track>().libraryIndex()
        assertThat(index.folders.keys).containsExactlyInAnyOrder("")
    }

    @Test
    fun indexing_folder_SingleTrack() {
        val track1 = track(1, path = "/music/track1.mp3")
        val index = listOf(track1).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
    }

    @Test
    fun indexing_folder_MultipleTracksInSameFolder() {
        val track1 = track(1, path = "/music/track1.mp3")
        val track2 = track(2, path = "/music/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1, track2)
    }

    @Test
    fun indexing_folder_TracksInDifferentFolders() {
        val track1 = track(1, path = "/music/track1.mp3")
        val track2 = track(2, path = "/music2/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders.keys).contains("music2")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
        assertThat(index.folders["music2"]?.childTracks).containsExactly(track2)
    }

    @Test
    fun indexing_folder_TracksInNestedFolders() {
        val track1 = track(1, path = "/music/track1.mp3")
        val track2 = track(2, path = "/music/album/track2.mp3")
        val track3 = track(3, path = "/music/album/track3.mp3")
        val index = listOf(track1, track2, track3).libraryIndex()
        assertThat(index.folders.keys).contains("music")
        assertThat(index.folders.keys).contains("music/album")
        assertThat(index.folders["music"]?.childTracks).containsExactly(track1)
        assertThat(index.folders["music/album"]?.childTracks).containsExactly(track2, track3)
    }

    @Test
    fun indexing_folder_TracksInRootFolder() {
        val track1 = track(1, path = "/track1.mp3")
        val track2 = track(2, path = "/track2.mp3")
        val index = listOf(track1, track2).libraryIndex()
        assertThat(index.folders.keys).containsExactlyInAnyOrder("")
        assertThat(index.folders[""]?.childTracks).containsExactly(track1, track2)
    }

    fun track(
        id: Long,
        path: String = InvalidTrack.path,
        fileName: String = InvalidTrack.fileName,
        dateAdded: Long = InvalidTrack.dateAdded,
        version: Long = InvalidTrack.version,
        title: String? = InvalidTrack.title,
        artists: List<String> = InvalidTrack.artists,
        album: String? = InvalidTrack.album,
        albumArtist: String? = InvalidTrack.albumArtist,
        genres: List<String> = InvalidTrack.genres,
        year: Int? = InvalidTrack.year,
        trackNumber: Int? = InvalidTrack.trackNumber,
        discNumber: Int? = InvalidTrack.discNumber,
        duration: Duration = InvalidTrack.duration,
        size: Long = InvalidTrack.size,
        format: String = InvalidTrack.format,
        sampleRate: Int = InvalidTrack.sampleRate,
        bitRate: Long = InvalidTrack.bitRate,
        bitDepth: Int = InvalidTrack.bitDepth,
        hasArtwork: Boolean = InvalidTrack.hasArtwork,
        vibrantColor: Color? = InvalidTrack.vibrantColor,
        mutedColor: Color? = InvalidTrack.mutedColor,
        unsyncedLyrics: String? = InvalidTrack.unsyncedLyrics,
        comment: String? = InvalidTrack.comment,
    ): Track {
        return Track(
            id = id,
            path = path,
            fileName = fileName,
            version = version,
            title = title,
            artists = artists,
            album = album,
            albumArtist = albumArtist,
            genres = genres,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            duration = duration,
            size = size,
            format = format,
            sampleRate = sampleRate,
            bitRate = bitRate,
            bitDepth = bitDepth,
            hasArtwork = hasArtwork,
            vibrantColor = vibrantColor,
            mutedColor = mutedColor,
            dateAdded = dateAdded,
            unsyncedLyrics = unsyncedLyrics,
            comment = comment,
        )
    }

    fun List<Track>.libraryIndex(): LibraryIndex {
        return LibraryIndex.new(
            UnfilteredTrackIndex(null, associateBy { it.id }),
            collator,
            emptyList(),
            emptyList(),
        )
    }
}
