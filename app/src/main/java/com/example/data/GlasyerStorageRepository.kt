package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.model.UploadProgressState
import com.example.model.UploadSubStage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageTask
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.UnknownHostException

data class UploadedMediaUrls(
    val videoUrl: String,
    val thumbnailUrl: String
)

data class ContentUriInfo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class GlasyerStorageRepository(
    private val context: Context,
    private val storage: FirebaseStorage = Firebase.storage,
    private val auth: FirebaseAuth = Firebase.auth
) {
    companion object {
        private const val TAG = "GlasyerStorage"
        const val MAX_VIDEO_BYTES = 100L * 1024L * 1024L // 100 MB
        const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L  // 10 MB
        private val SUPPORTED_VIDEO_MIME_PREFIXES = listOf(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/3gpp",
            "video/x-matroska"
        )
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("प्रमाणीकरण आवश्यक है। कृपया Google से साइन इन करें।")
    }

    /**
     * Inspects any content:// or file:// URI via ContentResolver without buffering media into RAM.
     * Verifies read permission / existence and resolves display name, MIME type, and file size.
     */
    fun inspectContentUri(
        uri: Uri,
        expectedPrefix: String = "video/",
        fallbackMime: String = "video/mp4"
    ): Result<ContentUriInfo> {
        var displayName = ""
        var sizeBytes = 0L

        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file == null || !file.exists() || !file.canRead()) {
                return Result.failure(
                    FileNotFoundException("चयनित फ़ाइल उपलब्ध नहीं है या पढ़ी नहीं जा सकती। कृपया दूसरी फ़ाइल चुनें।")
                )
            }
            displayName = file.name
            sizeBytes = file.length()
        } else {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx).orEmpty()
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } catch (sec: SecurityException) {
                Log.w(TAG, "Missing URI read permission for $uri", sec)
                return Result.failure(
                    SecurityException("फ़ाइल पढ़ने की अनुमति उपलब्ध नहीं है। कृपया पिकर से फ़ाइल पुनः चुनें।", sec)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cursor query failed for $uri: ${e.message}", e)
            }

            if (sizeBytes <= 0L) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.length > 0L) {
                            sizeBytes = afd.length
                        }
                    }
                } catch (sec: SecurityException) {
                    Log.w(TAG, "AssetFileDescriptor security error for $uri", sec)
                    return Result.failure(
                        SecurityException("फ़ाइल पढ़ने की अनुमति उपलब्ध नहीं है। कृपया पिकर से फ़ाइल पुनः चुनें।", sec)
                    )
                } catch (fnf: FileNotFoundException) {
                    return Result.failure(
                        FileNotFoundException("चयनित फ़ाइल नहीं मिली। कृपया दूसरी फ़ाइल चुनें।")
                    )
                } catch (_: Exception) {
                }
            }

            // Stream readability check without reading full bytes into RAM
            try {
                val opened = context.contentResolver.openInputStream(uri)
                if (opened == null) {
                    return Result.failure(
                        FileNotFoundException("चयनित फ़ाइल खोली नहीं जा सकी। कृपया दूसरी फ़ाइल चुनें।")
                    )
                }
                opened.close()
            } catch (sec: SecurityException) {
                Log.w(TAG, "Stream open SecurityException for $uri", sec)
                return Result.failure(
                    SecurityException("फ़ाइल पढ़ने की अनुमति उपलब्ध नहीं है। कृपया फ़ाइल पुनः चुनें।", sec)
                )
            } catch (fnf: FileNotFoundException) {
                return Result.failure(
                    FileNotFoundException("चयनित फ़ाइल डिवाइस पर मौजूद नहीं है।")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Stream open failed for $uri: ${e.message}", e)
                return Result.failure(
                    IllegalStateException("फ़ाइल पढ़ी नहीं जा सकी। कृपया दूसरी फ़ाइल चुनें।", e)
                )
            }
        }

        if (displayName.isBlank()) {
            displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "media_file" } ?: "media_file"
        }

        val mime = resolveMimeType(uri, displayName, fallbackMime)
        if (!mime.startsWith(expectedPrefix)) {
            return Result.failure(
                IllegalArgumentException(
                    if (expectedPrefix == "video/") {
                        "असमर्थित वीडियो फ़ॉर्मेट ($mime)। कृपया MP4, WebM, 3GP या MOV वीडियो चुनें।"
                    } else {
                        "असमर्थित छवि फ़ॉर्मेट ($mime)। कृपया JPG, PNG या WebP छवि चुनें।"
                    }
                )
            )
        }

        val maxLimit = if (expectedPrefix == "video/") MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
        if (sizeBytes > maxLimit) {
            return Result.failure(
                IllegalArgumentException(
                    if (expectedPrefix == "video/") {
                        "अपलोड आकार सीमा (100 MB) से अधिक है। कृपया छोटा वीडियो चुनें।"
                    } else {
                        "छवि का आकार 10 MB की सीमा से अधिक है।"
                    }
                )
            )
        }

        return Result.success(
            ContentUriInfo(
                uri = uri,
                displayName = displayName,
                mimeType = mime,
                sizeBytes = sizeBytes.coerceAtLeast(0L)
            )
        )
    }

    fun validateVideoUri(uri: Uri): Result<Pair<String, Long>> {
        val info = inspectContentUri(uri, expectedPrefix = "video/", fallbackMime = "video/mp4")
            .getOrElse { return Result.failure(it) }
        val isKnownSupported = info.mimeType.startsWith("video/") &&
            (SUPPORTED_VIDEO_MIME_PREFIXES.any { info.mimeType.equals(it, ignoreCase = true) } ||
                info.mimeType == "video/*" ||
                info.mimeType.startsWith("video/"))
        if (!isKnownSupported) {
            return Result.failure(
                IllegalArgumentException("असमर्थित वीडियो फ़ॉर्मेट (${info.mimeType})। कृपया MP4, WebM या MOV वीडियो चुनें।")
            )
        }
        return Result.success(info.mimeType to info.sizeBytes)
    }

    fun validateImageUri(uri: Uri): Result<Pair<String, Long>> {
        val info = inspectContentUri(uri, expectedPrefix = "image/", fallbackMime = "image/jpeg")
            .getOrElse { return Result.failure(it) }
        return Result.success(info.mimeType to info.sizeBytes)
    }

    suspend fun uploadVideoAndThumbnail(
        videoId: String,
        videoUri: Uri,
        thumbnailUri: Uri?,
        onTaskCreated: (StorageTask<UploadTask.TaskSnapshot>) -> Unit = {},
        onProgress: (UploadProgressState) -> Unit
    ): Result<UploadedMediaUrls> {
        return try {
            val uid = requireUid()
            if (!isNetworkAvailable()) {
                return Result.failure(IllegalStateException("इंटरनेट उपलब्ध नहीं है। कृपया कनेक्शन जांचें और पुनः प्रयास करें।"))
            }
            val bucket = storage.reference.bucket
            if (bucket.isBlank()) {
                return Result.failure(IllegalStateException("Firebase Storage कॉन्फ़िगर नहीं है। कृपया Firebase Console में Storage सक्षम करें।"))
            }

            val inspected = inspectContentUri(videoUri, expectedPrefix = "video/", fallbackMime = "video/mp4")
                .getOrElse { return Result.failure(it) }
            val videoMime = inspected.mimeType
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(videoMime)?.ifBlank { "mp4" } ?: "mp4"
            val initialThumbStage = if (thumbnailUri != null) UploadSubStage.IDLE else UploadSubStage.SKIPPED

            onProgress(
                UploadProgressState(
                    stageLabel = "वीडियो अपलोड हो रहा है...",
                    fraction = 0.02f,
                    bytesUploaded = 0L,
                    totalBytes = inspected.sizeBytes,
                    speedBytesPerSec = 0L,
                    videoStage = UploadSubStage.IN_PROGRESS,
                    thumbnailStage = initialThumbStage,
                    publishStage = UploadSubStage.IDLE
                )
            )

            // putFile streams directly from ContentResolver InputStream without buffering into RAM
            val videoRef = storage.reference.child("videos/$uid/$videoId/video.$ext")
            val videoMeta = storageMetadata { contentType = videoMime }
            val videoTask = videoRef.putFile(videoUri, videoMeta)
            onTaskCreated(videoTask)

            var lastBytes = 0L
            var lastTimeMs = System.currentTimeMillis()
            var smoothedSpeed = 0L

            videoTask.addOnProgressListener { snap ->
                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastTimeMs).coerceAtLeast(1L)
                val deltaBytes = (snap.bytesTransferred - lastBytes).coerceAtLeast(0L)
                if (elapsedMs >= 250L && deltaBytes > 0L) {
                    val instantSpeed = (deltaBytes * 1000L) / elapsedMs
                    smoothedSpeed = if (smoothedSpeed == 0L) instantSpeed else ((smoothedSpeed * 2 + instantSpeed) / 3)
                    lastBytes = snap.bytesTransferred
                    lastTimeMs = now
                }
                val total = snap.totalByteCount.takeIf { it > 0L } ?: inspected.sizeBytes.coerceAtLeast(1L)
                val ratio = (snap.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    UploadProgressState(
                        stageLabel = "वीडियो अपलोड हो रहा है...",
                        fraction = ratio * 0.80f,
                        bytesUploaded = snap.bytesTransferred,
                        totalBytes = total,
                        speedBytesPerSec = smoothedSpeed,
                        videoStage = UploadSubStage.IN_PROGRESS,
                        thumbnailStage = initialThumbStage,
                        publishStage = UploadSubStage.IDLE
                    )
                )
            }
            videoTask.await()
            val uploadedVideoUrl = videoRef.downloadUrl.await().toString()

            var uploadedThumbUrl = ""
            if (thumbnailUri != null) {
                val thumbValidation = validateImageUri(thumbnailUri)
                if (thumbValidation.isSuccess) {
                    val (thumbMime, thumbSize) = thumbValidation.getOrThrow()
                    val thumbExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(thumbMime)?.ifBlank { "jpg" } ?: "jpg"
                    onProgress(
                        UploadProgressState(
                            stageLabel = "थंबनेल अपलोड हो रहा है...",
                            fraction = 0.84f,
                            bytesUploaded = inspected.sizeBytes,
                            totalBytes = inspected.sizeBytes,
                            speedBytesPerSec = smoothedSpeed,
                            videoStage = UploadSubStage.COMPLETED,
                            thumbnailStage = UploadSubStage.IN_PROGRESS,
                            publishStage = UploadSubStage.IDLE
                        )
                    )
                    val thumbRef = storage.reference.child("videos/$uid/$videoId/thumb.$thumbExt")
                    val thumbMeta = storageMetadata { contentType = thumbMime }
                    val thumbTask = thumbRef.putFile(thumbnailUri, thumbMeta)
                    onTaskCreated(thumbTask)
                    thumbTask.addOnProgressListener { snap ->
                        val tTotal = snap.totalByteCount.takeIf { it > 0L } ?: thumbSize.coerceAtLeast(1L)
                        val tRatio = (snap.bytesTransferred.toFloat() / tTotal.toFloat()).coerceIn(0f, 1f)
                        onProgress(
                            UploadProgressState(
                                stageLabel = "थंबनेल अपलोड हो रहा है...",
                                fraction = 0.84f + (tRatio * 0.08f),
                                bytesUploaded = inspected.sizeBytes,
                                totalBytes = inspected.sizeBytes,
                                speedBytesPerSec = smoothedSpeed,
                                videoStage = UploadSubStage.COMPLETED,
                                thumbnailStage = UploadSubStage.IN_PROGRESS,
                                publishStage = UploadSubStage.IDLE
                            )
                        )
                    }
                    thumbTask.await()
                    uploadedThumbUrl = thumbRef.downloadUrl.await().toString()
                }
            }

            onProgress(
                UploadProgressState(
                    stageLabel = "Firestore में सहेजा जा रहा है...",
                    fraction = 0.95f,
                    bytesUploaded = inspected.sizeBytes,
                    totalBytes = inspected.sizeBytes,
                    speedBytesPerSec = 0L,
                    videoStage = UploadSubStage.COMPLETED,
                    thumbnailStage = if (uploadedThumbUrl.isNotBlank()) UploadSubStage.COMPLETED else UploadSubStage.SKIPPED,
                    publishStage = UploadSubStage.IN_PROGRESS
                )
            )
            Result.success(UploadedMediaUrls(videoUrl = uploadedVideoUrl, thumbnailUrl = uploadedThumbUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Upload error: ${e.message}", e)
            Result.failure(IllegalStateException(mapStorageErrorToHindi(e), e))
        }
    }

    suspend fun uploadProfileImage(kind: String, imageUri: Uri): Result<String> {
        return try {
            val uid = requireUid()
            if (!isNetworkAvailable()) {
                return Result.failure(IllegalStateException("इंटरनेट उपलब्ध नहीं है। कृपया कनेक्शन जांचें और पुनः प्रयास करें।"))
            }
            val (mime, _) = validateImageUri(imageUri).getOrElse { return Result.failure(it) }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.ifBlank { "jpg" } ?: "jpg"
            val ref = storage.reference.child("users/$uid/${kind}_${System.currentTimeMillis()}.$ext")
            val meta = storageMetadata { contentType = mime }
            ref.putFile(imageUri, meta).await()
            Result.success(ref.downloadUrl.await().toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapStorageErrorToHindi(e), e))
        }
    }

    suspend fun uploadPostImages(postId: String, uris: List<Uri>): Result<List<String>> {
        return try {
            val uid = requireUid()
            if (!isNetworkAvailable()) {
                return Result.failure(IllegalStateException("इंटरनेट उपलब्ध नहीं है। कृपया कनेक्शन जांचें।"))
            }
            val urls = mutableListOf<String>()
            uris.take(10).forEachIndexed { idx, uri ->
                val (mime, _) = validateImageUri(uri).getOrElse { return Result.failure(it) }
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.ifBlank { "jpg" } ?: "jpg"
                val ref = storage.reference.child("posts/$uid/$postId/img_$idx.$ext")
                val meta = storageMetadata { contentType = mime }
                ref.putFile(uri, meta).await()
                urls.add(ref.downloadUrl.await().toString())
            }
            Result.success(urls)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapStorageErrorToHindi(e), e))
        }
    }

    private fun resolveMimeType(uri: Uri, displayName: String, fallback: String): String {
        val resolverMime = if (uri.scheme != "file") context.contentResolver.getType(uri) else null
        if (!resolverMime.isNullOrBlank() && resolverMime != "application/octet-stream") {
            return resolverMime
        }
        val candidateName = displayName.ifBlank { uri.toString() }
        val ext = candidateName.substringAfterLast('.', "").lowercase()
            .ifBlank { MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase() }
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapped.isNullOrBlank()) return mapped
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> if (ext.isNotBlank() && uri.scheme == "file") "application/$ext" else fallback
        }
    }

    fun mapStorageErrorToHindi(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        if (causes.any { it is SecurityException }) {
            return "फ़ाइल पढ़ने की अनुमति समाप्त हो गई है। कृपया वीडियो पुनः चुनें।"
        }
        if (causes.any { it is FileNotFoundException }) {
            return "चयनित फ़ाइल नहीं मिली। कृपया दूसरी फ़ाइल चुनें।"
        }
        val storageEx = causes.filterIsInstance<StorageException>().firstOrNull()
        if (storageEx != null) {
            return when (storageEx.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Firebase Storage कॉन्फ़िगर नहीं है। कृपया Firebase Console में Storage सक्षम करें।"
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "प्रमाणीकरण आवश्यक है। कृपया Google से साइन इन करें।"
                StorageException.ERROR_NOT_AUTHORIZED ->
                    "Storage अनुमति अस्वीकृत। कृपया Storage सुरक्षा नियम जांचें।"
                StorageException.ERROR_QUOTA_EXCEEDED ->
                    "Storage कोटा समाप्त हो गया है।"
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                    "इंटरनेट उपलब्ध नहीं है या अपलोड समय समाप्त हो गया। पुनः प्रयास करें।"
                StorageException.ERROR_CANCELED ->
                    "अपलोड रद्द किया गया।"
                else ->
                    "अपलोड विफल रहा (${storageEx.errorCode}): कृपया Firebase Storage सेटिंग्स और नेटवर्क जांचें।"
            }
        }
        if (causes.any { it is FirebaseNetworkException || it is UnknownHostException || it is ConnectException }) {
            return "इंटरनेट उपलब्ध नहीं है। कृपया कनेक्शन जांचें और पुनः प्रयास करें।"
        }
        return error.localizedMessage ?: "अपलोड विफल रहा। कृपया पुनः प्रयास करें।"
    }
}
