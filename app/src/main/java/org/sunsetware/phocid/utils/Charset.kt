package org.sunsetware.phocid.utils

import com.ibm.icu.text.CharsetDetector
import java.nio.ByteBuffer
import java.nio.charset.Charset

fun ByteArray.decodeWithCharsetName(charsetName: String?): String {
    return if (charsetName != null && Charset.isSupported(charsetName)) {
        Charset.forName(charsetName).decode(ByteBuffer.wrap(this)).toString()
    } else {
        CharsetDetector().setText(this).detect().string
    }
}
