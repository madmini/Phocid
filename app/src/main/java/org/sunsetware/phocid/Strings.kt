package org.sunsetware.phocid

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import java.lang.ref.WeakReference
import kotlin.time.Duration
import org.sunsetware.phocid.utils.icuFormat

/**
 * I would argue littering [Context] randomly everywhere is a bigger code smell than a static
 * singleton.
 */
@Stable
object Strings {
    @Stable
    operator fun get(id: Int): String {
        return stringSource.get()?.getString(id)
            ?: run {
                Log.e("Phocid", "Accessing string resource $id after context disposal")
                "<error>"
            }
    }

    @Stable
    fun conjoin(strings: Iterable<String?>): String {
        return strings.filterNotNull().joinToString(get(R.string.symbol_conjunction))
    }

    @Stable
    fun conjoin(vararg strings: String?): String {
        return conjoin(strings.asIterable())
    }

    @Stable
    fun separate(strings: Iterable<String?>): String {
        return strings.filterNotNull().joinToString(get(R.string.symbol_separator))
    }

    @Stable
    fun separate(vararg strings: String?): String {
        return separate(strings.asIterable())
    }
}

fun Duration.format(): String {
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

@Volatile var stringSource = WeakReference<Context>(null)
