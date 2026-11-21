package com.example.ui.shorts

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.LikeRecord
import com.example.model.Subscription
import com.example.model.UserChannel
import com.example.model.VideoComment
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatCount
import com.example.ui.player.CommentsSection
import com.example.ui.theme.GlasyerLiveCoral
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    shorts: List<VideoItem>,
    channelMap: Map<String, UserChannel> = emptyMap(),
    likes: List<LikeRecord>,
    subscriptions: List<Subscription>,
    currentUserId: String,
    observeComments: (VideoItem) -> Flow<List<VideoComment>>,
    onRegisterQualifiedView: (String) -> Unit = {},
    onLikeShort: (String) -> Unit,
    onShareShort: (VideoItem) -> Unit,
    onToggleSubscribe: (VideoItem) -> Unit,
    onAddComment: (VideoItem, String, String) -> Unit,
    onLikeComment: (VideoComment) -> Unit,
    onDeleteComment: (VideoComment) -> Unit,
    onTogglePinOrHeart: (VideoComment, Boolean, Boolean) -> Unit,
    onReportComment: (VideoComment) -> Unit,
    onCreateShortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (shorts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = "अभी कोई वीडियो उपलब्ध नहीं है।",
                subtitle = "60 सेकंड तक का वर्टिकल वीडियो अपलोड करें और अपना पहला शॉर्ट बनाएं।",
                actionLabel = "पहला वीडियो अपलोड करें",
                onAction = onCreateShortClick
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { shorts.size })
    var isMuted by remember { mutableStateOf(false) }
    var is2xSpeed by remember { mutableStateOf(false) }
    var commentSheetShort by remember { mutableStateOf<VideoItem?>(null) }

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("shorts_pager")
    ) { page ->
        val shortVideo = shorts[page]
        val isActivePage = pagerState.currentPage == page
        val isLiked = likes.any { it.targetId == shortVideo.resolvedId && it.reaction == "like" }
        val isSubscribed = subscriptions.any { it.channelId == shortVideo.ownerUid }

        ShortPlayerPage(
            video = shortVideo,
            channel = channelMap[shortVideo.ownerUid],
            isActivePage = isActivePage,
            isMuted = isMuted,
            is2xSpeed = is2xSpeed,
            isLiked = isLiked,
            isSubscribed = isSubscribed,
            onRegisterQualifiedView = onRegisterQualifiedView,
            onToggleMute = { isMuted = !isMuted },
            onToggleSpeed = { is2xSpeed = !is2xSpeed },
            onLike = { onLikeShort(shortVideo.resolvedId) },
            onOpenComments = { commentSheetShort = shortVideo },
            onShare = { onShareShort(shortVideo) },
            onSubscribe = { onToggleSubscribe(shortVideo) }
        )
    }

    if (commentSheetShort != null) {
        val target = commentSheetShort!!
        val comments by remember(target.resolvedId) { observeComments(target) }.collectAsState(initial = emptyList())
        ModalBottomSheet(onDismissRequest = { commentSheetShort = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CommentsSection(
                    video = target,
                    comments = comments,
                    currentUserId = currentUserId,
                    onAddComment = { text, parentId -> onAddComment(target, text, parentId) },
                    onLikeComment = onLikeComment,
                    onDeleteComment = onDeleteComment,
                    onTogglePinOrHeart = onTogglePinOrHeart,
                    onReportComment = onReportComment
                )
            }
        }
    }
}

@Composable
private fun ShortPlayerPage(
    video: VideoItem,
    channel: UserChannel?,
    isActivePage: Boolean,
    isMuted: Boolean,
    is2xSpeed: Boolean,
    isLiked: Boolean,
    isSubscribed: Boolean,
    onRegisterQualifiedView: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeed: () -> Unit,
    onLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onSubscribe: () -> Unit
) {
    val context = LocalContext.current
    var isPausedByTap by remember { mutableStateOf(false) }
    var clearScreenMode by remember { mutableStateOf(false) }
    var playbackState by remember(video.resolvedId) { mutableIntStateOf(Player.STATE_IDLE) }
    var isActuallyPlaying by remember(video.resolvedId) { mutableStateOf(false) }

    val channelName = channel?.displayName?.ifBlank { null } ?: video.channelName.ifBlank { "GLASYER क्रिएटर" }
    val channelAvatar = channel?.photoUrl?.ifBlank { null } ?: video.channelAvatar

    val exoPlayer = remember(video.resolvedId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isActuallyPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActivePage, isPausedByTap) {
        exoPlayer.playWhenReady = isActivePage && !isPausedByTap
    }

    LaunchedEffect(video.resolvedId, isActivePage, isActuallyPlaying, playbackState) {
        if (isActivePage && isActuallyPlaying && playbackState == Player.STATE_READY) {
            delay(1000L)
            if (exoPlayer.isPlaying) {
                onRegisterQualifiedView(video.resolvedId)
            }
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(is2xSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(if (is2xSpeed) 2.0f else 1.0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isPausedByTap = !isPausedByTap },
                    onLongPress = { clearScreenMode = true },
                    onPress = {
                        tryAwaitRelease()
                        clearScreenMode = false
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (isPausedByTap && !clearScreenMode) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "चलाएं",
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        if (!clearScreenMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "शॉर्ट्स",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (is2xSpeed) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable(onClick = onToggleSpeed)
                    ) {
                        Text(
                            text = if (is2xSpeed) "2x" else "1x",
                            color = if (is2xSpeed) MaterialTheme.colorScheme.onPrimary else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape
                    ) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "अनम्यूट" else "म्यूट",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(name = channelName, photoUrl = channelAvatar, size = 36.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channelName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (channel?.verified == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "सत्यापित",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onSubscribe,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isSubscribed) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (isSubscribed) "सब्सक्राइब्ड" else "सब्सक्राइब",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "दबाकर रखें = क्लियर-स्क्रीन मोड",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ShortRailAction(
                            icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            label = formatCount(video.likesCount),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = onLike
                        )
                        ShortRailAction(
                            icon = Icons.AutoMirrored.Filled.Comment,
                            label = formatCount(video.commentsCount),
                            tint = Color.White,
                            onClick = onOpenComments
                        )
                        ShortRailAction(
                            icon = Icons.Filled.Share,
                            label = "शेयर",
                            tint = Color.White,
                            onClick = onShare
                        )
                        ShortRailAction(
                            icon = if (isPausedByTap) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            label = if (isPausedByTap) "चलाएं" else "रोकें",
                            tint = if (isPausedByTap) GlasyerLiveCoral else Color.White,
                            onClick = { isPausedByTap = !isPausedByTap }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortRailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
