package org.sunsetware.phocid.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.database.getLongOrNull

@Immutable data class SafFile(val uri: Uri, val name: String, val lastModified: Long?)

fun listSafFiles(context: Context, uri: Uri): Map<String, SafFile>? {
    val treeUri =
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

    val resolver = context.contentResolver
    val childrenUri =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(treeUri),
        )
    val results = mutableMapOf<String, SafFile>()

    // Android API source used a suspicious try catch here, keeping it just in case
    try {
        requireNotNull(
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(0)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val documentId = cursor.getString(1)
                    val documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val name = cursor.getString(2)
                    val lastModified = cursor.getLongOrNull(3)
                    results[name] = SafFile(documentUri, name, lastModified)
                }
            }
    } catch (ex: Exception) {
        Log.e("Phocid", "Error listing files for $uri", ex)
        return null
    }

    return results
}
