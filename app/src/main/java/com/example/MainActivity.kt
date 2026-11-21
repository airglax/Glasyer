package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.GlasyerInteractionsRepository
import com.example.data.GlasyerPreferences
import com.example.data.GlasyerRepository
import com.example.data.GlasyerStorageRepository
import com.example.model.MainTab
import com.example.model.UiState
import com.example.model.UserChannel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingProfileScreen
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.components.shareVideoText
import com.example.ui.create.CreateChoiceSheet
import com.example.ui.create.CreatePostScreen
import com.example.ui.create.UploadVideoScreen
import com.example.ui.home.HomeFeedScreen
import com.example.ui.home.HomeTopBar
import com.example.ui.home.NotificationsSheet
import com.example.ui.home.SearchScreen
import com.example.ui.player.MiniPlayerBar
import com.example.ui.player.VideoPlayerScreen
import com.example.ui.shorts.ShortsScreen
import com.example.ui.subscriptions.SubscriptionsScreen
import com.example.ui.theme.GlasyerTheme
import com.example.ui.you.ChannelProfileScreen
import com.example.ui.you.CreatorStudioScreen
import com.example.ui.you.SettingsScreen
import com.example.ui.you.YouTabScreen
import com.example.viewmodel.GlasyerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

sealed interface OverlayRoute {
    data object None : OverlayRoute
    data object Search : OverlayRoute
    data class Upload(val mode: String) : OverlayRoute
    data object CreatePost : OverlayRoute
    data class Channel(val channelId: String) : OverlayRoute
    data object Studio : OverlayRoute
    data object Settings : OverlayRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlasyerRoot()
        }
    }
}

@Composable
private fun GlasyerRoot() {
    val context = LocalContext.current
    val prefs = remember { GlasyerPreferences(context.applicationContext) }
    val localThemeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = "system")

    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )

    GlasyerTheme(themeMode = localThemeMode) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = {})
        } else {
            key(user.uid) {
                val vm: GlasyerViewModel = viewModel(
                    key = user.uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val dbId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(dbId)
                            GlasyerViewModel(
                                repo = GlasyerRepository(db),
                                interactions = GlasyerInteractionsRepository(db),
                                storageRepo = GlasyerStorageRepository(app),
                                prefs = GlasyerPreferences(app),
                                currentUserId = user.uid,
                                initialDisplayName = user.displayName ?: "GLASYER क्रिएटर",
                                initialPhotoUrl = user.photoUrl?.toString().orEmpty()
                            )
                        }
                    }
                )
                GlasyerAuthenticatedApp(vm = vm)
            }
        }
    }
}

