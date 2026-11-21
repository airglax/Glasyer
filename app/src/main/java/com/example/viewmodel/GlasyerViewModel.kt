package com.example.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GlasyerInteractionsRepository
import com.example.data.GlasyerPreferences
import com.example.data.GlasyerRepository
import com.example.data.GlasyerStorageRepository
import com.example.data.mapFirestoreErrorHindi
import com.example.model.BlockedUser
import com.example.model.CommunityPost
import com.example.model.LikeRecord
import com.example.model.LiveChatMessage
import com.example.model.NotificationItem
import com.example.model.Playlist
import com.example.model.PostComment
import com.example.model.Subscription
import com.example.model.UiState
import com.example.model.UploadProgressState
import com.example.model.UserChannel
import com.example.model.UserSettings
import com.example.model.VideoComment
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.UploadSubStage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class PendingUploadRequest(
    val videoUri: Uri,
    val thumbnailUri: Uri?,
    val title: String,
    val description: String,
    val tags: List<String>,
    val category: String,
    val videoType: String,
    val visibility: String,
    val durationSec: Long,
    val durationLabel: String,
    val commentsEnabled: Boolean,
    val ageRestricted: Boolean,
    val onComplete: () -> Unit
)

class GlasyerViewModel(
    private val repo: GlasyerRepository,
    private val interactions: GlasyerInteractionsRepository,
    private val storageRepo: GlasyerStorageRepository,
    private val prefs: GlasyerPreferences,
    val currentUserId: String,
    initialDisplayName: String,
    initialPhotoUrl: String
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("सब")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeVideo = MutableStateFlow<VideoItem?>(null)
    val activeVideo: StateFlow<VideoItem?> = _activeVideo.asStateFlow()

    private val _isPlayerMinimized = MutableStateFlow(false)
    val isPlayerMinimized: StateFlow<Boolean> = _isPlayerMinimized.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _uploadProgress = MutableStateFlow<UploadProgressState?>(null)
    val uploadProgress: StateFlow<UploadProgressState?> = _uploadProgress.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    private val _canRetryUpload = MutableStateFlow(false)
    val canRetryUpload: StateFlow<Boolean> = _canRetryUpload.asStateFlow()

    private var activePublishJob: Job? = null
    private var activeStorageTask: StorageTask<*>? = null
    private var lastPublishRequest: PendingUploadRequest? = null

    private val _votedPostIndices = MutableStateFlow<Map<String, Int>>(emptyMap())
    val votedPostIndices: StateFlow<Map<String, Int>> = _votedPostIndices.asStateFlow()

    private val _extraPagedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _lastPageCursor = MutableStateFlow<DocumentSnapshot?>(null)
    private val _hasMorePublicVideos = MutableStateFlow(false)
    val hasMorePublicVideos: StateFlow<Boolean> = _hasMorePublicVideos.asStateFlow()

    private val _isLoadingMoreVideos = MutableStateFlow(false)
    val isLoadingMoreVideos: StateFlow<Boolean> = _isLoadingMoreVideos.asStateFlow()

    private val viewedVideoIdsInSession = mutableSetOf<String>()

    val channelState: StateFlow<UiState<UserChannel?>> = repo.observeMyChannel(currentUserId)
        .map<UserChannel?, UiState<UserChannel?>> { UiState.Success(it) }
        .catch { error -> handleStreamError(error, "प्रोफ़ाइल लोड करने में त्रुटि") { emit(UiState.Error(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val publicChannelsState: StateFlow<List<UserChannel>> = repo.observePublicChannels()
        .catch { error -> handleStreamError(error, "चैनल लोड त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val channelMap: StateFlow<Map<String, UserChannel>> = publicChannelsState
        .map { list -> list.associateBy { it.userId.ifBlank { it.id } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val settingsState: StateFlow<UserSettings> = repo.observeUserSettings(currentUserId)
        .catch { error -> handleStreamError(error, "सेटिंग्स लोड त्रुटि") { emit(UserSettings(userId = currentUserId)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserSettings(userId = currentUserId))

    private val firstPagePublicVideosFlow: Flow<UiState<List<VideoItem>>> =
        repo.observeFirstPublicVideosPage()
            .map<com.example.data.PagedVideosResult, UiState<List<VideoItem>>> { page ->
                if (_extraPagedVideos.value.isEmpty()) {
                    _lastPageCursor.value = page.lastVisible
                    _hasMorePublicVideos.value = page.hasMore
                }
                UiState.Success(page.videos)
            }
            .catch { error ->
                handleStreamError(error, "वीडियो फ़ीड लोड नहीं हो सका। इंटरनेट जांचें और पुनः प्रयास करें।") {
                    emit(UiState.Error(it))
                }
            }

    val publicVideosState: StateFlow<UiState<List<VideoItem>>> =
        combine(firstPagePublicVideosFlow, _extraPagedVideos) { firstState, extra ->
            when (firstState) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> firstState
                is UiState.Success -> UiState.Success(
                    (firstState.data + extra).distinctBy { it.resolvedId }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myVideosState: StateFlow<List<VideoItem>> = repo.observeMyVideos(currentUserId)
        .catch { error -> handleStreamError(error, "आपके वीडियो लोड नहीं हो सके") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val subscriptionsState: StateFlow<List<Subscription>> = interactions.observeSubscriptions(currentUserId)
        .catch { error -> handleStreamError(error, "सब्सक्रिप्शन त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val likesState: StateFlow<List<LikeRecord>> = interactions.observeLikes(currentUserId)
        .catch { error -> handleStreamError(error, "लाइक त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val playlistsState: StateFlow<List<Playlist>> = interactions.observePlaylists(currentUserId)
        .catch { error -> handleStreamError(error, "प्लेलिस्ट त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val postsState: StateFlow<List<CommunityPost>> = interactions.observePublicPosts()
        .catch { error -> handleStreamError(error, "पोस्ट त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val notificationsState: StateFlow<List<NotificationItem>> = interactions.observeNotifications(currentUserId)
        .catch { error -> handleStreamError(error, "सूचनाएं त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val historyState: StateFlow<List<WatchHistoryEntry>> = interactions.observeHistory(currentUserId)
        .catch { error -> handleStreamError(error, "इतिहास त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val blockedUsersState: StateFlow<List<BlockedUser>> = repo.observeBlockedUsers(currentUserId)
        .catch { error -> handleStreamError(error, "ब्लॉक सूची त्रुटि") { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val recentSearchesState: StateFlow<List<String>> = prefs.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val downloadedIdsState: StateFlow<Set<String>> = prefs.downloadedVideoIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCommentsState: StateFlow<List<VideoComment>> = _activeVideo
        .flatMapLatest { video ->
            if (video == null) flowOf(emptyList())
            else repo.observeComments(video.resolvedId, video.visibility, video.ownerUid)
                .catch { error -> handleStreamError(error, "टिप्पणियाँ त्रुटि") { emit(emptyList()) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeLiveChatsState: StateFlow<List<LiveChatMessage>> = _activeVideo
        .flatMapLatest { video ->
            if (video == null || video.videoType != "live") flowOf(emptyList())
            else interactions.observeLiveChats(video.resolvedId)
                .catch { error -> handleStreamError(error, "लाइव चैट त्रुटि") { emit(emptyList()) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            repo.ensureUserInitialized(currentUserId, initialDisplayName, initialPhotoUrl)
            interactions.cleanupLegacyDemoRecords(currentUserId)
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { prefs.addSearchQuery(query) }
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch { prefs.removeSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { prefs.clearSearches() }
    }

    fun loadMorePublicVideos() {
        val cursor = _lastPageCursor.value ?: return
        if (_isLoadingMoreVideos.value || !_hasMorePublicVideos.value) return
        viewModelScope.launch {
            _isLoadingMoreVideos.value = true
            repo.loadMorePublicVideos(cursor)
                .onSuccess { page ->
                    _extraPagedVideos.value = (_extraPagedVideos.value + page.videos)
                        .distinctBy { it.resolvedId }
                    _lastPageCursor.value = page.lastVisible
                    _hasMorePublicVideos.value = page.hasMore
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "और वीडियो लोड नहीं हो सके।"
                }
            _isLoadingMoreVideos.value = false
        }
    }

    fun openVideo(video: VideoItem) {
        _activeVideo.value = video
        _isPlayerMinimized.value = false
        viewModelScope.launch {
            if (!settingsState.value.historyPaused) {
                interactions.recordWatchHistory(video)
            }
        }
    }

    /**
     * Qualified view tracking: called strictly once per session per video after real playback starts,
     * never on repeated Compose recompositions.
     */
    fun registerQualifiedView(videoId: String) {
        if (videoId.isBlank() || !viewedVideoIdsInSession.add(videoId)) return
        viewModelScope.launch {
            repo.incrementVideoView(videoId)
        }
    }

    fun minimizePlayer() {
        if (_activeVideo.value != null) {
            _isPlayerMinimized.value = true
        }
    }

    fun expandPlayer() {
        if (_activeVideo.value != null) {
            _isPlayerMinimized.value = false
        }
    }

    fun closePlayer() {
        _activeVideo.value = null
        _isPlayerMinimized.value = false
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearUploadError() {
        _uploadError.value = null
        _canRetryUpload.value = false
    }

    fun cancelVideoUpload() {
        runCatching { activeStorageTask?.cancel() }
        activeStorageTask = null
        activePublishJob?.cancel()
        activePublishJob = null
        _uploadProgress.value = null
        val cancelledMsg = "अपलोड रद्द किया गया। आप पुनः प्रयास कर सकते हैं या नया वीडियो चुन सकते हैं।"
        _uploadError.value = cancelledMsg
        _canRetryUpload.value = lastPublishRequest != null
        _snackbarMessage.value = "अपलोड रद्द किया गया।"
    }

    fun retryVideoUpload() {
        val pending = lastPublishRequest ?: return
        publishVideo(
            videoUri = pending.videoUri,
            thumbnailUri = pending.thumbnailUri,
            title = pending.title,
            description = pending.description,
            tags = pending.tags,
            category = pending.category,
            videoType = pending.videoType,
            visibility = pending.visibility,
            durationSec = pending.durationSec,
            durationLabel = pending.durationLabel,
            commentsEnabled = pending.commentsEnabled,
            ageRestricted = pending.ageRestricted,
            onComplete = pending.onComplete
        )
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun observeChannelById(channelUid: String): Flow<UserChannel?> {
        return repo.observeChannelById(channelUid)
            .catch { error -> handleStreamError(error, "चैनल लोड त्रुटि") { emit(null) } }
    }

    fun saveProfile(
        username: String,
        displayName: String,
        photoUrl: String,
        bannerUrl: String,
        bio: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var resolvedPhoto = photoUrl
            if (isLocalUri(photoUrl)) {
                val uploaded = storageRepo.uploadProfileImage("avatar", Uri.parse(photoUrl))
                if (uploaded.isFailure) {
                    _snackbarMessage.value = uploaded.exceptionOrNull()?.localizedMessage
                        ?: "प्रोफ़ाइल फ़ोटो अपलोड विफल रहा।"
                    return@launch
                }
                resolvedPhoto = uploaded.getOrNull().orEmpty()
            }

            var resolvedBanner = bannerUrl
            if (isLocalUri(bannerUrl)) {
                val uploaded = storageRepo.uploadProfileImage("banner", Uri.parse(bannerUrl))
                if (uploaded.isFailure) {
                    _snackbarMessage.value = uploaded.exceptionOrNull()?.localizedMessage
                        ?: "चैनल बैनर अपलोड विफल रहा।"
                    return@launch
                }
                resolvedBanner = uploaded.getOrNull().orEmpty()
            }

            repo.saveChannelProfile(username, displayName, resolvedPhoto, resolvedBanner, bio, onboardingDone = true)
                .onSuccess {
                    _snackbarMessage.value = "प्रोफ़ाइल सहेजी गई"
                    onDone()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "प्रोफ़ाइल सहेजने में त्रुटि"
                }
        }
    }

    fun updateSettings(updated: UserSettings) {
        viewModelScope.launch {
            prefs.setThemeMode(updated.themeMode)
            prefs.setLanguage(updated.language)
            repo.updateSettings(updated)
                .onFailure { _snackbarMessage.value = "सेटिंग्स अपडेट नहीं हो सकीं" }
        }
    }

    fun toggleSubscription(channelId: String, channelName: String, channelAvatar: String) {
        viewModelScope.launch {
            interactions.toggleSubscription(channelId, channelName, channelAvatar)
                .onSuccess { nowSubscribed ->
                    _snackbarMessage.value = if (nowSubscribed) "चैनल सब्सक्राइब किया गया" else "सदस्यता समाप्त की गई"
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "सदस्यता अपडेट विफल रहा"
                }
        }
    }

    fun updateBellMode(channelId: String, mode: String) {
        viewModelScope.launch {
            interactions.updateSubscriptionBell(channelId, mode)
                .onSuccess { _snackbarMessage.value = "नोटिफिकेशन प्राथमिकता अपडेट की गई" }
        }
    }

    fun reactToVideo(videoId: String, reaction: String) {
        viewModelScope.launch {
            interactions.setVideoReaction(videoId, reaction)
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "प्रतिक्रिया सहेजी नहीं जा सकी"
                }
        }
    }

    fun saveToWatchLater(videoId: String) {
        val watchLaterId = playlistsState.value.firstOrNull { it.systemType == "watch_later" }?.id
        viewModelScope.launch {
            interactions.saveToPlaylistOrWatchLater(watchLaterId, videoId)
                .onSuccess { _snackbarMessage.value = "'बाद में देखें' में सहेजा गया" }
                .onFailure { _snackbarMessage.value = "सहेजने में त्रुटि हुई" }
        }
    }

    fun saveToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            interactions.saveToPlaylistOrWatchLater(playlistId, videoId)
                .onSuccess { _snackbarMessage.value = "प्लेलिस्ट में जोड़ा गया" }
        }
    }

    fun createPlaylist(title: String, visibility: String) {
        viewModelScope.launch {
            interactions.createPlaylist(title, visibility)
                .onSuccess { _snackbarMessage.value = "नई प्लेलिस्ट बनाई गई" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "प्लेलिस्ट बनाने में त्रुटि" }
        }
    }

    fun updatePlaylist(playlist: Playlist, newTitle: String, newVisibility: String, newVideoIds: List<String>) {
        viewModelScope.launch {
            interactions.updatePlaylist(playlist.id, newTitle, newVisibility, newVideoIds)
                .onSuccess { _snackbarMessage.value = "प्लेलिस्ट अपडेट हो गई" }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            interactions.deletePlaylist(playlistId)
                .onSuccess { _snackbarMessage.value = "प्लेलिस्ट हटाई गई" }
        }
    }

    fun toggleDownload(videoId: String) {
        viewModelScope.launch {
            val added = prefs.toggleDownload(videoId)
            _snackbarMessage.value = if (added) "ऑफ़लाइन डाउनलोड सूची में जोड़ा गया" else "डाउनलोड से हटाया गया"
        }
    }

    fun addComment(video: VideoItem, text: String, parentId: String = "") {
        val me = (channelState.value as? UiState.Success)?.data
        val myName = me?.displayName?.ifBlank { "उपयोगकर्ता" } ?: "उपयोगकर्ता"
        val myAvatar = me?.photoUrl.orEmpty()
        viewModelScope.launch {
            repo.addComment(video, myName, myAvatar, text, parentId)
                .onSuccess {
                    if (video.ownerUid == currentUserId && parentId.isNotBlank()) {
                        interactions.createNotification(
                            recipientId = currentUserId,
                            actorName = myName,
                            type = "reply",
                            title = "नई टिप्पणी जवाब",
                            body = text.take(100),
                            targetId = video.resolvedId
                        )
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "टिप्पणी जोड़ी नहीं जा सकी"
                }
        }
    }

    fun observeCommentsForVideo(video: VideoItem): Flow<List<VideoComment>> {
        return repo.observeComments(video.resolvedId, video.visibility, video.ownerUid)
            .catch { error -> handleStreamError(error, "टिप्पणियाँ त्रुटि") { emit(emptyList()) } }
    }

    fun togglePinOrHeartComment(comment: VideoComment, togglePin: Boolean, toggleHeart: Boolean) {
        viewModelScope.launch {
            val nextPinned = if (togglePin) !comment.pinned else comment.pinned
            val nextHeart = if (toggleHeart) !comment.creatorHearted else comment.creatorHearted
            repo.updateCommentModeration(comment.videoId, comment.id, nextPinned, nextHeart)
        }
    }

    fun likeComment(comment: VideoComment) {
        viewModelScope.launch {
            repo.toggleCommentLike(comment.videoId, comment.id)
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "टिप्पणी लाइक नहीं हो सकी"
                }
        }
    }

    fun deleteComment(comment: VideoComment) {
        viewModelScope.launch {
            repo.deleteComment(comment.videoId, comment.id)
                .onSuccess { _snackbarMessage.value = "टिप्पणी हटाई गई" }
        }
    }

    fun sendLiveChat(video: VideoItem, message: String, superChatAmount: Long = 0L) {
        val me = (channelState.value as? UiState.Success)?.data
        viewModelScope.launch {
            interactions.sendLiveChat(
                video = video,
                senderName = me?.displayName ?: "दर्शक",
                senderAvatar = me?.photoUrl.orEmpty(),
                message = message,
                superChatAmount = superChatAmount
            ).onFailure { _snackbarMessage.value = "चैट संदेश भेजा नहीं जा सका" }
        }
    }

    fun deleteLiveChat(chatId: String) {
        viewModelScope.launch {
            interactions.deleteLiveChatMessage(chatId)
                .onSuccess { _snackbarMessage.value = "संदेश हटाया गया" }
        }
    }

    fun publishVideo(
        videoUri: Uri,
        thumbnailUri: Uri?,
        title: String,
        description: String,
        tags: List<String>,
        category: String,
        videoType: String,
        visibility: String,
        durationSec: Long,
        durationLabel: String,
        commentsEnabled: Boolean,
        ageRestricted: Boolean,
        onComplete: () -> Unit
    ) {
        val me = (channelState.value as? UiState.Success)?.data
        val channelName = me?.displayName?.ifBlank { "GLASYER क्रिएटर" } ?: "GLASYER क्रिएटर"
        val channelAvatar = me?.photoUrl.orEmpty()
        val videoId = GlasyerRepository.generateSortableVideoId()

        lastPublishRequest = PendingUploadRequest(
            videoUri = videoUri,
            thumbnailUri = thumbnailUri,
            title = title,
            description = description,
            tags = tags,
            category = category,
            videoType = videoType,
            visibility = visibility,
            durationSec = durationSec,
            durationLabel = durationLabel,
            commentsEnabled = commentsEnabled,
            ageRestricted = ageRestricted,
            onComplete = onComplete
        )

        activePublishJob?.cancel()
        activePublishJob = viewModelScope.launch {
            _uploadError.value = null
            _canRetryUpload.value = false
            _uploadProgress.value = UploadProgressState(
                stageLabel = "फ़ाइल जांची जा रही है...",
                fraction = 0.01f,
                videoStage = UploadSubStage.IN_PROGRESS,
                thumbnailStage = if (thumbnailUri != null) UploadSubStage.IDLE else UploadSubStage.SKIPPED,
                publishStage = UploadSubStage.IDLE
            )

            val uploadResult = storageRepo.uploadVideoAndThumbnail(
                videoId = videoId,
                videoUri = videoUri,
                thumbnailUri = thumbnailUri,
                onTaskCreated = { task -> activeStorageTask = task },
                onProgress = { progressState ->
                    _uploadProgress.value = progressState
                }
            )
            activeStorageTask = null

            val uploadedUrls = uploadResult.getOrElse { error ->
                _uploadProgress.value = null
                val msg = error.localizedMessage ?: "वीडियो अपलोड विफल रहा। कृपया पुनः प्रयास करें।"
                _uploadError.value = msg
                _canRetryUpload.value = true
                _snackbarMessage.value = msg
                return@launch
            }

            _uploadProgress.value = UploadProgressState(
                stageLabel = "Firestore में प्रकाशित हो रहा है...",
                fraction = 0.96f,
                videoStage = UploadSubStage.COMPLETED,
                thumbnailStage = if (uploadedUrls.thumbnailUrl.isNotBlank()) UploadSubStage.COMPLETED else UploadSubStage.SKIPPED,
                publishStage = UploadSubStage.IN_PROGRESS
            )

            val enriched = VideoItem(
                id = videoId,
                videoId = videoId,
                ownerUid = currentUserId,
                channelName = channelName,
                channelAvatar = channelAvatar,
                title = title,
                description = description,
                tags = tags,
                category = category,
                videoType = videoType,
                visibility = visibility,
                videoUrl = uploadedUrls.videoUrl,
                thumbnailUrl = uploadedUrls.thumbnailUrl,
                durationLabel = durationLabel,
                duration = durationSec,
                viewerCount = if (videoType == "live") 1L else 0L,
                commentsEnabled = commentsEnabled,
                ageRestricted = ageRestricted
            )

            repo.createVideo(videoId, enriched)
                .onSuccess { newId ->
                    _uploadProgress.value = UploadProgressState(
                        stageLabel = "प्रकाशित!",
                        fraction = 1.0f,
                        videoStage = UploadSubStage.COMPLETED,
                        thumbnailStage = if (uploadedUrls.thumbnailUrl.isNotBlank()) UploadSubStage.COMPLETED else UploadSubStage.SKIPPED,
                        publishStage = UploadSubStage.COMPLETED
                    )
                    if (enriched.visibility == "public") {
                        interactions.createNotification(
                            recipientId = currentUserId,
                            actorName = enriched.channelName,
                            type = if (enriched.videoType == "live") "live" else "upload",
                            title = if (enriched.videoType == "short") "नया शॉर्ट प्रकाशित" else "नया वीडियो प्रकाशित",
                            body = enriched.title,
                            targetId = newId
                        )
                    }
                    lastPublishRequest = null
                    _uploadProgress.value = null
                    _uploadError.value = null
                    _canRetryUpload.value = false
                    _snackbarMessage.value = "आपका कंटेंट सफलतापूर्वक प्रकाशित हुआ!"
                    onComplete()
                }
                .onFailure { error ->
                    _uploadProgress.value = null
                    val msg = mapFirestoreErrorHindi(error)
                    _uploadError.value = msg
                    _canRetryUpload.value = true
                    _snackbarMessage.value = msg
                }
        }
    }

    fun updateVideoMetadata(
        videoId: String,
        title: String,
        description: String,
        category: String,
        visibility: String,
        commentsEnabled: Boolean,
        ageRestricted: Boolean
    ) {
        viewModelScope.launch {
            repo.updateVideoMetadata(videoId, title, description, category, visibility, commentsEnabled, ageRestricted)
                .onSuccess { _snackbarMessage.value = "वीडियो विवरण अपडेट हो गया" }
                .onFailure { _snackbarMessage.value = "अपडेट करने में त्रुटि" }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            repo.deleteVideo(videoId)
                .onSuccess {
                    if (_activeVideo.value?.resolvedId == videoId) closePlayer()
                    _snackbarMessage.value = "वीडियो हटाया गया"
                }
                .onFailure { _snackbarMessage.value = "वीडियो हटाने में त्रुटि" }
        }
    }

    fun createPost(text: String, imageUris: List<Uri>, pollOptions: List<String>, onDone: () -> Unit) {
        val me = (channelState.value as? UiState.Success)?.data
        val postId = "post_${System.currentTimeMillis()}_${(100..999).random()}"
        viewModelScope.launch {
            val uploadedUrls = if (imageUris.isNotEmpty()) {
                val res = storageRepo.uploadPostImages(postId, imageUris)
                if (res.isFailure) {
                    _snackbarMessage.value = res.exceptionOrNull()?.localizedMessage
                        ?: "पोस्ट की छवियाँ अपलोड नहीं हो सकीं।"
                    return@launch
                }
                res.getOrNull().orEmpty()
            } else {
                emptyList()
            }

            interactions.createCommunityPost(
                postId = postId,
                authorName = me?.displayName ?: "क्रिएटर",
                authorAvatar = me?.photoUrl.orEmpty(),
                text = text,
                imageUrls = uploadedUrls,
                pollOptions = pollOptions
            ).onSuccess {
                _snackbarMessage.value = "कम्युनिटी पोस्ट प्रकाशित हुई!"
                onDone()
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "पोस्ट प्रकाशित नहीं हो सकी"
            }
        }
    }

    fun voteOnPoll(post: CommunityPost, optionIndex: Int) {
        if (_votedPostIndices.value.containsKey(post.id)) {
            _snackbarMessage.value = "आप पहले ही इस पोल पर वोट कर चुके हैं"
            return
        }
        _votedPostIndices.value = _votedPostIndices.value + (post.id to optionIndex)
        viewModelScope.launch {
            interactions.voteOnPostPoll(post, optionIndex)
        }
    }

    fun likePost(postId: String) {
        viewModelScope.launch { interactions.likeCommunityPost(postId) }
    }

    fun observePostComments(postId: String): Flow<List<PostComment>> {
        return interactions.observePostComments(postId)
            .catch { error -> handleStreamError(error, "पोस्ट टिप्पणियाँ त्रुटि") { emit(emptyList()) } }
    }

    fun addPostComment(postId: String, text: String) {
        val me = (channelState.value as? UiState.Success)?.data
        viewModelScope.launch {
            interactions.addPostComment(postId, me?.displayName ?: "उपयोगकर्ता", text)
        }
    }

    fun clearHistory() {
        val items = historyState.value
        viewModelScope.launch {
            interactions.clearWatchHistory(items)
                .onSuccess { _snackbarMessage.value = "वॉच हिस्ट्री साफ़ की गई" }
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch { interactions.markNotificationRead(notifId) }
    }

    fun reportTarget(targetId: String, targetType: String, reason: String) {
        viewModelScope.launch {
            interactions.submitReport(targetId, targetType, reason)
                .onSuccess { _snackbarMessage.value = "रिपोर्ट दर्ज की गई। धन्यवाद!" }
        }
    }

    fun blockCreator(creatorId: String, creatorName: String) {
        if (creatorId == currentUserId) {
            _snackbarMessage.value = "आप स्वयं को ब्लॉक नहीं कर सकते"
            return
        }
        viewModelScope.launch {
            repo.blockUser(creatorId, creatorName)
                .onSuccess { _snackbarMessage.value = "$creatorName को ब्लॉक किया गया" }
        }
    }

    fun unblockCreator(creatorId: String) {
        viewModelScope.launch {
            repo.unblockUser(creatorId)
                .onSuccess { _snackbarMessage.value = "उपयोगकर्ता अनब्लॉक किया गया" }
        }
    }

    fun deleteAccount(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            repo.deleteMyAccountDoc()
                .onSuccess { onSignedOut() }
                .onFailure { _snackbarMessage.value = "खाता हटाने में त्रुटि हुई" }
        }
    }

    private fun isLocalUri(value: String): Boolean {
        return value.startsWith("content://") || value.startsWith("file://")
    }

    private suspend inline fun handleStreamError(
        error: Throwable,
        fallbackMessage: String,
        crossinline emitFallback: suspend (String) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Stream error: ${rootError.message}", rootError)
        emitFallback(rootError.message ?: fallbackMessage)
    }

    private companion object {
        const val TAG = "GlasyerVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
