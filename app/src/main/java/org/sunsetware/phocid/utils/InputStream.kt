package org.sunsetware.phocid.utils

import java.io.DataInputStream
import java.io.InputStream

fun InputStream.readAllBytesCompat(): ByteArray {
    val buffer = ByteArray(available())
    DataInputStream(this).readFully(buffer)
    return buffer
}
