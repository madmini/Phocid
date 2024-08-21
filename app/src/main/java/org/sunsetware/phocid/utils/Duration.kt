package org.sunsetware.phocid.utils

import kotlin.time.Duration
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings

fun Duration.toShortString(): String {
    return absoluteValue.toComponents { hours, minutes, seconds, _ ->
        if (isNegative()) {
            if (hours > 0)
                Strings[R.string.duration_negative_hours_minutes_seconds].icuFormat(
                    hours,
                    minutes,
                    seconds,
                )
            else Strings[R.string.duration_negative_minutes_seconds].icuFormat(minutes, seconds)
        } else {
            if (hours > 0)
                Strings[R.string.duration_hours_minutes_seconds].icuFormat(hours, minutes, seconds)
            else Strings[R.string.duration_minutes_seconds].icuFormat(minutes, seconds)
        }
    }
}

inline fun <T> Iterable<T>.sumOf(transform: (T) -> Duration): Duration {
    return map(transform).fold(Duration.ZERO, Duration::plus)
}
