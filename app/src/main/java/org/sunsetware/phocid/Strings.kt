package org.sunsetware.phocid

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import java.lang.ref.WeakReference

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
                /* NON-NLS */ "<error>"
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

@Volatile lateinit var stringSource: WeakReference<Context>
