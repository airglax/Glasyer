package com.example.ui.you

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.LikeRecord
import com.example.model.Playlist
import com.example.model.UserChannel
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlasyerThumbnail
import com.example.ui.components.VideoCard
import com.example.ui.components.formatCount
import com.example.ui.theme.GlasyerLiveCoral

enum class LibrarySubView {
    NONE, HISTORY, LIKED, WATCH_LATER, DOWNLOADS, PLAYLIST_DETAIL
}

@Composable
fun YouTabScreen(
    myChannel: UserChannel?,
    history: List<WatchHistoryEntry>,
    historyPaused: Boolean,
    allVideos: List<VideoItem>,
    likes: List<LikeRecord>,
    playlists: List<Playlist>,
    downloadedIds: Set<String>,
    onOpenChannel: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onToggleHistoryPaused: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCreatePlaylist: (title: String, visibility: String) -> Unit,
    onUpdatePlaylist: (Playlist, String, String, List<String>) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var subView by remember { mutableStateOf(LibrarySubView.NONE) }
    var activePlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = subView != LibrarySubView.NONE) {
        subView = LibrarySubView.NONE
    }

    val videoMap = remember(allVideos) { allVideos.associateBy { it.resolvedId } }
    val likedVideos = remember(likes, videoMap) {
        likes.filter { it.targetType == "video" && it.reaction == "like" }
            .mapNotNull { videoMap[it.targetId] }
    }
    val watchLaterPlaylist = playlists.firstOrNull { it.systemType == "watch_later" }
    val watchLaterVideos = remember(watchLaterPlaylist, videoMap) {
        watchLaterPlaylist?.videoIds?.mapNotNull { videoMap[it] }.orEmpty()
    }
    val downloadedVideos = remember(downloadedIds, videoMap) {
        downloadedIds.mapNotNull { videoMap[it] }
    }

    when (subView) {
        LibrarySubView.HISTORY -> {
            Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
                SubViewHeader("वॉच हिस्ट्री") { subView = LibrarySubView.NONE }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("हिस्ट्री रोकें", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = historyPaused, onCheckedChange = onToggleHistoryPaused)
                    }
                    TextButton(onClick = onClearHistory, enabled = history.isNotEmpty()) {
                        Text("इतिहास साफ़ करें", color = GlasyerLiveCoral)
                    }
                }
                HorizontalDivider()
                if (history.isEmpty()) {
                    EmptyStateCard(
                        title = "वॉच हिस्ट्री खाली है",
                        subtitle = "आपके द्वारा देखे गए वीडियो यहाँ सहेजे जाएंगे।"
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp)) {
                        items(history, key = { it.id }) { item ->
                            val video = videoMap[item.videoId]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (video != null) onOpenVideo(video) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlasyerThumbnail(
                                    thumbnailUrl = item.thumbnailUrl,
                                    contentDescription = item.videoTitle,
                                    modifier = Modifier.size(width = 128.dp, height = 72.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.videoTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${item.channelName} • ${item.durationLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        LibrarySubView.LIKED -> {
            VideoCollectionSubScreen(
                title = "पसंद किए गए वीडियो",
                videos = likedVideos,
                emptyTitle = "कोई लाइक किया गया वीडियो नहीं",
                emptySubtitle = "जो वीडियो आपको पसंद आएं उन पर लाइक दबाएं।",
                onBack = { subView = LibrarySubView.NONE },
                onOpenVideo = onOpenVideo,
                modifier = modifier
            )
        }
        LibrarySubView.WATCH_LATER -> {
            VideoCollectionSubScreen(
                title = "बाद में देखें",
                videos = watchLaterVideos,
                emptyTitle = "'बाद में देखें' सूची खाली है",
                emptySubtitle = "किसी भी वीडियो को सहेजने के लिए 'सहेजें' बटन दबाएं।",
                onBack = { subView = LibrarySubView.NONE },
                onOpenVideo = onOpenVideo,
                modifier = modifier
            )
        }
        LibrarySubView.DOWNLOADS -> {
            VideoCollectionSubScreen(
                title = "डाउनलोड्स",
                videos = downloadedVideos,
                emptyTitle = "कोई डाउनलोड किया गया वीडियो नहीं",
                emptySubtitle = "वीडियो प्लेयर में 'डाउनलोड' बटन दबाकर ऑफ़लाइन सूची बनाएं।",
                onBack = { subView = LibrarySubView.NONE },
                onOpenVideo = onOpenVideo,
                modifier = modifier
            )
        }
        LibrarySubView.PLAYLIST_DETAIL -> {
            val playlist = playlists.firstOrNull { it.id == activePlaylistId }
            if (playlist == null) {
                subView = LibrarySubView.NONE
            } else {
                PlaylistDetailScreen(
                    playlist = playlist,
                    videoMap = videoMap,
                    onBack = { subView = LibrarySubView.NONE },
                    onOpenVideo = onOpenVideo,
                    onUpdatePlaylist = { title, vis, ids -> onUpdatePlaylist(playlist, title, vis, ids) },
                    onDelete = {
                        onDeletePlaylist(playlist.id)
                        subView = LibrarySubView.NONE
                    }
                )
            }
        }
        LibrarySubView.NONE -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenChannel)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(
                            name = myChannel?.displayName ?: "G",
                            photoUrl = myChannel?.photoUrl.orEmpty(),
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = myChannel?.displayName ?: "GLASYER क्रिएटर",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${myChannel?.username ?: "creator"} • ${formatCount(myChannel?.subscriberCount ?: 0L)} सब्सक्राइबर",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "चैनल देखें ›",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).testTag("settings_button")) {
                            Icon(Icons.Filled.Settings, contentDescription = "सेटिंग्स")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenStudio,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("creator_studio_button")
                        ) {
                            Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("क्रिएटर स्टूडियो")
                        }
                        Button(
                            onClick = onOpenChannel,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("आपका चैनल")
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                }

                // History strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("इतिहास", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { subView = LibrarySubView.HISTORY }) {
                            Text("सभी देखें")
                        }
                    }
                    if (history.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(history.take(10), key = { it.id }) { h ->
                                val video = videoMap[h.videoId]
                                Column(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .clickable { if (video != null) onOpenVideo(video) }
                                ) {
                                    GlasyerThumbnail(
                                        thumbnailUrl = h.thumbnailUrl,
                                        contentDescription = h.videoTitle,
                                        modifier = Modifier.size(width = 148.dp, height = 84.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(h.videoTitle, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Text(
                            "कोई हालिया इतिहास नहीं",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                }

                // Library quick rows
                item {
                    LibraryRow(Icons.Filled.ThumbUp, "पसंद किए गए वीडियो", "${likedVideos.size} वीडियो") {
                        subView = LibrarySubView.LIKED
                    }
                    LibraryRow(Icons.Filled.WatchLater, "बाद में देखें", "${watchLaterVideos.size} वीडियो") {
                        subView = LibrarySubView.WATCH_LATER
                    }
                    LibraryRow(Icons.Filled.Download, "डाउनलोड्स", "${downloadedVideos.size} वीडियो") {
                        subView = LibrarySubView.DOWNLOADS
                    }
                    LibraryRow(Icons.Filled.VideoLibrary, "आपके वीडियो (स्टूडियो)", "प्रबंधित करें व आँकड़े देखें") {
                        onOpenStudio()
                    }
                    HorizontalDivider()
                }

                // Playlists section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("प्लेलिस्ट", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("नई प्लेलिस्ट")
                        }
                    }
                }

                val customPlaylists = playlists.filter { it.systemType != "watch_later" }
                if (customPlaylists.isEmpty()) {
                    item {
                        Text(
                            "अभी कोई कस्टम प्लेलिस्ट नहीं है। ऊपर '+ नई प्लेलिस्ट' दबाएं।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(customPlaylists, key = { it.id }) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activePlaylistId = pl.id
                                    subView = LibrarySubView.PLAYLIST_DETAIL
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${pl.videoIds.size} वीडियो • ${if (pl.visibility == "public") "पब्लिक" else "प्राइवेट"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (pl.visibility == "public") Icons.Filled.Public else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var plTitle by remember { mutableStateOf("") }
        var plVisibility by remember { mutableStateOf("private") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("नई प्लेलिस्ट बनाएं") },
            text = {
                Column {
                    OutlinedTextField(
                        value = plTitle,
                        onValueChange = { plTitle = it.take(100) },
                        label = { Text("प्लेलिस्ट का नाम") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = plVisibility == "private", onClick = { plVisibility = "private" }, label = { Text("प्राइवेट") })
                        FilterChip(selected = plVisibility == "public", onClick = { plVisibility = "public" }, label = { Text("पब्लिक") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (plTitle.isNotBlank()) {
                        onCreatePlaylist(plTitle.trim(), plVisibility)
                        showCreatePlaylistDialog = false
                    }
                }) { Text("बनाएं") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("रद्द करें") }
            }
        )
    }
}

@Composable
private fun LibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubViewHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VideoCollectionSubScreen(
    title: String,
    videos: List<VideoItem>,
    emptyTitle: String,
    emptySubtitle: String,
    onBack: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        SubViewHeader(title, onBack)
        if (videos.isEmpty()) {
            EmptyStateCard(title = emptyTitle, subtitle = emptySubtitle)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp)) {
                items(videos, key = { it.resolvedId }) { v ->
                    VideoCard(
                        video = v,
                        onClick = { onOpenVideo(v) },
                        onChannelClick = {},
                        onMenuClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    videoMap: Map<String, VideoItem>,
    onBack: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onUpdatePlaylist: (String, String, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (playlist.visibility == "public") "पब्लिक प्लेलिस्ट" else "प्राइवेट प्लेलिस्ट", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "नाम बदलें")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "प्लेलिस्ट हटाएं", tint = GlasyerLiveCoral)
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = playlist.visibility == "public",
                onClick = { onUpdatePlaylist(playlist.title, "public", playlist.videoIds) },
                label = { Text("पब्लिक") }
            )
            FilterChip(
                selected = playlist.visibility == "private",
                onClick = { onUpdatePlaylist(playlist.title, "private", playlist.videoIds) },
                label = { Text("प्राइवेट") }
            )
        }
        HorizontalDivider()

        val itemsList = playlist.videoIds
        if (itemsList.isEmpty()) {
            EmptyStateCard(
                title = "यह प्लेलिस्ट खाली है",
                subtitle = "किसी वीडियो पर 'सहेजें' दबाकर उसे इस प्लेलिस्ट में जोड़ें।"
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(itemsList.size) { index ->
                    val vidId = itemsList[index]
                    val video = videoMap[vidId]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { if (video != null) onOpenVideo(video) }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = video?.title ?: "वीडियो ($vidId)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            if (index > 0) {
                                IconButton(onClick = {
                                    val mutable = itemsList.toMutableList()
                                    val tmp = mutable[index - 1]
                                    mutable[index - 1] = mutable[index]
                                    mutable[index] = tmp
                                    onUpdatePlaylist(playlist.title, playlist.visibility, mutable)
                                }) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "ऊपर ले जाएं")
                                }
                            }
                            if (index < itemsList.lastIndex) {
                                IconButton(onClick = {
                                    val mutable = itemsList.toMutableList()
                                    val tmp = mutable[index + 1]
                                    mutable[index + 1] = mutable[index]
                                    mutable[index] = tmp
                                    onUpdatePlaylist(playlist.title, playlist.visibility, mutable)
                                }) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "नीचे ले जाएं")
                                }
                            }
                            IconButton(onClick = {
                                val mutable = itemsList.toMutableList().apply { removeAt(index) }
                                onUpdatePlaylist(playlist.title, playlist.visibility, mutable)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "हटाएं")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("प्लेलिस्ट का नाम बदलें") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it.take(100) }) },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onUpdatePlaylist(newName.trim(), playlist.visibility, playlist.videoIds)
                        showRenameDialog = false
                    }
                }) { Text("सहेजें") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("रद्द करें") }
            }
        )
    }
}
