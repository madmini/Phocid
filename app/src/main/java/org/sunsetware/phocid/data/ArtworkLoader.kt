package org.sunsetware.phocid.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.view.WindowManager
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import org.apache.commons.io.FilenameUtils

/** https://developer.android.com/media/platform/supported-formats#image-formats */
private val imageFileExtensionScores =
    listOf("png", "bmp", "jpg", "jpeg", "webp", "heif", "heic", "gif")
        .reversed()
        .mapIndexed { index, extension -> extension to index }
        .toMap()

/** "Folder" seems to be off-limits and can't be accessed, listed for completeness */
private val imageFileNameScores =
    listOf("cover", "folder", "artwork", "front", "album")
        .sortedDescending()
        .mapIndexed { index, name -> name to index }
        .toMap()

private val cachedScreenSize = AtomicInteger(0)

fun loadArtwork(
    context: Context,
    uri: Uri,
    path: String?,
    highRes: Boolean = false,
    sizeLimit: Int? = null,
): Bitmap? {
    val forcedSizeLimit =
        sizeLimit
            ?: cachedScreenSize.get().takeIf { it > 0 }
            ?: run {
                val screenSize =
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .maximumWindowMetrics
                        .bounds
                val limit = min(screenSize.width(), screenSize.height()).coerceAtLeast(256)
                cachedScreenSize.set(limit)
                limit
            }

    return if (highRes) {
        loadWithJaudiotagger(path, forcedSizeLimit)
            ?: loadWithContentResolver(context, uri, forcedSizeLimit)
            ?: loadExternal(context, path, forcedSizeLimit)
    } else {
        loadWithContentResolver(context, uri, forcedSizeLimit)
            ?: loadExternal(context, path, forcedSizeLimit)
    }
}

fun loadArtwork(
    context: Context,
    id: Long,
    path: String?,
    highRes: Boolean = false,
    sizeLimit: Int? = null,
): Bitmap? {
    return loadArtwork(
        context,
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
        path,
        highRes,
        sizeLimit,
    )
}

private fun loadWithJaudiotagger(path: String?, sizeLimit: Int?): Bitmap? {
    val file =
        try {
            readJaudiotaggerFile(requireNotNull(path))
        } catch (_: Exception) {
            null
        }
    return try {
        ImageDecoder.decodeBitmap(
            file
                ?.tag
                ?.firstArtwork
                ?.binaryData
                .let(::requireNotNull)
                .let(ByteBuffer::wrap)
                .let(ImageDecoder::createSource)
        ) { decoder, info, source ->
            if (sizeLimit != null) {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                decoder.setTargetSize(sizeLimit * info.size.width / info.size.height, sizeLimit)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadWithContentResolver(context: Context, uri: Uri, sizeLimit: Int): Bitmap? {
    return try {
        context.contentResolver.loadThumbnail(uri, Size(sizeLimit, sizeLimit), null)
    } catch (_: Exception) {
        null
    }
}

private fun loadExternal(context: Context, path: String?, sizeLimit: Int?): Bitmap? {
    if (path == null) return null

    val trackName = FilenameUtils.getBaseName(path)
    val directoryName = FilenameUtils.getName(FilenameUtils.getPathNoEndSeparator(path))
    val files =
        try {
            File(FilenameUtils.getPath(path)).listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
    return files
        .mapNotNull {
            val name = it.nameWithoutExtension
            val extension = it.extension
            // higher score is better
            val extensionScore =
                imageFileExtensionScores[extension.lowercase()] ?: return@mapNotNull null
            val nameScore =
                when {
                    name.equals(trackName, true) -> 999
                    name.equals(directoryName, true) -> 998
                    else -> imageFileNameScores[name.lowercase()] ?: return@mapNotNull null
                }

            it to (nameScore * 1000 + extensionScore)
        }
        .sortedByDescending { it.second }
        .firstNotNullOfOrNull { (file, _) ->
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, source
                    ->
                    if (sizeLimit != null) {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                        decoder.setTargetSize(
                            sizeLimit * info.size.width / info.size.height,
                            sizeLimit,
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
}
