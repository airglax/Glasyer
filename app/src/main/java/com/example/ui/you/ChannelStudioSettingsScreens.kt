package com.example.ui.you

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.BlockedUser
import com.example.model.CommunityPost
import com.example.model.GlasyerCategories
import com.example.model.Playlist
import com.example.model.UserChannel
import com.example.model.UserSettings
import com.example.model.VideoItem
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.VideoCard
import com.example.ui.components.formatCount
import com.example.ui.subscriptions.CommunityPostCard
import com.example.ui.theme.GlasyerLiveCoral

@Composable
fun ChannelProfileScreen(
    channel: UserChannel,
    isOwner: Boolean,
    isSubscribed: Boolean,
    videos: List<VideoItem>,
    posts: List<CommunityPost>,
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onToggleSubscribe: () -> Unit,
    onSaveProfile: (username: String, displayName: String, photoUrl: String, bannerUrl: String, bio: String) -> Unit,
    onBlockCreator: () -> Unit,
    onLogOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }

    val channelVideos = remember(videos, channel.userId) {
        videos.filter { it.ownerUid == channel.userId && it.videoType != "short" }
    }
    val channelShorts = remember(videos, channel.userId) {
        videos.filter { it.ownerUid == channel.userId && it.videoType == "short" }
    }
    val channelPosts = remember(posts, channel.userId) {
        posts.filter { it.authorId == channel.userId }
    }
    val channelPlaylists = remember(playlists, channel.userId) {
        playlists.filter { it.ownerId == channel.userId && (isOwner || it.visibility == "public") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            Text(channel.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isOwner) {
                IconButton(onClick = onLogOut) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "लॉग आउट")
                }
            }
        }

        // Channel Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            if (channel.bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.bannerUrl,
                    contentDescription = "चैनल बैनर",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatar(name = channel.displayName, photoUrl = channel.photoUrl, size = 64.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@${channel.username} • ${formatCount(channel.subscriberCount)} सब्सक्राइबर", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(modifier = Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isOwner) {
                Button(onClick = { showEditDialog = true }, modifier = Modifier.weight(1f).testTag("edit_profile_button")) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("प्रोफ़ाइल संपादित करें")
                }
                OutlinedButton(onClick = onLogOut) {
                    Text("लॉग आउट")
                }
            } else {
                Button(onClick = onToggleSubscribe, modifier = Modifier.weight(1f)) {
                    Text(if (isSubscribed) "सब्सक्राइब्ड" else "सब्सक्राइब करें")
                }
                OutlinedButton(onClick = onBlockCreator) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ब्लॉक")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val tabs = listOf("वीडियो", "शॉर्ट्स", "पोस्ट", "प्लेलिस्ट", "हमारे बारे में")
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            when (selectedTab) {
                0 -> {
                    if (channelVideos.isEmpty()) item { EmptyStateCard("कोई वीडियो नहीं", "इस चैनल पर अभी कोई वीडियो अपलोड नहीं है।") }
                    else items(channelVideos, key = { it.resolvedId }) { v -> VideoCard(v, channel, { onOpenVideo(v) }, {}, {}) }
                }
                1 -> {
                    if (channelShorts.isEmpty()) item { EmptyStateCard("कोई शॉर्ट नहीं", "अभी कोई शॉर्ट उपलब्ध नहीं है।") }
                    else items(channelShorts, key = { it.resolvedId }) { v -> VideoCard(v, channel, { onOpenVideo(v) }, {}, {}) }
                }
                2 -> {
                    if (channelPosts.isEmpty()) item { EmptyStateCard("कोई पोस्ट नहीं", "अभी कोई कम्युनिटी पोस्ट नहीं है।") }
                    else items(channelPosts, key = { it.id }) { p ->
                        Box(modifier = Modifier.padding(12.dp)) {
                            CommunityPostCard(p, null, {}, {}, {}, {})
                        }
                    }
                }
                3 -> {
                    if (channelPlaylists.isEmpty()) item { EmptyStateCard("कोई प्लेलिस्ट नहीं", "कोई सार्वजनिक प्लेलिस्ट नहीं मिली।") }
                    else items(channelPlaylists, key = { it.id }) { pl ->
                        ListItemCard(pl.title, "${pl.videoIds.size} वीडियो • ${pl.visibility}")
                    }
                }
                4 -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("विवरण (About)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(channel.bio.ifBlank { "कोई विवरण नहीं जोड़ा गया है।" }, style = MaterialTheme.typography.bodyLarge)
                            HorizontalDivider()
                            Text("कुल वीडियो: ${channelVideos.size + channelShorts.size}", style = MaterialTheme.typography.bodyMedium)
                            Text("कुल सब्सक्राइबर: ${channel.subscriberCount}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            channel = channel,
            onDismiss = { showEditDialog = false },
            onSave = { u, d, p, b, bio ->
                onSaveProfile(u, d, p, b, bio)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ListItemCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditProfileDialog(
    channel: UserChannel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var username by remember { mutableStateOf(channel.username) }
    var displayName by remember { mutableStateOf(channel.displayName) }
    var photoUrl by remember { mutableStateOf(channel.photoUrl) }
    var bannerUrl by remember { mutableStateOf(channel.bannerUrl) }
    var bio by remember { mutableStateOf(channel.bio) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) photoUrl = uri.toString()
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) bannerUrl = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("चैनल संपादित करें") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("अवतार बदलें")
                    }
                    OutlinedButton(onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("बैनर बदलें")
                    }
                }
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("प्रदर्शन नाम") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it.replace(" ", "_").lowercase() }, label = { Text("यूज़रनेम") }, singleLine = true)
                OutlinedTextField(value = bio, onValueChange = { bio = it.take(300) }, label = { Text("परिचय") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(username, displayName, photoUrl, bannerUrl, bio) }) { Text("सहेजें") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करें") }
        }
    )
}

