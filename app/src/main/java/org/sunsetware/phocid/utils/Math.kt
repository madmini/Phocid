package org.sunsetware.phocid.utils

fun Int.wrap(other: Int, repeat: Boolean): Int? {
    return if (other > 0) {
        if (repeat) mod(other) else this.takeIf { it in 0..<other }
    } else null
}
