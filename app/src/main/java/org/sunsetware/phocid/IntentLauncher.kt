package org.sunsetware.phocid

import android.net.Uri

interface IntentLauncher {
    fun openDocumentTree(continuation: (Uri?) -> Unit)
}
