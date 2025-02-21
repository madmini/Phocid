package org.sunsetware.phocid.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastFlatMap
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

private val lrcLineRegex =
    Regex(
        """^(?<timestamps>(?:\[\d+:\d{1,2}\.\d{2,3}])+)(?<text>.*)$""",
    )
private val lrcTimestampRegex =
    Regex(
        """\[(?<minutes>\d+):(?<seconds>\d{1,2})\.((?<milliseconds>\d{3})|(?<centiseconds>\d{2}))]""",
    )

@Stable
fun parseLrc(lrc: ByteArray, charsetName: String?): Lyrics {
    return parseLrc(lrc.decodeWithCharsetName(charsetName))
}

@Stable
fun parseLrc(lrc: String): Lyrics {
    return Lyrics(
        lrc.lines()
            .fastFlatMap { line ->
                lrcLineRegex.matchEntire(line.trimAndNormalize())?.let { m1 ->
                    val text = m1.groups["text"]!!.value.trim()
                    lrcTimestampRegex.findAll(m1.groups["timestamps"]!!.value).map { m2 ->
                        val timestamp =
                            m2.groups["minutes"]!!.value.toInt().minutes +
                                m2.groups["seconds"]!!.value.toInt().seconds +
                                (m2.groups["milliseconds"]?.value?.toInt()?.milliseconds
                                    ?: (m2.groups["centiseconds"]!!.value.toInt() * 10).milliseconds)
                        Pair(timestamp, text)
                    }.toList()
                } ?: emptyList()
            }
            .sortedBy { it.first }
    )
}