@Composable
private fun GlasyerAuthenticatedApp(vm: GlasyerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val channelState by vm.channelState.collectAsStateWithLifecycle()
    val channelMap by vm.channelMap.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val publicVideosState by vm.publicVideosState.collectAsStateWithLifecycle()
    val hasMorePublicVideos by vm.hasMorePublicVideos.collectAsStateWithLifecycle()
    val isLoadingMoreVideos by vm.isLoadingMoreVideos.collectAsStateWithLifecycle()
    val myVideos by vm.myVideosState.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptionsState.collectAsStateWithLifecycle()
    val likes by vm.likesState.collectAsStateWithLifecycle()
    val playlists by vm.playlistsState.collectAsStateWithLifecycle()
    val posts by vm.postsState.collectAsStateWithLifecycle()
    val notifications by vm.notificationsState.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val blockedUsers by vm.blockedUsersState.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearchesState.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIdsState.collectAsStateWithLifecycle()

    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val activeVideo by vm.activeVideo.collectAsStateWithLifecycle()
    val isPlayerMinimized by vm.isPlayerMinimized.collectAsStateWithLifecycle()
    val activeComments by vm.activeCommentsState.collectAsStateWithLifecycle()
    val activeLiveChats by vm.activeLiveChatsState.collectAsStateWithLifecycle()
    val uploadProgress by vm.uploadProgress.collectAsStateWithLifecycle()
    val uploadError by vm.uploadError.collectAsStateWithLifecycle()
    val canRetryUpload by vm.canRetryUpload.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()
    val votedIndices by vm.votedPostIndices.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var overlayRoute by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage!!)
            vm.clearSnackbar()
        }
    }

    val allPublicVideos = (publicVideosState as? UiState.Success)?.data.orEmpty()
    val combinedKnownVideos = remember(allPublicVideos, myVideos) {
        (myVideos + allPublicVideos).distinctBy { it.resolvedId }
    }
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedUserId }.toSet() }
    val unreadNotifs = remember(notifications) { notifications.count { !it.read } }
    val isEnglish = settings.language == "en"

    GlasyerTheme(themeMode = settings.themeMode) {
        when (val chState = channelState) {
            is UiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 48.dp)) {
                    VideoCardSkeleton()
                    VideoCardSkeleton()
                }
                return@GlasyerTheme
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    ErrorStateCard(message = chState.message)
                }
                return@GlasyerTheme
            }
            is UiState.Success -> {
                val myChannel = chState.data
                if (myChannel != null && !myChannel.onboardingDone) {
                    OnboardingProfileScreen(
                        existingChannel = myChannel,
                        onSaveProfile = { username, displayName, photoUrl, bio ->
                            vm.saveProfile(username, displayName, photoUrl, myChannel.bannerUrl, bio)
                        }
                    )
                    return@GlasyerTheme
                }

                if (overlayRoute != OverlayRoute.None) {
                    BackHandler { overlayRoute = OverlayRoute.None }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val route = overlayRoute) {
                        OverlayRoute.Search -> {
                            SearchScreen(
                                query = searchQuery,
                                recentSearches = recentSearches,
                                allVideos = allPublicVideos,
                                channelMap = channelMap,
                                onQueryChange = vm::updateSearchQuery,
                                onCommitQuery = vm::commitSearchQuery,
                                onRemoveRecent = vm::removeSearchHistoryItem,
                                onClearRecent = vm::clearSearchHistory,
                                onSelectVideo = { v ->
                                    overlayRoute = OverlayRoute.None
                                    if (v.videoType == "short") currentTab = MainTab.SHORTS else vm.openVideo(v)
                                },
                                onOpenChannel = { chId -> overlayRoute = OverlayRoute.Channel(chId) },
                                onBack = { overlayRoute = OverlayRoute.None }
                            )
                        }
                        is OverlayRoute.Upload -> {
                            UploadVideoScreen(
                                initialMode = route.mode,
                                uploadProgress = uploadProgress,
                                uploadError = uploadError,
                                canRetry = canRetryUpload,
                                onClearError = vm::clearUploadError,
                                onCancelUpload = vm::cancelVideoUpload,
                                onRetryUpload = vm::retryVideoUpload,
                                onPublish = { videoUri, thumbUri, title, desc, tags, cat, type, vis, durSec, durLabel, comm, age ->
                                    vm.publishVideo(
                                        videoUri = videoUri,
                                        thumbnailUri = thumbUri,
                                        title = title,
                                        description = desc,
                                        tags = tags,
                                        category = cat,
                                        videoType = type,
                                        visibility = vis,
                                        durationSec = durSec,
                                        durationLabel = durLabel,
                                        commentsEnabled = comm,
                                        ageRestricted = age
                                    ) {
                                        overlayRoute = OverlayRoute.None
                                        currentTab = if (type == "short") MainTab.SHORTS else MainTab.HOME
                                    }
                                },
                                onBack = { overlayRoute = OverlayRoute.None }
                            )
                        }
                        OverlayRoute.CreatePost -> {
                            CreatePostScreen(
                                onPublishPost = { text, imageUris, poll ->
                                    vm.createPost(text, imageUris, poll) {
                                        overlayRoute = OverlayRoute.None
                                        currentTab = MainTab.SUBSCRIPTIONS
                                    }
                                },
                                onBack = { overlayRoute = OverlayRoute.None }
                            )
                        }
                        is OverlayRoute.Channel -> {
                            val liveChannel by remember(route.channelId) {
                                vm.observeChannelById(route.channelId)
                            }.collectAsStateWithLifecycle(initialValue = null)

                            val targetChannel = if (route.channelId == vm.currentUserId) {
                                myChannel ?: UserChannel(userId = vm.currentUserId, displayName = "GLASYER क्रिएटर")
                            } else {
                                liveChannel
                                    ?: channelMap[route.channelId]
                                    ?: allPublicVideos.firstOrNull { it.ownerUid == route.channelId }?.let {
                                        UserChannel(
                                            userId = it.ownerUid,
                                            username = it.channelName.lowercase().replace(" ", "_"),
                                            displayName = it.channelName,
                                            photoUrl = it.channelAvatar
                                        )
                                    }
                                    ?: UserChannel(userId = route.channelId, displayName = "चैनल")
                            }
                            val isSub = subscriptions.any { it.channelId == targetChannel.userId }
                            ChannelProfileScreen(
                                channel = targetChannel,
                                isOwner = targetChannel.userId == vm.currentUserId,
                                isSubscribed = isSub,
                                videos = combinedKnownVideos,
                                posts = posts,
                                playlists = playlists,
                                onBack = { overlayRoute = OverlayRoute.None },
                                onOpenVideo = { v ->
                                    overlayRoute = OverlayRoute.None
                                    vm.openVideo(v)
                                },
                                onToggleSubscribe = {
                                    vm.toggleSubscription(targetChannel.userId, targetChannel.displayName, targetChannel.photoUrl)
                                },
                                onSaveProfile = { u, d, p, b, bio ->
                                    vm.saveProfile(u, d, p, b, bio)
                                },
                                onBlockCreator = {
                                    vm.blockCreator(targetChannel.userId, targetChannel.displayName)
                                    overlayRoute = OverlayRoute.None
                                },
                                onLogOut = {
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                        OverlayRoute.Studio -> {
                            CreatorStudioScreen(
                                myChannel = myChannel,
                                myVideos = myVideos,
                                onBack = { overlayRoute = OverlayRoute.None },
                                onEditVideo = vm::updateVideoMetadata,
                                onDeleteVideo = vm::deleteVideo
                            )
                        }
                        OverlayRoute.Settings -> {
                            SettingsScreen(
                                settings = settings,
                                blockedUsers = blockedUsers,
                                onUpdateSettings = vm::updateSettings,
                                onUnblockUser = vm::unblockCreator,
                                onLogOut = { signOut(credentialManager, scope) },
                                onDeleteAccount = {
                                    vm.deleteAccount { signOut(credentialManager, scope) }
                                },
                                onBack = { overlayRoute = OverlayRoute.None }
                            )
                        }
                        OverlayRoute.None -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val useRail = maxWidth >= 700.dp
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (useRail) {
                                        GlasyerNavigationRail(
                                            currentTab = currentTab,
                                            isEnglish = isEnglish,
                                            onSelectTab = { currentTab = it },
                                            onCreateClick = { showCreateSheet = true }
                                        )
                                    }

                                    Scaffold(
                                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                        snackbarHost = { SnackbarHost(snackbarHostState) },
                                        topBar = {
                                            if (currentTab != MainTab.SHORTS) {
                                                HomeTopBar(
                                                    myChannel = myChannel,
                                                    unreadNotifications = unreadNotifs,
                                                    onNotificationsClick = { showNotificationsSheet = true },
                                                    onSearchClick = { overlayRoute = OverlayRoute.Search },
                                                    onAvatarClick = { overlayRoute = OverlayRoute.Channel(vm.currentUserId) }
                                                )
                                            }
                                        },
                                        bottomBar = {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                if (activeVideo != null && isPlayerMinimized) {
                                                    MiniPlayerBar(
                                                        video = activeVideo!!,
                                                        onExpand = vm::expandPlayer,
                                                        onClose = vm::closePlayer
                                                    )
                                                }
                                                if (!useRail) {
                                                    GlasyerBottomBar(
                                                        currentTab = currentTab,
                                                        isEnglish = isEnglish,
                                                        onSelectTab = { currentTab = it },
                                                        onCreateClick = { showCreateSheet = true }
                                                    )
                                                }
                                            }
                                        }
                                    ) { innerPadding ->
                                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                            when (currentTab) {
                                                MainTab.HOME -> HomeFeedScreen(
                                                    videosState = publicVideosState,
                                                    channelMap = channelMap,
                                                    selectedCategory = selectedCategory,
                                                    restrictedMode = settings.restrictedMode,
                                                    blockedIds = blockedIds,
                                                    hasMore = hasMorePublicVideos,
                                                    isLoadingMore = isLoadingMoreVideos,
                                                    onLoadMore = vm::loadMorePublicVideos,
                                                    onSelectCategory = vm::selectCategory,
                                                    onOpenVideo = vm::openVideo,
                                                    onOpenShorts = { currentTab = MainTab.SHORTS },
                                                    onOpenChannel = { chId -> overlayRoute = OverlayRoute.Channel(chId) },
                                                    onSaveWatchLater = vm::saveToWatchLater,
                                                    onShareVideo = { v -> shareVideoText(context, v) },
                                                    onReportVideo = { v -> vm.reportTarget(v.resolvedId, "video", "अनुचित सामग्री") },
                                                    onCreateClick = { showCreateSheet = true }
                                                )
                                                MainTab.SHORTS -> {
                                                    val shortsList = remember(allPublicVideos, blockedIds) {
                                                        allPublicVideos.filter { it.videoType == "short" && it.ownerUid !in blockedIds }
                                                    }
                                                    ShortsScreen(
                                                        shorts = shortsList,
                                                        channelMap = channelMap,
                                                        likes = likes,
                                                        subscriptions = subscriptions,
                                                        currentUserId = vm.currentUserId,
                                                        observeComments = vm::observeCommentsForVideo,
                                                        onRegisterQualifiedView = vm::registerQualifiedView,
                                                        onLikeShort = { id -> vm.reactToVideo(id, "like") },
                                                        onShareShort = { v -> shareVideoText(context, v) },
                                                        onToggleSubscribe = { v ->
                                                            val ch = channelMap[v.ownerUid]
                                                            vm.toggleSubscription(
                                                                v.ownerUid,
                                                                ch?.displayName ?: v.channelName,
                                                                ch?.photoUrl ?: v.channelAvatar
                                                            )
                                                        },
                                                        onAddComment = { v, text, parentId -> vm.addComment(v, text, parentId) },
                                                        onLikeComment = vm::likeComment,
                                                        onDeleteComment = vm::deleteComment,
                                                        onTogglePinOrHeart = vm::togglePinOrHeartComment,
                                                        onReportComment = { c -> vm.reportTarget(c.id, "comment", "स्पैम") },
                                                        onCreateShortClick = { overlayRoute = OverlayRoute.Upload("short") }
                                                    )
                                                }
                                                MainTab.SUBSCRIPTIONS -> SubscriptionsScreen(
                                                    subscriptions = subscriptions,
                                                    allVideos = allPublicVideos,
                                                    channelMap = channelMap,
                                                    posts = posts,
                                                    votedIndices = votedIndices,
                                                    observePostComments = vm::observePostComments,
                                                    onOpenVideo = vm::openVideo,
                                                    onOpenChannel = { chId -> overlayRoute = OverlayRoute.Channel(chId) },
                                                    onVotePoll = vm::voteOnPoll,
                                                    onLikePost = vm::likePost,
                                                    onAddPostComment = vm::addPostComment,
                                                    onBrowseHome = { currentTab = MainTab.HOME }
                                                )
                                                MainTab.YOU -> YouTabScreen(
                                                    myChannel = myChannel,
                                                    history = history,
                                                    historyPaused = settings.historyPaused,
                                                    allVideos = combinedKnownVideos,
                                                    likes = likes,
                                                    playlists = playlists,
                                                    downloadedIds = downloadedIds,
                                                    onOpenChannel = { overlayRoute = OverlayRoute.Channel(vm.currentUserId) },
                                                    onOpenStudio = { overlayRoute = OverlayRoute.Studio },
                                                    onOpenSettings = { overlayRoute = OverlayRoute.Settings },
                                                    onOpenVideo = vm::openVideo,
                                                    onToggleHistoryPaused = { paused ->
                                                        vm.updateSettings(settings.copy(historyPaused = paused))
                                                    },
                                                    onClearHistory = vm::clearHistory,
                                                    onCreatePlaylist = vm::createPlaylist,
                                                    onUpdatePlaylist = vm::updatePlaylist,
                                                    onDeletePlaylist = vm::deletePlaylist
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (activeVideo != null && !isPlayerMinimized) {
                        val liveVideo = combinedKnownVideos.firstOrNull { it.resolvedId == activeVideo!!.resolvedId } ?: activeVideo!!
                        val uploaderChannel = channelMap[liveVideo.ownerUid]
                            ?: if (liveVideo.ownerUid == vm.currentUserId) myChannel else null
                        val currentReaction = likes.firstOrNull { it.targetId == liveVideo.resolvedId }?.reaction
                        val sub = subscriptions.firstOrNull { it.channelId == liveVideo.ownerUid }
                        VideoPlayerScreen(
                            video = liveVideo,
                            uploaderChannel = uploaderChannel,
                            channelMap = channelMap,
                            suggestedVideos = allPublicVideos.filter { it.videoType != "short" },
                            comments = activeComments,
                            liveChats = activeLiveChats,
                            playlists = playlists,
                            currentUserId = vm.currentUserId,
                            currentReaction = currentReaction,
                            subscription = sub,
                            isDownloaded = liveVideo.resolvedId in downloadedIds,
                            autoplayNext = settings.autoplay,
                            onRegisterQualifiedView = vm::registerQualifiedView,
                            onMinimize = vm::minimizePlayer,
                            onOpenVideo = vm::openVideo,
                            onOpenChannel = { chId ->
                                vm.minimizePlayer()
                                overlayRoute = OverlayRoute.Channel(chId)
                            },
                            onReact = { r -> vm.reactToVideo(liveVideo.resolvedId, r) },
                            onShare = { v -> shareVideoText(context, v) },
                            onSaveWatchLater = vm::saveToWatchLater,
                            onSaveToPlaylist = vm::saveToPlaylist,
                            onToggleDownload = vm::toggleDownload,
                            onToggleSubscribe = {
                                val chName = uploaderChannel?.displayName ?: liveVideo.channelName
                                val chAvatar = uploaderChannel?.photoUrl ?: liveVideo.channelAvatar
                                vm.toggleSubscription(liveVideo.ownerUid, chName, chAvatar)
                            },
                            onUpdateBell = { mode -> vm.updateBellMode(liveVideo.ownerUid, mode) },
                            onAddComment = { text, parentId -> vm.addComment(liveVideo, text, parentId) },
                            onLikeComment = vm::likeComment,
                            onDeleteComment = vm::deleteComment,
                            onTogglePinOrHeart = vm::togglePinOrHeartComment,
                            onSendLiveChat = { msg, amt -> vm.sendLiveChat(liveVideo, msg, amt) },
                            onDeleteLiveChat = vm::deleteLiveChat,
                            onBlockUser = vm::blockCreator,
                            onReport = vm::reportTarget
                        )
                    }
                }

                if (showCreateSheet) {
                    CreateChoiceSheet(
                        onUploadVideo = {
                            showCreateSheet = false
                            vm.clearUploadError()
                            overlayRoute = OverlayRoute.Upload("video")
                        },
                        onCreateShort = {
                            showCreateSheet = false
                            vm.clearUploadError()
                            overlayRoute = OverlayRoute.Upload("short")
                        },
                        onCreatePost = {
                            showCreateSheet = false
                            overlayRoute = OverlayRoute.CreatePost
                        },
                        onDismiss = { showCreateSheet = false }
                    )
                }

                if (showNotificationsSheet) {
                    NotificationsSheet(
                        notifications = notifications,
                        onMarkRead = vm::markNotificationRead,
                        onOpenTarget = { targetId ->
                            showNotificationsSheet = false
                            combinedKnownVideos.firstOrNull { it.resolvedId == targetId }?.let { vm.openVideo(it) }
                        },
                        onDismiss = { showNotificationsSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlasyerBottomBar(
    currentTab: MainTab,
    isEnglish: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onCreateClick: () -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBarItem(
            selected = currentTab == MainTab.HOME,
            onClick = { onSelectTab(MainTab.HOME) },
            icon = { Icon(if (currentTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) },
            label = { Text(if (isEnglish) "Home" else "होम", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.testTag("nav_home")
        )
        NavigationBarItem(
            selected = currentTab == MainTab.SHORTS,
            onClick = { onSelectTab(MainTab.SHORTS) },
            icon = { Icon(if (currentTab == MainTab.SHORTS) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle, contentDescription = null) },
            label = { Text(if (isEnglish) "Shorts" else "शॉर्ट्स", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.testTag("nav_shorts")
        )
        NavigationBarItem(
            selected = false,
            onClick = onCreateClick,
            icon = {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "बनाएं", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(6.dp))
                }
            },
            label = { Text(if (isEnglish) "Create" else "बनाएं", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_create")
        )
        NavigationBarItem(
            selected = currentTab == MainTab.SUBSCRIPTIONS,
            onClick = { onSelectTab(MainTab.SUBSCRIPTIONS) },
            icon = { Icon(if (currentTab == MainTab.SUBSCRIPTIONS) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, contentDescription = null) },
            label = { Text(if (isEnglish) "Subs" else "सब्सक्रिप्शन", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.testTag("nav_subs")
        )
        NavigationBarItem(
            selected = currentTab == MainTab.YOU,
            onClick = { onSelectTab(MainTab.YOU) },
            icon = { Icon(if (currentTab == MainTab.YOU) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = null) },
            label = { Text(if (isEnglish) "You" else "आप", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.testTag("nav_you")
        )
    }
}

@Composable
private fun GlasyerNavigationRail(
    currentTab: MainTab,
    isEnglish: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onCreateClick: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight().navigationBarsPadding()
    ) {
        NavigationRailItem(
            selected = currentTab == MainTab.HOME,
            onClick = { onSelectTab(MainTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(if (isEnglish) "Home" else "होम") }
        )
        NavigationRailItem(
            selected = currentTab == MainTab.SHORTS,
            onClick = { onSelectTab(MainTab.SHORTS) },
            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
            label = { Text(if (isEnglish) "Shorts" else "शॉर्ट्स") }
        )
        NavigationRailItem(
            selected = false,
            onClick = onCreateClick,
            icon = { Icon(Icons.Filled.Add, contentDescription = "बनाएं") },
            label = { Text(if (isEnglish) "Create" else "बनाएं") }
        )
        NavigationRailItem(
            selected = currentTab == MainTab.SUBSCRIPTIONS,
            onClick = { onSelectTab(MainTab.SUBSCRIPTIONS) },
            icon = { Icon(Icons.Filled.Subscriptions, contentDescription = null) },
            label = { Text(if (isEnglish) "Subs" else "सब्सक्रिप्शन") }
        )
        NavigationRailItem(
            selected = currentTab == MainTab.YOU,
            onClick = { onSelectTab(MainTab.YOU) },
            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
            label = { Text(if (isEnglish) "You" else "आप") }
        )
    }
}
