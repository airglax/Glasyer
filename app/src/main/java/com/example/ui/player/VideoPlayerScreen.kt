package com.example.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.LiveChatMessage
import com.example.model.Playlist
import com.example.model.Subscription
import com.example.model.UserChannel
import com.example.model.VideoComment
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.GlasyerThumbnail
import com.example.ui.components.VideoCard
import com.example.ui.components.formatCount
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.GlasyerLiveCoral
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    uploaderChannel: UserChannel? = null,
    channelMap: Map<String, UserChannel> = emptyMap(),
    suggestedVideos: List<VideoItem>,
    comments: List<VideoComment>,
    liveChats: List<LiveChatMessage>,
    playlists: List<Playlist>,
    currentUserId: String,
    currentReaction: String?,
    subscription: Subscription?,
    isDownloaded: Boolean,
    autoplayNext: Boolean,
    onRegisterQualifiedView: (String) -> Unit = {},
    onMinimize: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onReact: (String) -> Unit,
    onShare: (VideoItem) -> Unit,
    onSaveWatchLater: (String) -> Unit,
    onSaveToPlaylist: (playlistId: String, videoId: String) -> Unit,
    onToggleDownload: (String) -> Unit,
    onToggleSubscribe: () -> Unit,
    onUpdateBell: (String) -> Unit,
    onAddComment: (text: String, parentId: String) -> Unit,
    onLikeComment: (VideoComment) -> Unit,
    onDeleteComment: (VideoComment) -> Unit,
    onTogglePinOrHeart: (VideoComment, Boolean, Boolean) -> Unit,
    onSendLiveChat: (String, Long) -> Unit,
    onDeleteLiveChat: (String) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onReport: (targetId: String, targetType: String, reason: String) -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showBellMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }

    val resolvedChannelName = uploaderChannel?.displayName?.ifBlank { null }
        ?: video.channelName.ifBlank { "GLASYER क्रिएटर" }
    val resolvedChannelAvatar = uploaderChannel?.photoUrl?.ifBlank { null } ?: video.channelAvatar

    val exoPlayer = remember(video.resolvedId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember(video.resolvedId) { mutableStateOf(false) }
    var playbackState by remember(video.resolvedId) { mutableIntStateOf(Player.STATE_IDLE) }
    var currentPosMs by remember(video.resolvedId) { mutableLongStateOf(0L) }
    var durationMs by remember(video.resolvedId) { mutableLongStateOf(1L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_ENDED && autoplayNext && suggestedVideos.isNotEmpty()) {
                    val next = suggestedVideos.firstOrNull { it.resolvedId != video.resolvedId }
                    if (next != null) onOpenVideo(next)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(video.resolvedId, isPlaying, playbackState) {
        if (isPlaying && playbackState == Player.STATE_READY) {
            delay(2000L)
            if (exoPlayer.isPlaying) {
                onRegisterQualifiedView(video.resolvedId)
            }
        }
    }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            delay(400)
        }
    }

    BackHandler {
        if (isFullscreen) isFullscreen = false else onMinimize()
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                PlayerSurfaceBox(
                    exoPlayer = exoPlayer,
                    isPlaying = isPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    isFullscreen = true,
                    showSpeedMenu = showSpeedMenu,
                    onTogglePlay = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    onSeek = { exoPlayer.seekTo(it) },
                    onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                    onSelectSpeed = { playbackSpeed = it; showSpeedMenu = false },
                    onToggleFullscreen = { isFullscreen = false },
                    onMinimize = { isFullscreen = false; onMinimize() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        PlayerSurfaceBox(
            exoPlayer = exoPlayer,
            isPlaying = isPlaying,
            currentPosMs = currentPosMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            isFullscreen = false,
            showSpeedMenu = showSpeedMenu,
            onTogglePlay = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
            onSeek = { exoPlayer.seekTo(it) },
            onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
            onSelectSpeed = { playbackSpeed = it; showSpeedMenu = false },
            onToggleFullscreen = { isFullscreen = true },
            onMinimize = onMinimize,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video.videoType == "live") {
                            Surface(color = GlasyerLiveCoral, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "लाइव • ${formatCount(video.viewerCount)} देख रहे हैं",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "${formatCount(video.viewsCount)} बार देखा गया • ${formatTimeAgo(video.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                            Text(if (descriptionExpanded) "कम देखें" else "...और देखें")
                        }
                    }

                    if (descriptionExpanded) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = video.description.ifBlank { "कोई विवरण उपलब्ध नहीं है।" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (video.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = video.tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).clickable { onOpenChannel(video.ownerUid) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(name = resolvedChannelName, photoUrl = resolvedChannelAvatar, size = 40.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = resolvedChannelName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uploaderChannel?.verified == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "सत्यापित",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                val subText = if (uploaderChannel != null) {
                                    "${formatCount(uploaderChannel.subscriberCount)} सब्सक्राइबर • ${video.category}"
                                } else {
                                    video.category
                                }
                                Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        val isSub = subscription != null
                        Button(
                            onClick = onToggleSubscribe,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSub) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if (isSub) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("subscribe_button")
                        ) {
                            Text(if (isSub) "सब्सक्राइब्ड" else "सब्सक्राइब करें")
                        }

                        if (isSub) {
                            Box {
                                IconButton(onClick = { showBellMenu = true }) {
                                    Icon(
                                        imageVector = when (subscription.notifyMode) {
                                            "off" -> Icons.Filled.NotificationsOff
                                            "all" -> Icons.Filled.NotificationsActive
                                            else -> Icons.Filled.Notifications
                                        },
                                        contentDescription = "सूचना घंटी"
                                    )
                                }
                                DropdownMenu(expanded = showBellMenu, onDismissRequest = { showBellMenu = false }) {
                                    DropdownMenuItem(text = { Text("सभी सूचनाएं") }, onClick = { onUpdateBell("all"); showBellMenu = false })
                                    DropdownMenuItem(text = { Text("पर्सनलाइज़्ड") }, onClick = { onUpdateBell("personalized"); showBellMenu = false })
                                    DropdownMenuItem(text = { Text("कोई नहीं (बंद)") }, onClick = { onUpdateBell("off"); showBellMenu = false })
                                }
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        item {
                            ActionPill(
                                icon = if (currentReaction == "like") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                label = formatCount(video.likesCount),
                                active = currentReaction == "like",
                                onClick = { onReact("like") }
                            )
                        }
                        item {
                            ActionPill(
                                icon = if (currentReaction == "dislike") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                label = "नापसंद",
                                active = currentReaction == "dislike",
                                onClick = { onReact("dislike") }
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.Share,
                                label = "शेयर",
                                onClick = { onShare(video) }
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.BookmarkAdd,
                                label = "सहेजें",
                                onClick = { showPlaylistSheet = true }
                            )
                        }
                        item {
                            ActionPill(
                                icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                label = if (isDownloaded) "डाउनलोडेड" else "डाउनलोड",
                                active = isDownloaded,
                                onClick = { onToggleDownload(video.resolvedId) }
                            )
                        }
                        item {
                            ActionPill(
                                icon = Icons.Filled.Flag,
                                label = "रिपोर्ट",
                                onClick = { showReportDialog = video.resolvedId to "video" }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            if (video.videoType == "live") {
                item {
                    LiveChatPanel(
                        video = video,
                        messages = liveChats,
                        currentUserId = currentUserId,
                        onSendChat = onSendLiveChat,
                        onDeleteChat = onDeleteLiveChat,
                        onBlockUser = onBlockUser
                    )
                    HorizontalDivider()
                }
            }

            item {
                CommentsSection(
                    video = video,
                    comments = comments,
                    currentUserId = currentUserId,
                    onAddComment = onAddComment,
                    onLikeComment = onLikeComment,
                    onDeleteComment = onDeleteComment,
                    onTogglePinOrHeart = onTogglePinOrHeart,
                    onReportComment = { c -> showReportDialog = c.id to "comment" }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = "आगे देखें",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }

            items(suggestedVideos.filter { it.resolvedId != video.resolvedId }.take(8), key = { it.resolvedId }) { nextVideo ->
                VideoCard(
                    video = nextVideo,
                    channel = channelMap[nextVideo.ownerUid],
                    onClick = { onOpenVideo(nextVideo) },
                    onChannelClick = { onOpenChannel(nextVideo.ownerUid) },
                    onMenuClick = {}
                )
            }
        }
    }

    if (showPlaylistSheet) {
        SaveToPlaylistSheet(
            playlists = playlists,
            onWatchLater = {
                onSaveWatchLater(video.resolvedId)
                showPlaylistSheet = false
            },
            onSelectPlaylist = { plId ->
                onSaveToPlaylist(plId, video.resolvedId)
                showPlaylistSheet = false
            },
            onDismiss = { showPlaylistSheet = false }
        )
    }

    if (showReportDialog != null) {
        var reason by remember { mutableStateOf("स्पैम या भ्रामक सामग्री") }
        AlertDialog(
            onDismissRequest = { showReportDialog = null },
            title = { Text("रिपोर्ट दर्ज करें") },
            text = {
                Column {
                    Text("कृपया रिपोर्ट का कारण लिखें:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = reason, onValueChange = { reason = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val (id, type) = showReportDialog!!
                    onReport(id, type, reason)
                    showReportDialog = null
                }) { Text("भेजें") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = null }) { Text("रद्द करें") }
            }
        )
    }
}

@Composable
private fun PlayerSurfaceBox(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    isFullscreen: Boolean,
    showSpeedMenu: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMinimize) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "मिनी-प्लेयर", tint = Color.White)
            }
            Box {
                IconButton(onClick = onToggleSpeedMenu) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, contentDescription = "गति", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("${playbackSpeed}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = onToggleSpeedMenu) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        DropdownMenuItem(
                            text = { Text("${spd}x") },
                            onClick = { onSelectSpeed(spd) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "रोकें" else "चलाएं",
                    tint = Color.White
                )
            }
            val sliderVal = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            Slider(
                value = sliderVal,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatDurationMs(currentPosMs)}/${formatDurationMs(durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "फ़ुलस्क्रीन",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(
    playlists: List<Playlist>,
    onWatchLater: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "प्लेलिस्ट में सहेजें",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onWatchLater)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("बाद में देखें (Watch Later)", style = MaterialTheme.typography.bodyLarge)
            }
            playlists.filter { it.systemType != "watch_later" }.forEach { pl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(pl.id) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pl.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${pl.videoIds.size} वीडियो", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    video: VideoItem,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlasyerThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(82.dp)
                    .height(46.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onExpand) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "खोलें")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "बंद करें")
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
