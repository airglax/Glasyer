package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CommunityPost
import com.example.model.LikeRecord
import com.example.model.LiveChatMessage
import com.example.model.NotificationItem
import com.example.model.Playlist
import com.example.model.PostComment
import com.example.model.Subscription
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.isIdentifiableDemoVideo
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

class GlasyerInteractionsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private const val LEGACY_DEMO_POST_TEXT = "आप अगला बड़ा वीडियो किस श्रेणी में देखना पसंद करेंगे? नीचे वोट करें!"
        private const val LEGACY_DEMO_NOTIF_TITLE = "GLASYER में आपका स्वागत है!"
        private const val LEGACY_DEMO_CHAT_MSG = "GLASYER लाइव चैट में स्वागत है! अपना सवाल भेजें 🏏"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("प्रमाणीकरण आवश्यक है। कृपया Google से साइन इन करें।")
    }

    fun observeSubscriptions(uid: String): Flow<List<Subscription>> = flow {
        db.collection("subscriptions")
            .whereEqualTo("subscriberId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(Subscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { logAndRethrow(it, OperationType.LIST, "subscriptions", uid) }
            .collect { emit(it) }
    }

    fun observeLikes(uid: String): Flow<List<LikeRecord>> = flow {
        db.collection("likes")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(LikeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { logAndRethrow(it, OperationType.LIST, "likes", uid) }
            .collect { emit(it) }
    }

    fun observePlaylists(uid: String): Flow<List<Playlist>> = flow {
        db.collection("playlists")
            .whereEqualTo("ownerId", uid)
            .snapshots()
            .map { snap -> snap.toObjects(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { logAndRethrow(it, OperationType.LIST, "playlists", uid) }
            .collect { emit(it) }
    }

    fun observePublicPosts(): Flow<List<CommunityPost>> = flow {
        val uid = requireUserId()
        db.collection("posts")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                snap.toObjects(CommunityPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.text != LEGACY_DEMO_POST_TEXT }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { logAndRethrow(it, OperationType.LIST, "posts", uid) }
            .collect { emit(it) }
    }

    fun observePostComments(postId: String): Flow<List<PostComment>> = flow {
        val uid = requireUserId()
        db.collection("posts").document(postId).collection("postComments")
            .whereEqualTo("postVisibility", "public")
            .snapshots()
            .map { snap -> snap.toObjects(PostComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { logAndRethrow(it, OperationType.LIST, "posts/$postId/postComments", uid) }
            .collect { emit(it) }
    }

    fun observeLiveChats(videoId: String): Flow<List<LiveChatMessage>> = flow {
        val uid = requireUserId()
        db.collection("liveChats")
            .whereEqualTo("videoId", videoId)
            .whereEqualTo("roomPublic", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.message != LEGACY_DEMO_CHAT_MSG }
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { logAndRethrow(it, OperationType.LIST, "liveChats", uid) }
            .collect { emit(it) }
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        db.collection("notifications")
            .whereEqualTo("recipientId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.title != LEGACY_DEMO_NOTIF_TITLE }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { logAndRethrow(it, OperationType.LIST, "notifications", uid) }
            .collect { emit(it) }
    }

    fun observeHistory(uid: String): Flow<List<WatchHistoryEntry>> = flow {
        db.collection("history")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    WatchHistoryEntry(
                        id = doc.id,
                        userId = (data["userId"] as? String).orEmpty(),
                        videoId = (data["videoId"] as? String).orEmpty(),
                        videoTitle = (data["videoTitle"] as? String).orEmpty(),
                        channelName = (data["channelName"] as? String).orEmpty(),
                        thumbnailUrl = (data["thumbnailUrl"] as? String) ?: (data["thumbnailKey"] as? String).orEmpty(),
                        durationLabel = (data["durationLabel"] as? String).orEmpty(),
                        watchedAt = doc.getTimestamp("watchedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { it.watchedAt?.seconds ?: 0L }
            }
            .catch { logAndRethrow(it, OperationType.LIST, "history", uid) }
            .collect { emit(it) }
    }

    /**
     * Transaction-safe subscription toggle that prevents duplicate subscriptions
     * and keeps users/{channelId}.subscriberCount accurate in Firestore.
     */
    suspend fun toggleSubscription(
        channelId: String,
        channelName: String,
        channelAvatar: String
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val docId = "${uid}_${channelId}"
        val path = "subscriptions/$docId"
        return try {
            val activeUid = requireUserId()
            val subRef = db.collection("subscriptions").document(docId)
            val channelRef = db.collection("users").document(channelId)

            val nowSubscribed = db.runTransaction { tx ->
                val subSnap = tx.get(subRef)
                val channelSnap = tx.get(channelRef)
                if (subSnap.exists()) {
                    tx.delete(subRef)
                    if (channelSnap.exists()) {
                        val currentSubs = channelSnap.getLong("subscriberCount") ?: 0L
                        val nextSubs = (currentSubs - 1L).coerceAtLeast(0L)
                        tx.update(
                            channelRef,
                            mapOf(
                                "subscriberCount" to nextSubs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    false
                } else {
                    tx.set(
                        subRef,
                        mapOf(
                            "subscriberId" to activeUid,
                            "channelId" to channelId,
                            "channelName" to channelName.ifBlank { "क्रिएटर" }.take(80),
                            "channelAvatar" to channelAvatar.take(1500),
                            "notifyMode" to "all",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (channelSnap.exists()) {
                        tx.update(
                            channelRef,
                            mapOf(
                                "subscriberCount" to FieldValue.increment(1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    true
                }
            }.await()
            Result.success(nowSubscribed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun updateSubscriptionBell(channelId: String, mode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val docId = "${uid}_${channelId}"
        val path = "subscriptions/$docId"
        return try {
            requireUserId()
            db.collection("subscriptions").document(docId).update(
                mapOf(
                    "notifyMode" to mode,
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

    /**
     * Transaction-safe like/dislike reaction that prevents duplicate likes
     * and atomically updates likesCount / dislikesCount on videos/{videoId}.
     */
    suspend fun setVideoReaction(
        videoId: String,
        newReaction: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val likeDocId = "${uid}_${videoId}"
        val path = "likes/$likeDocId"
        return try {
            val activeUid = requireUserId()
            val likeRef = db.collection("likes").document(likeDocId)
            val videoRef = db.collection("videos").document(videoId)

            db.runTransaction { tx ->
                val likeSnap = tx.get(likeRef)
                val videoSnap = tx.get(videoRef)
                val existingReaction = likeSnap.getString("reaction")
                val curLikes = videoSnap.getLong("likesCount") ?: 0L
                val curDislikes = videoSnap.getLong("dislikesCount") ?: 0L

                if (existingReaction == newReaction) {
                    tx.delete(likeRef)
                    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                    if (newReaction == "like") {
                        updates["likesCount"] = (curLikes - 1L).coerceAtLeast(0L)
                    } else {
                        updates["dislikesCount"] = (curDislikes - 1L).coerceAtLeast(0L)
                    }
                    if (videoSnap.exists()) tx.update(videoRef, updates)
                } else {
                    if (likeSnap.exists()) {
                        tx.update(
                            likeRef,
                            mapOf(
                                "reaction" to newReaction,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        tx.set(
                            likeRef,
                            mapOf(
                                "userId" to activeUid,
                                "targetId" to videoId,
                                "targetType" to "video",
                                "reaction" to newReaction,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                    if (newReaction == "like") {
                        updates["likesCount"] = curLikes + 1L
                        if (existingReaction == "dislike") {
                            updates["dislikesCount"] = (curDislikes - 1L).coerceAtLeast(0L)
                        }
                    } else {
                        updates["dislikesCount"] = curDislikes + 1L
                        if (existingReaction == "like") {
                            updates["likesCount"] = (curLikes - 1L).coerceAtLeast(0L)
                        }
                    }
                    if (videoSnap.exists()) tx.update(videoRef, updates)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun saveToPlaylistOrWatchLater(
        playlistId: String?,
        videoId: String
    ): Result<Unit> {
        val path = "playlists/${playlistId ?: "watch_later"}"
        return try {
            val uid = requireUserId()
            val targetId = playlistId ?: "${uid}_watch_later"
            val docRef = db.collection("playlists").document(targetId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "ownerId" to uid,
                        "title" to "बाद में देखें",
                        "description" to "सहेजे गए वीडियो",
                        "visibility" to "private",
                        "videoIds" to listOf(videoId),
                        "systemType" to "watch_later",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existing = snap.toObject(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val currentIds = existing?.videoIds.orEmpty()
                val updated = if (videoId in currentIds) currentIds else (currentIds + videoId).take(100)
                docRef.update(
                    mapOf(
                        "videoIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun createPlaylist(title: String, visibility: String): Result<String> {
        val path = "playlists"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(100)
            require(cleanTitle.isNotEmpty()) { "प्लेलिस्ट का नाम आवश्यक है।" }
            val docRef = db.collection("playlists").document()
            docRef.set(
                mapOf(
                    "ownerId" to uid,
                    "title" to cleanTitle,
                    "description" to "",
                    "visibility" to visibility,
                    "videoIds" to emptyList<String>(),
                    "systemType" to "custom",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        title: String,
        visibility: String,
        videoIds: List<String>
    ): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            requireUserId()
            db.collection("playlists").document(playlistId).update(
                mapOf(
                    "title" to title.trim().take(100),
                    "visibility" to visibility,
                    "videoIds" to videoIds.take(100),
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

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            requireUserId()
            db.collection("playlists").document(playlistId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun createCommunityPost(
        postId: String,
        authorName: String,
        authorAvatar: String,
        text: String,
        imageUrls: List<String>,
        pollOptions: List<String>
    ): Result<Unit> {
        val path = "posts/$postId"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotEmpty()) { "पोस्ट का टेक्स्ट आवश्यक है।" }
            val cleanOptions = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
            val initialVotes = List(cleanOptions.size) { 0L }
            val docRef = db.collection("posts").document(postId)
            docRef.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "क्रिएटर" }.take(80),
                    "authorAvatar" to authorAvatar.take(1500),
                    "text" to cleanText,
                    "imageUrls" to imageUrls.take(10),
                    "pollOptions" to cleanOptions,
                    "pollVotes" to initialVotes,
                    "likesCount" to 0L,
                    "commentsCount" to 0L,
                    "visibility" to "public",
                    "createdAt" to FieldValue.serverTimestamp(),
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

    suspend fun voteOnPostPoll(post: CommunityPost, optionIndex: Int): Result<Unit> {
        val path = "posts/${post.id}"
        return try {
            requireUserId()
            if (optionIndex !in post.pollOptions.indices) return Result.failure(IllegalArgumentException("अमान्य विकल्प"))
            val updatedVotes = post.pollVotes.toMutableList()
            while (updatedVotes.size < post.pollOptions.size) updatedVotes.add(0L)
            updatedVotes[optionIndex] = updatedVotes[optionIndex] + 1L
            db.collection("posts").document(post.id).update(
                mapOf(
                    "pollVotes" to updatedVotes,
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

    suspend fun likeCommunityPost(postId: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireUserId()
            db.collection("posts").document(postId).update(
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
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

    suspend fun addPostComment(postId: String, authorName: String, text: String): Result<Unit> {
        val path = "posts/$postId/postComments"
        return try {
            val uid = requireUserId()
            val clean = text.trim().take(600)
            require(clean.isNotEmpty()) { "टिप्पणी खाली नहीं हो सकती।" }
            val ref = db.collection("posts").document(postId).collection("postComments").document()
            ref.set(
                mapOf(
                    "postId" to postId,
                    "postVisibility" to "public",
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "उपयोगकर्ता" }.take(80),
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("posts").document(postId).update(
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

    suspend fun sendLiveChat(
        video: VideoItem,
        senderName: String,
        senderAvatar: String,
        message: String,
        superChatAmount: Long = 0L
    ): Result<Unit> {
        val path = "liveChats"
        return try {
            val uid = requireUserId()
            val clean = message.trim().take(400)
            require(clean.isNotEmpty()) { "संदेश खाली नहीं हो सकता।" }
            val ref = db.collection("liveChats").document()
            ref.set(
                mapOf(
                    "videoId" to video.resolvedId,
                    "videoOwnerId" to video.ownerUid,
                    "roomPublic" to true,
                    "senderId" to uid,
                    "senderName" to senderName.ifBlank { "दर्शक" }.take(80),
                    "senderAvatar" to senderAvatar.take(1500),
                    "message" to clean,
                    "superChatAmount" to superChatAmount.coerceAtLeast(0L),
                    "highlighted" to (superChatAmount > 0L),
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

    suspend fun deleteLiveChatMessage(chatId: String): Result<Unit> {
        val path = "liveChats/$chatId"
        return try {
            requireUserId()
            db.collection("liveChats").document(chatId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun recordWatchHistory(video: VideoItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val vid = video.resolvedId
        val docId = "${uid}_${vid}"
        val path = "history/$docId"
        return try {
            val activeUid = requireUserId()
            db.collection("history").document(docId).set(
                mapOf(
                    "userId" to activeUid,
                    "videoId" to vid,
                    "videoTitle" to video.title.take(150),
                    "channelName" to video.channelName.take(80),
                    "thumbnailUrl" to video.thumbnailUrl.take(2000),
                    "durationLabel" to video.durationLabel.take(20),
                    "watchedAt" to FieldValue.serverTimestamp()
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

    suspend fun clearWatchHistory(entries: List<WatchHistoryEntry>): Result<Unit> {
        val path = "history"
        return try {
            requireUserId()
            val batch = db.batch()
            entries.forEach { entry ->
                if (entry.id.isNotBlank()) {
                    batch.delete(db.collection("history").document(entry.id))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun createNotification(
        recipientId: String,
        actorName: String,
        type: String,
        title: String,
        body: String,
        targetId: String
    ): Result<Unit> {
        val path = "notifications"
        return try {
            val uid = requireUserId()
            db.collection("notifications").document().set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to uid,
                    "actorName" to actorName.ifBlank { "GLASYER" }.take(80),
                    "type" to type,
                    "title" to title.take(120),
                    "body" to body.take(300),
                    "targetId" to targetId.take(128),
                    "read" to false,
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

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        val path = "notifications/$notifId"
        return try {
            requireUserId()
            db.collection("notifications").document(notifId).update("read", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(IllegalStateException(mapFirestoreErrorHindi(e), e))
        }
    }

    suspend fun submitReport(targetId: String, targetType: String, reason: String): Result<Unit> {
        val path = "reports"
        return try {
            val uid = requireUserId()
            db.collection("reports").document().set(
                mapOf(
                    "reporterId" to uid,
                    "targetId" to targetId,
                    "targetType" to targetType,
                    "reason" to reason.trim().ifBlank { "अनुचित सामग्री" }.take(300),
                    "status" to "pending",
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

    /**
     * Safely removes ONLY identifiable demo documents created by the previous build
     * that belong to the signed-in user, without touching real uploads or accounts.
     */
    suspend fun cleanupLegacyDemoRecords(uid: String) {
        try {
            // Legacy videos may use `ownerId` or `ownerUid`
            val legacyOwnerDocs = db.collection("videos").whereEqualTo("ownerId", uid).get().await().documents
            val newOwnerDocs = db.collection("videos").whereEqualTo("ownerUid", uid).get().await().documents
            val demoVideos = (legacyOwnerDocs + newOwnerDocs).distinctBy { it.id }.filter { isIdentifiableDemoVideo(it) }
            for (vDoc in demoVideos) {
                val comments = vDoc.reference.collection("comments").get().await().documents
                for (cDoc in comments) {
                    runCatching { cDoc.reference.delete().await() }
                }
                runCatching { vDoc.reference.delete().await() }
            }

            // Legacy demo community post
            val myPosts = db.collection("posts").whereEqualTo("authorId", uid).get().await().documents
            for (pDoc in myPosts) {
                if (pDoc.getString("text") == LEGACY_DEMO_POST_TEXT) {
                    runCatching { pDoc.reference.delete().await() }
                }
            }

            // Legacy demo welcome notification
            val myNotifs = db.collection("notifications").whereEqualTo("recipientId", uid).get().await().documents
            for (nDoc in myNotifs) {
                if (nDoc.getString("title") == LEGACY_DEMO_NOTIF_TITLE) {
                    runCatching { nDoc.reference.delete().await() }
                }
            }

            // Legacy demo live chat
            val myChats = db.collection("liveChats").whereEqualTo("senderId", uid).get().await().documents
            for (cDoc in myChats) {
                if (cDoc.getString("message") == LEGACY_DEMO_CHAT_MSG) {
                    runCatching { cDoc.reference.delete().await() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort cleanup; never crash startup
        }
    }

    private fun logAndRethrow(
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
