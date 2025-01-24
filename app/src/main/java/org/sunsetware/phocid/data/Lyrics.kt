package org.sunsetware.phocid.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.sunsetware.phocid.utils.decodeWithCharsetName
import org.sunsetware.phocid.utils.trimAndNormalize

@Immutable
data class Lyrics(val lines: List<Pair<Duration, String>>) {
    @Stable
    fun getLineIndex(time: Duration): Int? {
        val search = lines.binarySearchBy(time) { it.first }
        return when {
            search >= 0 -> search
            search < -1 -> -search - 2
            else -> null
        }?.let {
            var result = it
            while (result > 0 && lines[result - 1].first == lines[result].first) {
                result--
            }
            result
        }
    }
}

private val lrcRegex =
    Regex(
        """^\[(?<minutes>[0-9]+):(?<seconds>[0-9]{1,2})\.((?<milliseconds>[0-9]{3})|(?<centiseconds>[0-9]{2}))](?<text>.*)$"""
    )

@Stable
fun parseLrc(lrc: ByteArray, charsetName: String?): Lyrics {
    return parseLrc(lrc.decodeWithCharsetName(charsetName))
}

@Stable
fun parseLrc(lrc: String): Lyrics {
    return Lyrics(
        lrc.lines()
            .mapNotNull { line ->
                lrcRegex.matchEntire(line.trimAndNormalize())?.let {
                    val timestamp =
                        it.groups["minutes"]!!.value.toInt().minutes +
                            it.groups["seconds"]!!.value.toInt().seconds +
                            (it.groups["milliseconds"]?.value?.toInt()?.milliseconds
                                ?: (it.groups["centiseconds"]!!.value.toInt() * 10).milliseconds)
                    val text = it.groups["text"]!!.value.trim()
                    Pair(timestamp, text)
                }
            }
            .sortedBy { it.first }
    )
}
