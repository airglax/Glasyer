package com.example.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.CommunityPost
import com.example.model.PostComment
import com.example.model.Subscription
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.VideoCard
import com.example.ui.components.formatTimeAgo
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    allVideos: List<VideoItem>,
    channelMap: Map<String, com.example.model.UserChannel> = emptyMap(),
    posts: List<CommunityPost>,
    votedIndices: Map<String, Int>,
    observePostComments: (String) -> Flow<List<PostComment>>,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onVotePoll: (CommunityPost, Int) -> Unit,
    onLikePost: (String) -> Unit,
    onAddPostComment: (String, String) -> Unit,
    onBrowseHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf("videos") } // videos | posts
    var selectedChannelFilter by remember { mutableStateOf<String?>(null) }
    var activePostForComments by remember { mutableStateOf<CommunityPost?>(null) }

    val subChannelIds = remember(subscriptions) { subscriptions.map { it.channelId }.toSet() }
    val subVideos = remember(allVideos, subChannelIds, selectedChannelFilter) {
        allVideos.filter { v ->
            val inSub = v.ownerUid in subChannelIds
            val matchesChannel = selectedChannelFilter == null || v.ownerUid == selectedChannelFilter
            inSub && matchesChannel
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (subscriptions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(subscriptions, key = { it.id }) { sub ->
                    val active = selectedChannelFilter == sub.channelId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(68.dp)
                            .clickable {
                                selectedChannelFilter = if (active) null else sub.channelId
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .padding(3.dp)
                        ) {
                            ChannelAvatar(name = sub.channelName, photoUrl = sub.channelAvatar, size = 50.dp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sub.channelName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = selectedSection == "videos",
                onClick = { selectedSection = "videos" },
                label = { Text("नवीनतम वीडियो") }
            )
            FilterChip(
                selected = selectedSection == "posts",
                onClick = { selectedSection = "posts" },
                label = { Text("पोस्ट (${posts.size})") }
            )
        }

        if (selectedSection == "videos") {
            if (subscriptions.isEmpty()) {
                EmptyStateCard(
                    title = "अभी कोई सब्सक्रिप्शन नहीं है",
                    subtitle = "अपने पसंदीदा क्रिएटर्स को सब्सक्राइब करें ताकि उनके नए वीडियो और पोस्ट यहाँ दिखें।",
                    actionLabel = "होम पर वीडियो देखें",
                    onAction = onBrowseHome
                )
            } else if (subVideos.isEmpty()) {
                EmptyStateCard(
                    title = "चयनित चैनल का कोई नया वीडियो नहीं",
                    subtitle = "सभी सब्सक्राइब किए गए चैनलों को देखने के लिए फ़िल्टर हटाएं।",
                    actionLabel = "फ़िल्टर साफ़ करें",
                    onAction = { selectedChannelFilter = null }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(subVideos, key = { it.resolvedId }) { video ->
                        VideoCard(
                            video = video,
                            channel = channelMap[video.ownerUid],
                            onClick = { onOpenVideo(video) },
                            onChannelClick = { onOpenChannel(video.ownerUid) },
                            onMenuClick = {}
                        )
                    }
                }
            }
        } else {
            if (posts.isEmpty()) {
                EmptyStateCard(
                    title = "अभी कोई कम्युनिटी पोस्ट नहीं है",
                    subtitle = "+ बटन दबाकर अपनी पहली कम्युनिटी पोस्ट या पोल बनाएं।"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        CommunityPostCard(
                            post = post,
                            votedOptionIndex = votedIndices[post.id],
                            onChannelClick = { onOpenChannel(post.authorId) },
                            onVote = { idx -> onVotePoll(post, idx) },
                            onLike = { onLikePost(post.id) },
                            onComments = { activePostForComments = post }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (activePostForComments != null) {
        val targetPost = activePostForComments!!
        val postComments by remember(targetPost.id) { observePostComments(targetPost.id) }.collectAsState(initial = emptyList())
        var input by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { activePostForComments = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("पोस्ट पर टिप्पणियाँ (${postComments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(600) },
                        placeholder = { Text("टिप्पणी लिखें...") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (input.isNotBlank()) {
                            onAddPostComment(targetPost.id, input)
                            input = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "भेजें", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(postComments, key = { it.id }) { c ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(c.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    votedOptionIndex: Int?,
    onChannelClick: () -> Unit,
    onVote: (Int) -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onChannelClick)
            ) {
                ChannelAvatar(name = post.authorName, photoUrl = post.authorAvatar, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(formatTimeAgo(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(post.text, style = MaterialTheme.typography.bodyLarge)

            if (post.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.imageUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "कम्युनिटी फ़ोटो",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 240.dp, height = 160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            if (post.pollOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val totalVotes = post.pollVotes.sum().coerceAtLeast(1L)
                post.pollOptions.forEachIndexed { idx, opt ->
                    val votes = post.pollVotes.getOrNull(idx) ?: 0L
                    val pct = (votes.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
                    val isSelected = votedOptionIndex == idx
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onVote(idx) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(opt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("${(pct * 100).toInt()}% ($votes)", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onLike).padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = "लाइक", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.likesCount}", style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onComments).padding(vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "टिप्पणी", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.commentsCount}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
