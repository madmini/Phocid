package org.sunsetware.phocid.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ibm.icu.text.Collator
import kotlinx.serialization.Serializable

/**
 * All properties should return null if and only if this type doesn't support that property. Missing
 * values should be represented as "" or 0.
 */
interface Sortable {
    val sortTitle: String?
        get() = null

    val sortArtist: String?
        get() = null

    val sortAlbum: String?
        get() = null

    val sortAlbumArtist: String?
        get() = null

    val sortDiscNumber: Int?
        get() = null

    val sortTrackNumber: Int?
        get() = null

    val sortYear: Int?
        get() = null

    val sortGenre: String?
        get() = null

    val sortPlaylist: Pair<SpecialPlaylist?, String>?
        get() = null

    val sortIsFile: Boolean?
        get() = null

    val sortFilename: String?
        get() = null
}

@Immutable
@Serializable
enum class SortingKey {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    TRACK,
    YEAR,
    GENRE,
    PLAYLIST,
    FILE_NAME,
}

@Immutable data class SortingOption(val stringId: Int?, val keys: List<SortingKey>)

@Stable
inline fun <T> Iterable<T>.sortedBy(
    collator: Collator,
    sortingKeys: List<SortingKey>,
    ascending: Boolean,
    crossinline selector: (T) -> Sortable,
): List<T> {
    val comparator =
        Comparator<T> { genericA, genericB ->
            val a = selector(genericA)
            val b = selector(genericB)
            sortingKeys.forEach { item ->
                val result =
                    when (item) {
                        SortingKey.TITLE -> collator.compare(a.sortTitle, b.sortTitle)
                        SortingKey.ARTIST -> collator.compare(a.sortArtist, b.sortArtist)
                        SortingKey.ALBUM -> collator.compare(a.sortAlbum, b.sortAlbum)
                        SortingKey.ALBUM_ARTIST ->
                            collator.compare(a.sortAlbumArtist, b.sortAlbumArtist)
                        SortingKey.TRACK ->
                            a.sortDiscNumber!!.compareTo(b.sortDiscNumber!!).takeIf { it != 0 }
                                ?: a.sortTrackNumber!!.compareTo(b.sortTrackNumber!!)
                        SortingKey.YEAR -> a.sortYear!!.compareTo(b.sortYear!!)
                        SortingKey.GENRE -> collator.compare(a.sortGenre, b.sortGenre)
                        SortingKey.PLAYLIST ->
                            (a.sortPlaylist!!.first?.order ?: Int.MAX_VALUE)
                                .compareTo(b.sortPlaylist!!.first?.order ?: Int.MAX_VALUE)
                                .takeIf { it != 0 }
                                ?: collator.compare(
                                    a.sortPlaylist!!.second,
                                    b.sortPlaylist!!.second,
                                )
                        SortingKey.FILE_NAME ->
                            a.sortIsFile!!.compareTo(b.sortIsFile!!).takeIf { it != 0 }
                                ?: collator.compare(a.sortFilename!!, b.sortFilename!!)
                    }
                if (result != 0) return@Comparator result
            }
            return@Comparator 0
        }

    return sortedWith(comparator).run { if (ascending) this else reversed() }
}

@Stable
fun <T : Sortable> Iterable<T>.sorted(
    collator: Collator,
    sortingKeys: List<SortingKey>,
    ascending: Boolean,
): List<T> {
    return sortedBy(collator, sortingKeys, ascending) { it }
}
