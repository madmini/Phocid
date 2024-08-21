package org.sunsetware.phocid.utils

import android.util.Log
import com.ibm.icu.text.MessageFormat
import com.ibm.icu.text.Normalizer2

fun String.trimAndNormalize(): String {
    return Normalizer2.getNFCInstance().normalize(this.trim())
}

fun String.icuFormat(vararg args: Any?): String {
    return try {
        MessageFormat.format(this, *args)
    } catch (ex: Exception) {
        Log.e("Phocid", "Can't format string \"$this\" with (${args.joinToString(", ")})", ex)
        this
    }
}
