package com.example.data

import android.content.Context
import com.example.R
import com.example.model.BlockedUser
import com.example.model.UserChannel
import com.example.model.UserSettings
import com.example.model.VideoComment
import com.example.model.VideoItem
import com.example.model.toVideoItemSafe
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class PagedVideosResult(
    val videos: List<VideoItem>,
    val lastVisible: DocumentSnapshot?,
    val hasMore: Boolean
)

class GlasyerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val HOME_PAGE_SIZE = 10L

        fun generateSortableVideoId(): String {
            val reverseEpoch = (9_999_999_999_999L - System.currentTimeMillis()).coerceAtLeast(0L)
            val suffix = (1000..9999).random(Random.Default)
            return "${reverseEpoch}_$suffix"
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("प्रमाणीकरण आवश्यक है। कृपया Google से साइन इन करें।")
    }

    fun observeMyChannel(uid: String): Flow<UserChannel?> = flow {
        val path = "users/$uid"
        db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(UserChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.GET, path, uid) }
            .collect { emit(it) }
    }

    fun observeChannelById(channelUid: String): Flow<UserChannel?> = flow {
        val uid = requireUserId()
        val path = "users/$channelUid"
        db.collection("users").document(channelUid)
            .snapshots()
            .map { it.toObject(UserChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.GET, path, uid) }
            .collect { emit(it) }
    }

    fun observePublicChannels(): Flow<List<UserChannel>> = flow {
        val uid = requireUserId()
        val path = "users"
        db.collection("users")
            .whereEqualTo("publicChannel", true)
            .snapshots()
            .map { snap -> snap.toObjects(UserChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.LIST, path, uid) }
            .collect { emit(it) }
    }

    fun observeUserSettings(uid: String): Flow<UserSettings> = flow {
        val path = "users/$uid/private/settings"
        db.collection("users").document(uid).collection("private").document("settings")
            .snapshots()
            .map { snap ->
                snap.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserSettings(userId = uid)
            }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.GET, path, uid) }
            .collect { emit(it) }
    }

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> = flow {
        val path = "users/$uid/blocked"
        db.collection("users").document(uid).collection("blocked")
            .snapshots()
            .map { snap -> snap.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.LIST, path, uid) }
            .collect { emit(it) }
    }

    fun observeFirstPublicVideosPage(pageSize: Long = HOME_PAGE_SIZE): Flow<PagedVideosResult> = flow {
        val uid = requireUserId()
        val path = "videos"
        db.collection("videos")
            .whereEqualTo("visibility", "public")
            .limit(pageSize)
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { it.toVideoItemSafe() }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                val lastDoc = snap.documents.lastOrNull()
                PagedVideosResult(
                    videos = list,
                    lastVisible = lastDoc,
                    hasMore = snap.documents.size.toLong() >= pageSize
                )
            }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.LIST, path, uid) }
            .collect { emit(it) }
    }

    suspend fun loadMorePublicVideos(
        lastVisible: DocumentSnapshot,
        pageSize: Long = HOME_PAGE_SIZE
    ): Result<PagedVideosResult> {
        val path = "videos"
        return try {
            requireUserId()
            val snap = db.collection("videos")
                .whereEqualTo("visibility", "public")
                .startAfter(lastVisible)
                .limit(pageSize)
                .get()
                .await()
            val list = snap.documents.mapNotNull { it.toVideoItemSafe() }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            val nextCursor = snap.documents.lastOrNull()
            Result.success(
                PagedVideosResult(
                    videos = list,
                    lastVisible = nextCursor,
                    hasMore = snap.documents.size.toLong() >= pageSize
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    fun observeMyVideos(uid: String): Flow<List<VideoItem>> = flow {
        val path = "videos"
        db.collection("videos")
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { it.toVideoItemSafe() }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.LIST, path, uid) }
            .collect { emit(it) }
    }

    fun observeComments(videoId: String, videoVisibility: String, videoOwnerId: String): Flow<List<VideoComment>> = flow {
        val uid = requireUserId()
        val path = "videos/$videoId/comments"
        val query = if (videoVisibility == "private") {
            db.collection("videos").document(videoId).collection("comments")
                .whereEqualTo("videoOwnerId", videoOwnerId)
        } else {
            db.collection("videos").document(videoId).collection("comments")
                .whereEqualTo("videoVisibility", videoVisibility)
        }
        query.snapshots()
            .map { snap ->
                snap.toObjects(VideoComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error -> logAndRethrowIfNeeded(error, OperationType.LIST, path, uid) }
            .collect { emit(it) }
    }

    suspend fun ensureUserInitialized(
        uid: String,
        defaultName: String,
        defaultPhoto: String
    ): Result<UserChannel> {
        val path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "उपयोगकर्ता सत्र मेल नहीं खाता।" }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val parsed = existing.toObject(UserChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserChannel(id = uid, userId = uid)
                Result.success(parsed)
            } else {
                val cleanName = defaultName.ifBlank { "GLASYER क्रिएटर" }.take(80)
                val handleBase = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "glasyer" }
                val handle = "${handleBase.take(24)}_${uid.take(5)}"
                val payload = mapOf(
                    "userId" to uid,
                    "username" to handle,
                    "displayName" to cleanName,
                    "photoUrl" to defaultPhoto.take(1500),
                    "bannerUrl" to "",
                    "bio" to "",
                    "subscriberCount" to 0L,
                    "verified" to false,
                    "publicChannel" to true,
                    "onboardingDone" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()

                val settingsPayload = mapOf(
                    "userId" to uid,
                    "themeMode" to "system",
                    "language" to "hi",
                    "autoplay" to true,
                    "playbackQuality" to "auto",
                    "restrictedMode" to false,
                    "dataSaver" to false,
                    "historyPaused" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.collection("private").document("settings").set(settingsPayload).await()

                Result.success(
                    UserChannel(
                        id = uid,
                        userId = uid,
                        username = handle,
                        displayName = cleanName,
                        photoUrl = defaultPhoto,
                        bio = "",
                        onboardingDone = false
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun saveChannelProfile(
        username: String,
        displayName: String,
        photoUrl: String,
        bannerUrl: String,
        bio: String,
        onboardingDone: Boolean = true
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            val cleanUser = username.trim().replace(" ", "_").take(40)
            require(cleanUser.length >= 2) { "यूज़रनेम कम से कम 2 अक्षर का होना चाहिए।" }
            val cleanDisplay = displayName.trim().take(80)
            require(cleanDisplay.isNotEmpty()) { "चैनल का नाम आवश्यक है।" }

            db.collection("users").document(activeUid).update(
                mapOf(
                    "username" to cleanUser,
                    "displayName" to cleanDisplay,
                    "photoUrl" to photoUrl.take(1500),
                    "bannerUrl" to bannerUrl.take(1500),
                    "bio" to bio.trim().take(300),
                    "publicChannel" to true,
                    "onboardingDone" to onboardingDone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun updateSettings(settings: UserSettings): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/private/settings"
        return try {
            val activeUid = requireUserId()
            val docRef = db.collection("users").document(activeUid).collection("private").document("settings")
            docRef.set(
                mapOf(
                    "userId" to activeUid,
                    "themeMode" to settings.themeMode,
                    "language" to settings.language,
                    "autoplay" to settings.autoplay,
                    "playbackQuality" to settings.playbackQuality,
                    "restrictedMode" to settings.restrictedMode,
                    "dataSaver" to settings.dataSaver,
                    "historyPaused" to settings.historyPaused,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun createVideo(videoId: String, video: VideoItem): Result<String> {
        val path = "videos/$videoId"
        return try {
            val uid = requireUserId()
            require(videoId.isNotBlank()) { "अमान्य वीडियो ID।" }
            val docRef = db.collection("videos").document(videoId)
            val cleanTitle = video.title.trim().take(150)
            require(cleanTitle.isNotEmpty()) { "वीडियो का शीर्षक आवश्यक है।" }
            val cleanUrl = video.videoUrl.trim().take(2000)
            require(cleanUrl.startsWith("https://")) { "वीडियो फ़ाइल Firebase Storage पर अपलोड नहीं हुई है।" }

            val payload = mapOf(
                "videoId" to videoId,
                "ownerUid" to uid,
                "channelName" to video.channelName.ifBlank { "GLASYER क्रिएटर" }.take(80),
                "channelAvatar" to video.channelAvatar.take(1500),
                "title" to cleanTitle,
                "description" to video.description.trim().take(2000),
                "tags" to video.tags.take(10),
                "category" to video.category.ifBlank { "सब" }.take(40),
                "videoType" to video.videoType,
                "visibility" to video.visibility,
                "videoUrl" to cleanUrl,
                "thumbnailUrl" to video.thumbnailUrl.take(2000),
                "durationLabel" to video.durationLabel.ifBlank { "00:00" }.take(20),
                "duration" to video.duration.coerceAtLeast(0L),
                "viewsCount" to 0L,
                "likesCount" to 0L,
                "dislikesCount" to 0L,
                "commentsCount" to 0L,
                "viewerCount" to if (video.videoType == "live") 1L else 0L,
                "commentsEnabled" to video.commentsEnabled,
                "ageRestricted" to video.ageRestricted,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun updateVideoMetadata(
        videoId: String,
        title: String,
        description: String,
        category: String,
        visibility: String,
        commentsEnabled: Boolean,
        ageRestricted: Boolean
    ): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            require(videoId.isNotBlank())
            val cleanTitle = title.trim().take(150)
            require(cleanTitle.isNotEmpty()) { "शीर्षक खाली नहीं हो सकता।" }
            db.collection("videos").document(videoId).update(
                mapOf(
                    "title" to cleanTitle,
                    "description" to description.trim().take(2000),
                    "category" to category.take(40),
                    "visibility" to visibility,
                    "commentsEnabled" to commentsEnabled,
                    "ageRestricted" to ageRestricted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun incrementVideoView(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun addComment(
        video: VideoItem,
        authorName: String,
        authorAvatar: String,
        text: String,
        parentId: String = ""
    ): Result<Unit> {
        val vid = video.resolvedId
        val path = "videos/$vid/comments"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(1000)
            require(cleanText.isNotEmpty()) { "टिप्पणी खाली नहीं हो सकती।" }
            val commentRef = db.collection("videos").document(vid).collection("comments").document()
            commentRef.set(
                mapOf(
                    "videoId" to vid,
                    "videoOwnerId" to video.ownerUid,
                    "videoVisibility" to video.visibility,
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "उपयोगकर्ता" }.take(80),
                    "authorAvatar" to authorAvatar.take(1500),
                    "text" to cleanText,
                    "parentId" to parentId.take(128),
                    "likesCount" to 0L,
                    "pinned" to false,
                    "creatorHearted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("videos").document(vid).update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun updateCommentModeration(
        videoId: String,
        commentId: String,
        pinned: Boolean,
        creatorHearted: Boolean
    ): Result<Unit> {
        val path = "videos/$videoId/comments/$commentId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).collection("comments").document(commentId)
                .update(
                    mapOf(
                        "pinned" to pinned,
                        "creatorHearted" to creatorHearted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun toggleCommentLike(videoId: String, commentId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val likeDocId = "${uid}_c_${commentId}"
        val path = "videos/$videoId/comments/$commentId"
        return try {
            val activeUid = requireUserId()
            val likeRef = db.collection("likes").document(likeDocId)
            val commentRef = db.collection("videos").document(videoId).collection("comments").document(commentId)
            db.runTransaction { tx ->
                val likeSnap = tx.get(likeRef)
                if (likeSnap.exists()) {
                    tx.delete(likeRef)
                    tx.update(
                        commentRef,
                        mapOf(
                            "likesCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        likeRef,
                        mapOf(
                            "userId" to activeUid,
                            "targetId" to commentId,
                            "targetType" to "comment",
                            "reaction" to "like",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    tx.update(
                        commentRef,
                        mapOf(
                            "likesCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun deleteComment(videoId: String, commentId: String): Result<Unit> {
        val path = "videos/$videoId/comments/$commentId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).collection("comments").document(commentId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun blockUser(blockedUserId: String, blockedName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/blocked/$blockedUserId"
        return try {
            val activeUid = requireUserId()
            db.collection("users").document(activeUid).collection("blocked").document(blockedUserId)
                .set(
                    mapOf(
                        "blockedUserId" to blockedUserId,
                        "blockedName" to blockedName.ifBlank { "क्रिएटर" }.take(100),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/blocked/$blockedUserId"
        return try {
            val activeUid = requireUserId()
            db.collection("users").document(activeUid).collection("blocked").document(blockedUserId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun deleteMyAccountDoc(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val activeUid = requireUserId()
            db.collection("users").document(activeUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    private fun logAndRethrowIfNeeded(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw IllegalStateException(mapFirestoreErrorHindi(error), error)
    }
}

fun mapFirestoreErrorHindi(error: Throwable): String {
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreError?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore अनुमति अस्वीकृत। कृपया लॉगिन स्थिति और सुरक्षा नियम जांचें।"
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "प्रमाणीकरण आवश्यक है। कृपया Google से साइन इन करें।"
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "इंटरनेट उपलब्ध नहीं है या Firestore सर्वर अनुपलब्ध है। कृपया पुनः प्रयास करें।"
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "डेटाबेस इंडेक्स या शर्त पूरी नहीं हुई है।"
        else -> error.localizedMessage ?: "Firestore संचालन विफल रहा। कृपया पुनः प्रयास करें।"
    }
}
