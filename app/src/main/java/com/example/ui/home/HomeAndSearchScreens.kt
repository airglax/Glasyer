package com.example.ui.home

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GlasyerCategories
import com.example.model.NotificationItem
import com.example.model.UiState
import com.example.model.UserChannel
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.GlasyerLogo
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.GlasyerLiveCoral

@Composable
fun HomeTopBar(
    myChannel: UserChannel?,
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlasyerLogo(fontSizeSp = 22)
            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotifications > 0) {
                            Badge(containerColor = GlasyerLiveCoral, contentColor = Color.White) {
                                Text(unreadNotifications.coerceAtMost(99).toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (unreadNotifications > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "सूचनाएं"
                    )
                }
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "खोजें"
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                ChannelAvatar(
                    name = myChannel?.displayName ?: "G",
                    photoUrl = myChannel?.photoUrl.orEmpty(),
                    size = 32.dp
                )
            }
        }
    }
}

@Composable
fun HomeFeedScreen(
    videosState: UiState<List<VideoItem>>,
    channelMap: Map<String, UserChannel> = emptyMap(),
    selectedCategory: String,
    restrictedMode: Boolean,
    blockedIds: Set<String>,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSelectCategory: (String) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenShorts: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onSaveWatchLater: (String) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onReportVideo: (VideoItem) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuVideo by remember { mutableStateOf<VideoItem?>(null) }
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(GlasyerCategories.all) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(category) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (category == "लाइव") {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else GlasyerLiveCoral)
                                )
                            }
                            Text(category, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (category == "लाइव") GlasyerLiveCoral else MaterialTheme.colorScheme.primary,
                        selectedLabelColor = if (category == "लाइव") Color.White else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }

        when (videosState) {
            is UiState.Loading -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(4) { VideoCardSkeleton() }
                }
            }
            is UiState.Error -> {
                ErrorStateCard(message = videosState.message)
            }
            is UiState.Success -> {
                val filtered = remember(videosState.data, selectedCategory, restrictedMode, blockedIds) {
                    videosState.data.filter { v ->
                        val passRestricted = !restrictedMode || !v.ageRestricted
                        val passBlocked = v.ownerUid !in blockedIds
                        val passCategory = when (selectedCategory) {
                            "सब" -> true
                            "लाइव" -> v.videoType == "live" || v.category == "लाइव"
                            else -> v.category == selectedCategory || v.tags.any { it.equals(selectedCategory, true) }
                        }
                        passRestricted && passBlocked && passCategory
                    }
                }

                val shouldLoadMore by remember(filtered.size, hasMore, isLoadingMore) {
                    derivedStateOf {
                        if (!hasMore || isLoadingMore || filtered.isEmpty()) return@derivedStateOf false
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= filtered.size - 2
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) onLoadMore()
                }

                if (filtered.isEmpty()) {
                    EmptyStateCard(
                        title = "अभी कोई वीडियो उपलब्ध नहीं है।",
                        subtitle = if (videosState.data.isEmpty()) {
                            "GLASYER पर अपना वास्तविक वीडियो अपलोड करके शुरुआत करें।"
                        } else {
                            "इस श्रेणी में कोई सार्वजनिक वीडियो नहीं मिला।"
                        },
                        actionLabel = "पहला वीडियो अपलोड करें",
                        onAction = onCreateClick
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filtered, key = { it.resolvedId }) { video ->
                            VideoCard(
                                video = video,
                                channel = channelMap[video.ownerUid],
                                onClick = {
                                    if (video.videoType == "short") onOpenShorts() else onOpenVideo(video)
                                },
                                onChannelClick = { onOpenChannel(video.ownerUid) },
                                onMenuClick = { menuVideo = video }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (menuVideo != null) {
        VideoQuickOptionsSheet(
            video = menuVideo!!,
            onDismiss = { menuVideo = null },
            onWatchLater = {
                onSaveWatchLater(menuVideo!!.resolvedId)
                menuVideo = null
            },
            onShare = {
                onShareVideo(menuVideo!!)
                menuVideo = null
            },
            onReport = {
                onReportVideo(menuVideo!!)
                menuVideo = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickOptionsSheet(
    video: VideoItem,
    onDismiss: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            SheetActionRow("बाद में देखें में सहेजें", onClick = onWatchLater)
            SheetActionRow("वीडियो साझा करें", onClick = onShare)
            SheetActionRow("रिपोर्ट करें", onClick = onReport, isDestructive = true)
        }
    }
}

@Composable
private fun SheetActionRow(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) GlasyerLiveCoral else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SearchScreen(
    query: String,
    recentSearches: List<String>,
    allVideos: List<VideoItem>,
    channelMap: Map<String, UserChannel> = emptyMap(),
    onQueryChange: (String) -> Unit,
    onCommitQuery: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val suggestions = remember(query, allVideos) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            allVideos.flatMap { listOf(it.title, it.channelName) + it.tags }
                .distinct()
                .filter { it.lowercase().contains(q) }
                .take(6)
        }
    }

    val results = remember(query, allVideos) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            allVideos.filter { v ->
                v.title.lowercase().contains(q) ||
                    v.channelName.lowercase().contains(q) ||
                    v.tags.any { it.lowercase().contains(q) } ||
                    v.category.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("GLASYER पर खोजें (शीर्षक, चैनल, टैग)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onCommitQuery(query) }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "साफ़ करें")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input")
            )
            IconButton(onClick = { onCommitQuery(query) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Search, contentDescription = "खोजें")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                if (recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("हाल की खोजें", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onClearRecent) { Text("सब साफ़ करें") }
                        }
                    }
                    items(recentSearches) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCommitQuery(item) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                item,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { onRemoveRecent(item) }) {
                                Icon(Icons.Filled.Close, contentDescription = "हटाएं", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    item {
                        EmptyStateCard(
                            title = "अपने पसंदीदा वीडियो खोजें",
                            subtitle = "चैनल का नाम, विषय या #टैग लिखकर खोज शुरू करें।"
                        )
                    }
                }
            } else {
                if (suggestions.isNotEmpty()) {
                    items(suggestions) { sug ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCommitQuery(sug) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(sug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                }

                if (results.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "कोई परिणाम नहीं मिला",
                            subtitle = "\"$query\" से मेल खाने वाला कोई वीडियो नहीं मिला। कुछ अन्य शब्द आज़माएं।"
                        )
                    }
                } else {
                    items(results, key = { it.resolvedId }) { video ->
                        VideoCard(
                            video = video,
                            channel = channelMap[video.ownerUid],
                            onClick = {
                                onCommitQuery(query)
                                onSelectVideo(video)
                            },
                            onChannelClick = { onOpenChannel(video.ownerUid) },
                            onMenuClick = {}
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    onOpenTarget: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "सूचनाएं",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            if (notifications.isEmpty()) {
                EmptyStateCard(
                    title = "कोई नई सूचना नहीं है",
                    subtitle = "आपके सब्सक्राइब किए गए चैनलों के नए अपलोड और उत्तर यहाँ दिखेंगे।"
                )
            } else {
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    items(notifications, key = { it.id }) { notif ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (!notif.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onMarkRead(notif.id)
                                    if (notif.targetId.isNotBlank()) onOpenTarget(notif.targetId)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ChannelAvatar(name = notif.actorName, photoUrl = "", size = 38.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    notif.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    notif.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatTimeAgo(notif.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
