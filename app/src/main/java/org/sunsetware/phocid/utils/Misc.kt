package org.sunsetware.phocid.utils

fun <T : Any> T.takeIfNot(value: T): T? {
    return takeIf { it != value }
}
