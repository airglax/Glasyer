package com.example.ui.create

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.GlasyerStorageRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val UPLOAD_MEDIA_TAG = "GlasyerUploadPicker"

class OpenVideoDocumentContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "video/mp4",
                    "video/webm",
                    "video/3gpp",
                    "video/quicktime",
                    "video/x-matroska",
                    "video/*"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}

class OpenImageDocumentContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/jpeg", "image/png", "image/webp", "image/*")
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

data class SelectedVideoMetadata(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationSec: Long,
    val isVertical: Boolean,
    val autoThumbUri: Uri?
)

fun tryTakePersistableReadPermission(context: Context, uri: Uri) {
    if (uri.scheme != "content") return
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Temporary URI grants from Photo Picker are valid for the active component lifecycle; ignore safely.
    } catch (_: Exception) {
    }
}

fun inspectSelectedVideo(
    context: Context,
    uri: Uri,
    pickerSource: String
): Result<SelectedVideoMetadata> {
    Log.d(UPLOAD_MEDIA_TAG, "Picker [$pickerSource] returned URI: $uri (SDK=${Build.VERSION.SDK_INT})")
    tryTakePersistableReadPermission(context, uri)

    val uriInfo = GlasyerStorageRepository(context).inspectContentUri(
        uri = uri,
        expectedPrefix = "video/",
        fallbackMime = "video/mp4"
    ).getOrElse { err ->
        Log.w(UPLOAD_MEDIA_TAG, "URI inspection failed for $uri from $pickerSource: ${err.message}", err)
        return Result.failure(err)
    }

    var durationSec = 0L
    var isVertical = false
    var autoThumbUri: Uri? = null

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        isVertical = if (rotation == 90 || rotation == 270) width > height else height > width
        durationSec = (durMs / 1000L).coerceAtLeast(if (durMs > 0L) 1L else 0L)

        val targetUs = if (durMs > 2_000L) 1_000_000L else 0L
        val frame = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (frame != null) {
            autoThumbUri = saveBitmapThumbnail(context, frame)
        }
    } catch (e: Exception) {
        Log.w(UPLOAD_MEDIA_TAG, "MediaMetadataRetriever partial read for $uri: ${e.message}", e)
    } finally {
        runCatching { retriever.release() }
    }

    Log.d(
        UPLOAD_MEDIA_TAG,
        "Selected video verified: name=${uriInfo.displayName}, mime=${uriInfo.mimeType}, size=${uriInfo.sizeBytes}, dur=${durationSec}s, vertical=$isVertical"
    )

    return Result.success(
        SelectedVideoMetadata(
            uri = uri,
            displayName = uriInfo.displayName,
            mimeType = uriInfo.mimeType,
            sizeBytes = uriInfo.sizeBytes,
            durationSec = durationSec,
            isVertical = isVertical,
            autoThumbUri = autoThumbUri
        )
    )
}

fun captureVideoFrame(
    context: Context,
    videoUri: Uri,
    positionFraction: Float
): Uri? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, videoUri)
        val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val targetUs = if (durMs > 0L) {
            ((durMs * positionFraction.coerceIn(0.05f, 0.9f)) * 1000L).toLong()
        } else {
            1_000_000L
        }
        val frame = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        frame?.let { saveBitmapThumbnail(context, it) }
    } catch (e: Exception) {
        Log.w(UPLOAD_MEDIA_TAG, "Frame capture failed for $videoUri: ${e.message}", e)
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun saveBitmapThumbnail(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val file = File(context.cacheDir, "glasyer_thumb_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        Uri.fromFile(file)
    } catch (_: Exception) {
        null
    }
}

fun formatDurationSec(sec: Long): String {
    if (sec <= 0L) return "00:00"
    val hours = sec / 3600L
    val mins = (sec % 3600L) / 60L
    val remSec = sec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, remSec)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, remSec)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "अज्ञात आकार"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

fun formatTransferSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.0f KB/s", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}

fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
}