@Composable
fun CreatorStudioScreen(
    myChannel: UserChannel?,
    myVideos: List<VideoItem>,
    onBack: () -> Unit,
    onEditVideo: (videoId: String, title: String, desc: String, cat: String, vis: String, comments: Boolean, age: Boolean) -> Unit,
    onDeleteVideo: (String) -> Unit
) {
    var editingVideo by remember { mutableStateOf<VideoItem?>(null) }

    val totalViews = myVideos.sumOf { it.viewsCount }
    val totalLikes = myVideos.sumOf { it.likesCount }
    val totalComments = myVideos.sumOf { it.commentsCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            Text("क्रिएटर स्टूडियो", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StudioStatCard("कुल व्यूज़", formatCount(totalViews), Modifier.weight(1f))
                    StudioStatCard("सब्सक्राइबर", formatCount(myChannel?.subscriberCount ?: 0L), Modifier.weight(1f))
                    StudioStatCard("लाइक / टिप्पणियाँ", "${formatCount(totalLikes)} / ${formatCount(totalComments)}", Modifier.weight(1f))
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("वीडियो व्यूज़ विश्लेषण", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        val barValues = remember(myVideos) {
                            myVideos.take(7).map { it.viewsCount.toFloat() }
                        }
                        if (barValues.isEmpty()) {
                            Text(
                                text = "आँकड़े देखने के लिए पहले कोई वास्तविक वीडियो अपलोड करें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val barColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                val maxVal = (barValues.maxOrNull() ?: 1f).coerceAtLeast(1f)
                                val slotWidth = size.width / barValues.size
                                val barWidth = slotWidth * 0.55f
                                barValues.forEachIndexed { idx, v ->
                                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                    val h = (v / maxVal) * (size.height - 16f)
                                    drawRoundRect(
                                        color = trackColor,
                                        topLeft = Offset(x, 0f),
                                        size = Size(barWidth, size.height),
                                        cornerRadius = CornerRadius(10f, 10f)
                                    )
                                    if (h > 0f) {
                                        drawRoundRect(
                                            color = barColor,
                                            topLeft = Offset(x, size.height - h),
                                            size = Size(barWidth, h),
                                            cornerRadius = CornerRadius(10f, 10f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("आपके वीडियो (${myVideos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (myVideos.isEmpty()) {
                item {
                    EmptyStateCard("अभी कोई वीडियो नहीं", "जब आप कोई वीडियो अपलोड करेंगे, तो उसके आँकड़े यहाँ दिखेंगे।")
                }
            } else {
                items(myVideos, key = { it.resolvedId }) { v ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(v.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${v.visibility.uppercase()} • ${formatCount(v.viewsCount)} व्यूज़ • ${formatCount(v.likesCount)} लाइक • ${v.commentsCount} टिप्पणियाँ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { editingVideo = v }) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("संपादित करें")
                                }
                                TextButton(onClick = { onDeleteVideo(v.resolvedId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = GlasyerLiveCoral, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("हटाएं", color = GlasyerLiveCoral)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingVideo != null) {
        val target = editingVideo!!
        var t by remember { mutableStateOf(target.title) }
        var d by remember { mutableStateOf(target.description) }
        var cat by remember { mutableStateOf(target.category) }
        var vis by remember { mutableStateOf(target.visibility) }
        var comm by remember { mutableStateOf(target.commentsEnabled) }
        var age by remember { mutableStateOf(target.ageRestricted) }

        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text("वीडियो संपादित करें") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = t, onValueChange = { t = it.take(150) }, label = { Text("शीर्षक") })
                    OutlinedTextField(value = d, onValueChange = { d = it.take(2000) }, label = { Text("विवरण") }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("public", "unlisted", "private").forEach { vMode ->
                            FilterChip(selected = vis == vMode, onClick = { vis = vMode }, label = { Text(vMode) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("टिप्पणियाँ चालू")
                        Switch(checked = comm, onCheckedChange = { comm = it })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("18+ आयु-प्रतिबंधित")
                        Switch(checked = age, onCheckedChange = { age = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (t.isNotBlank()) {
                        onEditVideo(target.resolvedId, t, d, cat.ifBlank { GlasyerCategories.all.first() }, vis, comm, age)
                        editingVideo = null
                    }
                }) { Text("सहेजें") }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) { Text("रद्द करें") }
            }
        )
    }
}

@Composable
private fun StudioStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    blockedUsers: List<BlockedUser>,
    onUpdateSettings: (UserSettings) -> Unit,
    onUnblockUser: (String) -> Unit,
    onLogOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            Text("सेटिंग्स", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("थीम (Theme)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "सिस्टम", "dark" to "डार्क", "light" to "लाइट").forEach { (key, label) ->
                    FilterChip(
                        selected = settings.themeMode == key,
                        onClick = { onUpdateSettings(settings.copy(themeMode = key)) },
                        label = { Text(label) }
                    )
                }
            }

            Text("भाषा (Language)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("hi" to "हिन्दी", "en" to "English").forEach { (key, label) ->
                    FilterChip(
                        selected = settings.language == key,
                        onClick = { onUpdateSettings(settings.copy(language = key)) },
                        label = { Text(label) }
                    )
                }
            }

            Text("प्लेबैक गुणवत्ता (Quality)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto", "1080p", "720p", "480p", "360p").forEach { q ->
                    FilterChip(
                        selected = settings.playbackQuality == q,
                        onClick = { onUpdateSettings(settings.copy(playbackQuality = q)) },
                        label = { Text(q) }
                    )
                }
            }

            SettingSwitch("अगला वीडियो ऑटोप्ले", settings.autoplay) {
                onUpdateSettings(settings.copy(autoplay = it))
            }
            SettingSwitch("प्रतिबंधित मोड (Restricted Mode)", settings.restrictedMode) {
                onUpdateSettings(settings.copy(restrictedMode = it))
            }
            SettingSwitch("डेटा सेवर (Data Saver)", settings.dataSaver) {
                onUpdateSettings(settings.copy(dataSaver = it))
            }

            if (blockedUsers.isNotEmpty()) {
                HorizontalDivider()
                Text("ब्लॉक किए गए उपयोगकर्ता", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                blockedUsers.forEach { bu ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bu.blockedName)
                        TextButton(onClick = { onUnblockUser(bu.blockedUserId) }) { Text("अनब्लॉक करें") }
                    }
                }
            }

            HorizontalDivider()
            OutlinedButton(
                onClick = onLogOut,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("लॉग आउट करें")
            }

            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = GlasyerLiveCoral),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("खाता हटाएं (Delete Account)")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("क्या आप वाकई खाता हटाना चाहते हैं?") },
            text = { Text("आपका चैनल प्रोफ़ाइल डेटा हटा दिया जाएगा और आपको साइन आउट कर दिया जाएगा।") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlasyerLiveCoral)
                ) { Text("हाँ, हटाएं") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("रद्द करें") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
