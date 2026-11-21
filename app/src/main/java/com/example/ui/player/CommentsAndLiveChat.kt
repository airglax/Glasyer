package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.LiveChatMessage
import com.example.model.VideoComment
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.GlasyerLiveCoral
import com.example.ui.theme.GlasyerSuperChatGold

private val quickEmojis = listOf("❤️", "🔥", "👏", "😂", "🙌", "🤩", "💯", "🏏")

@Composable
fun CommentsSection(
    video: VideoItem,
    comments: List<VideoComment>,
    currentUserId: String,
    onAddComment: (text: String, parentId: String) -> Unit,
    onLikeComment: (VideoComment) -> Unit,
    onDeleteComment: (VideoComment) -> Unit,
    onTogglePinOrHeart: (VideoComment, togglePin: Boolean, toggleHeart: Boolean) -> Unit,
    onReportComment: (VideoComment) -> Unit
) {
    var sortMode by remember { mutableStateOf("top") } // top | newest
    var commentText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<VideoComment?>(null) }

    val sortedTopLevel = remember(comments, sortMode) {
        val top = comments.filter { it.parentId.isBlank() }
        val sorted = if (sortMode == "top") {
            top.sortedWith(compareByDescending<VideoComment> { it.pinned }.thenByDescending { it.likesCount })
        } else {
            top.sortedWith(compareByDescending<VideoComment> { it.pinned }.thenByDescending { it.createdAt?.seconds ?: 0L })
        }
        sorted
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "टिप्पणियाँ (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sortMode == "top",
                    onClick = { sortMode = "top" },
                    label = { Text("टॉप") }
                )
                FilterChip(
                    selected = sortMode == "newest",
                    onClick = { sortMode = "newest" },
                    label = { Text("नवीनतम") }
                )
            }
        }

        if (!video.commentsEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    text = "क्रिएटर ने इस वीडियो के लिए टिप्पणियाँ बंद कर दी हैं।",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
            return
        }

        // Quick Emoji / Sticker bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickEmojis.forEach { emoji ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { commentText += emoji }
                ) {
                    Text(emoji, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }

        if (replyTarget != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${replyTarget!!.authorName} को जवाब दे रहे हैं",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { replyTarget = null }) { Text("रद्द करें") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it.take(1000) },
                placeholder = { Text("अपनी टिप्पणी लिखें...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("comment_input"),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (commentText.isNotBlank()) {
                        onAddComment(commentText, replyTarget?.id.orEmpty())
                        commentText = ""
                        replyTarget = null
                    }
                },
                modifier = Modifier.size(48.dp).testTag("send_comment_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "भेजें",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sortedTopLevel.isEmpty()) {
            Text(
                text = "अभी तक कोई टिप्पणी नहीं है। पहली टिप्पणी लिखें!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            sortedTopLevel.forEach { parent ->
                val replies = comments.filter { it.parentId == parent.id }
                CommentRow(
                    comment = parent,
                    isVideoOwner = video.ownerUid == currentUserId,
                    isAuthor = parent.authorId == currentUserId,
                    onLike = { onLikeComment(parent) },
                    onReply = { replyTarget = parent },
                    onDelete = { onDeleteComment(parent) },
                    onTogglePin = { onTogglePinOrHeart(parent, true, false) },
                    onToggleHeart = { onTogglePinOrHeart(parent, false, true) },
                    onReport = { onReportComment(parent) }
                )
                replies.forEach { reply ->
                    CommentRow(
                        comment = reply,
                        isVideoOwner = video.ownerUid == currentUserId,
                        isAuthor = reply.authorId == currentUserId,
                        isReply = true,
                        onLike = { onLikeComment(reply) },
                        onReply = { replyTarget = parent },
                        onDelete = { onDeleteComment(reply) },
                        onTogglePin = {},
                        onToggleHeart = { onTogglePinOrHeart(reply, false, true) },
                        onReport = { onReportComment(reply) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: VideoComment,
    isVideoOwner: Boolean,
    isAuthor: Boolean,
    isReply: Boolean = false,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHeart: () -> Unit,
    onReport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 38.dp else 0.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        ChannelAvatar(name = comment.authorName, photoUrl = comment.authorAvatar, size = if (isReply) 26.dp else 32.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (comment.pinned) {
                Text(
                    text = "📌 क्रिएटर द्वारा पिन किया गया",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${comment.authorName} • ${formatTimeAgo(comment.createdAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = "लाइक", modifier = Modifier.size(16.dp))
                }
                Text(comment.likesCount.toString(), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "जवाब", modifier = Modifier.size(16.dp))
                }
                if (comment.creatorHearted || isVideoOwner) {
                    IconButton(
                        onClick = { if (isVideoOwner) onToggleHeart() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (comment.creatorHearted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "क्रिएटर हार्ट",
                            tint = if (comment.creatorHearted) GlasyerLiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (isVideoOwner && !isReply) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (comment.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "पिन",
                            tint = if (comment.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (isAuthor || isVideoOwner) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "हटाएं", modifier = Modifier.size(16.dp))
                    }
                } else {
                    TextButton(onClick = onReport) {
                        Text("रिपोर्ट", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveChatPanel(
    video: VideoItem,
    messages: List<LiveChatMessage>,
    currentUserId: String,
    onSendChat: (message: String, superChatAmount: Long) -> Unit,
    onDeleteChat: (chatId: String) -> Unit,
    onBlockUser: (userId: String, userName: String) -> Unit
) {
    var chatInput by remember { mutableStateOf("") }
    var superChatAmount by remember { mutableLongStateOf(0L) }
    val isCreator = video.ownerUid == currentUserId

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = GlasyerLiveCoral, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "लाइव चैट",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        "${video.viewerCount} दर्शक ऑनलाइन",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (messages.isEmpty()) {
                Text(
                    "चैट में पहला संदेश भेजें!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    messages.takeLast(25).forEach { msg ->
                        val bg = if (msg.highlighted) {
                            GlasyerSuperChatGold.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(name = msg.senderName, photoUrl = msg.senderAvatar, size = 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        msg.senderName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.highlighted) GlasyerSuperChatGold else MaterialTheme.colorScheme.primary
                                    )
                                    if (msg.superChatAmount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = GlasyerSuperChatGold, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                "₹${msg.superChatAmount} सुपर चैट",
                                                color = Color.Black,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(msg.message, style = MaterialTheme.typography.bodySmall)
                            }
                            if (isCreator || msg.senderId == currentUserId) {
                                IconButton(
                                    onClick = { onDeleteChat(msg.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "संदेश हटाएं", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (isCreator && msg.senderId != currentUserId) {
                                IconButton(
                                    onClick = { onBlockUser(msg.senderId, msg.senderName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = "ब्लॉक करें", tint = GlasyerLiveCoral, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0L to "सामान्य", 40L to "₹40 सुपर", 100L to "₹100 सुपर", 500L to "₹500 सुपर").forEach { (amt, label) ->
                    FilterChip(
                        selected = superChatAmount == amt,
                        onClick = { superChatAmount = amt },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it.take(400) },
                    placeholder = { Text("लाइव चैट में लिखें...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            onSendChat(chatInput, superChatAmount)
                            chatInput = ""
                            superChatAmount = 0L
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "चैट भेजें", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
