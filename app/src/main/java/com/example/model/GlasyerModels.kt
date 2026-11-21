package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UploadSubStage {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    FAILED,
    CANCELLED
}

data class UploadProgressState(
    val stageLabel: String,
    val fraction: Float,
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val videoStage: UploadSubStage = UploadSubStage.IN_PROGRESS,
    val thumbnailStage: UploadSubStage = UploadSubStage.IDLE,
    val publishStage: UploadSubStage = UploadSubStage.IDLE,
    val isCancelled: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class UserChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bannerUrl: String = "",
    val bio: String = "",
    val subscriberCount: Long = 0L,
    val verified: Boolean = false,
    val publicChannel: Boolean = true,
    val onboardingDone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = "system",
    val language: String = "hi",
    val autoplay: Boolean = true,
    val playbackQuality: String = "auto",
    val restrictedMode: Boolean = false,
    val dataSaver: Boolean = false,
    val historyPaused: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUserId: String = "",
    val blockedName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoItem(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val channelAvatar: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "सब",
    val videoType: String = "video", // video | short | live
    val visibility: String = "public", // public | unlisted | private
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val durationLabel: String = "00:00",
    val duration: Long = 0L,
    val viewsCount: Long = 0L,
    val likesCount: Long = 0L,
    val dislikesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val viewerCount: Long = 0L,
    val commentsEnabled: Boolean = true,
    val ageRestricted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedId: String get() = videoId.ifBlank { id }
}

private const val LEGACY_DEMO_VIDEO_PREFIX = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"

fun isIdentifiableDemoVideo(doc: DocumentSnapshot): Boolean {
    if (doc.getBoolean("seedItem") == true) return true
    val videoUrl = doc.getString("videoUrl").orEmpty()
    val thumb = doc.getString("thumbnailUrl") ?: doc.getString("thumbnailKey").orEmpty()
    return videoUrl.startsWith(LEGACY_DEMO_VIDEO_PREFIX) || thumb.startsWith("preset:")
}

fun DocumentSnapshot.toVideoItemSafe(): VideoItem? {
    if (!exists()) return null
    if (isIdentifiableDemoVideo(this)) return null
    val data = data ?: return null
    val resolvedVideoId = (data["videoId"] as? String)?.takeIf { it.isNotBlank() } ?: id
    val resolvedOwnerUid = (data["ownerUid"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["ownerId"] as? String).orEmpty()
    val resolvedThumb = (data["thumbnailUrl"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["thumbnailKey"] as? String).orEmpty()
    val resolvedType = (data["videoType"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["type"] as? String).orEmpty().ifBlank { "video" }
    val resolvedDuration = (data["duration"] as? Number)?.toLong()
        ?: (data["durationSec"] as? Number)?.toLong() ?: 0L
    val resolvedViews = (data["viewsCount"] as? Number)?.toLong()
        ?: (data["views"] as? Number)?.toLong() ?: 0L
    val rawTags = (data["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    return VideoItem(
        id = id,
        videoId = resolvedVideoId,
        ownerUid = resolvedOwnerUid,
        channelName = (data["channelName"] as? String).orEmpty(),
        channelAvatar = (data["channelAvatar"] as? String).orEmpty(),
        title = (data["title"] as? String).orEmpty(),
        description = (data["description"] as? String).orEmpty(),
        tags = rawTags,
        category = (data["category"] as? String).orEmpty().ifBlank { "सब" },
        videoType = resolvedType,
        visibility = (data["visibility"] as? String).orEmpty().ifBlank { "public" },
        videoUrl = (data["videoUrl"] as? String).orEmpty(),
        thumbnailUrl = resolvedThumb,
        durationLabel = (data["durationLabel"] as? String).orEmpty().ifBlank { "00:00" },
        duration = resolvedDuration,
        viewsCount = resolvedViews,
        likesCount = (data["likesCount"] as? Number)?.toLong() ?: 0L,
        dislikesCount = (data["dislikesCount"] as? Number)?.toLong() ?: 0L,
        commentsCount = (data["commentsCount"] as? Number)?.toLong() ?: 0L,
        viewerCount = (data["viewerCount"] as? Number)?.toLong() ?: 0L,
        commentsEnabled = (data["commentsEnabled"] as? Boolean) ?: true,
        ageRestricted = (data["ageRestricted"] as? Boolean) ?: false,
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    )
}

@Keep
@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val videoOwnerId: String = "",
    val videoVisibility: String = "public",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    val parentId: String = "",
    val likesCount: Long = 0L,
    val pinned: Boolean = false,
    val creatorHearted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subscription(
    @DocumentId val id: String = "",
    val subscriberId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatar: String = "",
    val notifyMode: String = "all", // all | personalized | off
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LikeRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetId: String = "",
    val targetType: String = "video", // video | post | comment
    val reaction: String = "like", // like | dislike
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Playlist(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val description: String = "",
    val visibility: String = "private", // public | private
    val videoIds: List<String> = emptyList(),
    val systemType: String = "custom", // custom | watch_later
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    val imageUrls: List<String> = emptyList(),
    val pollOptions: List<String> = emptyList(),
    val pollVotes: List<Long> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val postVisibility: String = "public",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveChatMessage(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val videoOwnerId: String = "",
    val roomPublic: Boolean = true,
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val message: String = "",
    val superChatAmount: Long = 0L,
    val highlighted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val type: String = "upload", // upload | reply | like | live
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WatchHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val durationLabel: String = "",
    @ServerTimestamp val watchedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetId: String = "",
    val targetType: String = "video", // video | comment | user
    val reason: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object GlasyerCategories {
    val all = listOf("सब", "गेमिंग", "म्यूज़िक", "लाइव", "खाना", "क्रिकेट", "सीखें", "यात्रा")
}

enum class MainTab {
    HOME, SHORTS, SUBSCRIPTIONS, YOU
}
