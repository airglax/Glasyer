package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.GlasyerRepository
import com.example.data.GlasyerStorageRepository
import com.example.data.mapFirestoreErrorHindi
import com.example.model.UiState
import com.example.model.UploadProgressState
import com.example.model.UploadSubStage
import com.example.model.UserChannel
import com.example.model.VideoItem
import com.example.ui.components.VideoCard
import com.example.ui.create.OpenVideoDocumentContract
import com.example.ui.create.SelectedVideoPreviewCard
import com.example.ui.create.UploadVideoScreen
import com.example.ui.create.formatDurationSec
import com.example.ui.create.formatFileSize
import com.example.ui.create.formatTransferSpeed
import com.example.ui.home.HomeFeedScreen
import com.example.ui.shorts.ShortsScreen
import com.example.ui.theme.GlasyerTheme
import com.example.ui.you.CreatorStudioScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class GlasyerPlatformTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    @Test
    fun emptyHomeFeed_displaysHindiEmptyStateAndUploadButton_withNoDemoVideos() {
        var createClicked = false
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                HomeFeedScreen(
                    videosState = UiState.Success(emptyList()),
                    channelMap = emptyMap(),
                    selectedCategory = "सब",
                    restrictedMode = false,
                    blockedIds = emptySet(),
                    onSelectCategory = {},
                    onOpenVideo = {},
                    onOpenShorts = {},
                    onOpenChannel = {},
                    onSaveWatchLater = {},
                    onShareVideo = {},
                    onReportVideo = {},
                    onCreateClick = { createClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("अभी कोई वीडियो उपलब्ध नहीं है।").assertIsDisplayed()
        composeRule.onNodeWithText("पहला वीडियो अपलोड करें").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_state_action_button").performClick()
        assertTrue(createClicked)
    }

    @Test
    fun emptyShortsFeed_displaysHindiEmptyStateAndUploadButton() {
        var uploadShortClicked = false
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                ShortsScreen(
                    shorts = emptyList(),
                    channelMap = emptyMap(),
                    likes = emptyList(),
                    subscriptions = emptyList(),
                    currentUserId = "uid_1",
                    observeComments = { kotlinx.coroutines.flow.flowOf(emptyList()) },
                    onLikeShort = {},
                    onShareShort = {},
                    onToggleSubscribe = {},
                    onAddComment = { _, _, _ -> },
                    onLikeComment = {},
                    onDeleteComment = {},
                    onTogglePinOrHeart = { _, _, _ -> },
                    onReportComment = {},
                    onCreateShortClick = { uploadShortClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("अभी कोई वीडियो उपलब्ध नहीं है।").assertIsDisplayed()
        composeRule.onNodeWithText("पहला वीडियो अपलोड करें").performClick()
        assertTrue(uploadShortClicked)
    }

    @Test
    fun videoCard_showsVerifiedBadgeOnlyWhenUserChannelVerifiedIsTrue() {
        val videoUnverified = VideoItem(
            id = "vid_1",
            videoId = "vid_1",
            ownerUid = "user_a",
            channelName = "Creator A",
            title = "Real Uploaded Video A",
            viewsCount = 42L
        )
        val videoVerified = VideoItem(
            id = "vid_2",
            videoId = "vid_2",
            ownerUid = "user_b",
            channelName = "Creator B",
            title = "Real Uploaded Video B",
            viewsCount = 1200L
        )

        composeRule.setContent {
            GlasyerTheme(themeMode = "light") {
                androidx.compose.foundation.layout.Column {
                    VideoCard(
                        video = videoUnverified,
                        channel = UserChannel(userId = "user_a", displayName = "Creator A", verified = false),
                        onClick = {},
                        onChannelClick = {},
                        onMenuClick = {}
                    )
                    VideoCard(
                        video = videoVerified,
                        channel = UserChannel(userId = "user_b", displayName = "Creator B", verified = true),
                        onClick = {},
                        onChannelClick = {},
                        onMenuClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Real Uploaded Video A").assertIsDisplayed()
        composeRule.onNodeWithText("Real Uploaded Video B").assertIsDisplayed()
        composeRule.onNodeWithTag("verified_badge_vid_1", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("verified_badge_vid_2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun uploadScreen_rendersFourStepIndicator_andBothGalleryAndFilesPickers() {
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                UploadVideoScreen(
                    initialMode = "video",
                    uploadProgress = null,
                    uploadError = null,
                    onPublish = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("studio_step_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_step_1").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_step_2").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_step_3").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_step_4").assertIsDisplayed()

        composeRule.onNodeWithTag("section_01_card").assertIsDisplayed()
        composeRule.onNodeWithText("गैलरी से वीडियो चुनें").assertIsDisplayed()
        composeRule.onNodeWithText("फ़ाइलों से वीडियो चुनें").assertIsDisplayed()

        composeRule.onNodeWithTag("section_02_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("upload_title_input").performScrollTo().performTextInput("मेरा नया वीडियो")
        composeRule.onNodeWithText("15/150").assertIsDisplayed()

        composeRule.onNodeWithTag("section_03_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("section_04_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun uploadScreen_blocksPublishWithoutRealVideo_andRendersDetailedProgressCancelAndRetry() {
        var publishCalled = false
        var cancelCalled = false
        var retryCalled = false
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                UploadVideoScreen(
                    initialMode = "video",
                    uploadProgress = UploadProgressState(
                        stageLabel = "वीडियो अपलोड हो रहा है...",
                        fraction = 0.45f,
                        bytesUploaded = 4_500_000L,
                        totalBytes = 10_000_000L,
                        speedBytesPerSec = 1_500_000L,
                        videoStage = UploadSubStage.IN_PROGRESS,
                        thumbnailStage = UploadSubStage.IDLE,
                        publishStage = UploadSubStage.IDLE
                    ),
                    uploadError = "Firebase Storage कॉन्फ़िगर नहीं है।",
                    canRetry = true,
                    onCancelUpload = { cancelCalled = true },
                    onRetryUpload = { retryCalled = true },
                    onPublish = { _, _, _, _, _, _, _, _, _, _, _, _ -> publishCalled = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("upload_progress_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("वीडियो अपलोड हो रहा है... 45%").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_upload_button").performScrollTo().performClick()
        assertTrue(cancelCalled)

        composeRule.onNodeWithText("Firebase Storage कॉन्फ़िगर नहीं है।").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retry_upload_button").performScrollTo().performClick()
        assertTrue(retryCalled)
        assertFalse(publishCalled)
    }

    @Test
    fun uploadScreen_withoutVideoPicked_showsValidationErrorAndDoesNotPublish() {
        var publishCalled = false
        composeRule.setContent {
            GlasyerTheme(themeMode = "light") {
                UploadVideoScreen(
                    initialMode = "video",
                    uploadProgress = null,
                    uploadError = null,
                    onPublish = { _, _, _, _, _, _, _, _, _, _, _, _ -> publishCalled = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("publish_video_button").performScrollTo().performClick()
        composeRule.onNodeWithText("कृपया गैलरी से एक वास्तविक वीडियो चुनें।").performScrollTo().assertIsDisplayed()
        assertFalse(publishCalled)
    }

    @Test
    fun selectedVideoPreviewCard_displaysFileMetadataAndReplaceRemoveActions() {
        var removeCalled = false
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                SelectedVideoPreviewCard(
                    videoName = "mountain_vlog.mp4",
                    mimeType = "video/mp4",
                    durationSec = 95L,
                    sizeBytes = 12_582_912L,
                    previewThumbUri = null,
                    isShortDetected = false,
                    enabled = true,
                    onReplaceFromGallery = {},
                    onReplaceFromFiles = {},
                    onRemove = { removeCalled = true }
                )
            }
        }

        composeRule.onNodeWithText("mountain_vlog.mp4").assertIsDisplayed()
        composeRule.onNodeWithText("अवधि: 01:35 • आकार: 12.0 MB • MP4").assertIsDisplayed()
        composeRule.onNodeWithTag("replace_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("remove_video_button").performClick()
        assertTrue(removeCalled)
    }

    @Test
    fun openVideoDocumentContract_setsOpenableVideoMimeTypesAndReadPermission() {
        val contract = OpenVideoDocumentContract()
        val intent = contract.createIntent(context, arrayOf("video/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        val mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty()
        assertTrue(mimeTypes.contains("video/mp4"))
        assertTrue(mimeTypes.contains("video/webm"))
        assertTrue(mimeTypes.contains("video/quicktime"))
        assertTrue(mimeTypes.contains("video/3gpp"))
    }

    @Test
    fun mediaFormattingHelpers_formatDurationSizeAndSpeedCleanly() {
        assertEquals("01:05", formatDurationSec(65L))
        assertEquals("1:01:05", formatDurationSec(3665L))
        assertEquals("512 KB", formatFileSize(512L * 1024L))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("3.0 MB/s", formatTransferSpeed(3L * 1024L * 1024L))
    }

    @Test
    fun creatorStudio_emptyVideos_showsNoFakeChartData() {
        composeRule.setContent {
            GlasyerTheme(themeMode = "dark") {
                CreatorStudioScreen(
                    myChannel = UserChannel(userId = "uid_1", displayName = "Me", subscriberCount = 0L),
                    myVideos = emptyList(),
                    onBack = {},
                    onEditVideo = { _, _, _, _, _, _, _ -> },
                    onDeleteVideo = {}
                )
            }
        }

        composeRule.onNodeWithText("आँकड़े देखने के लिए पहले कोई वास्तविक वीडियो अपलोड करें।").assertIsDisplayed()
        composeRule.onNodeWithText("आपके वीडियो (0)").assertIsDisplayed()
    }

    @Test
    fun sortableVideoId_sortsNewestFirst() {
        val older = GlasyerRepository.generateSortableVideoId()
        Thread.sleep(3)
        val newer = GlasyerRepository.generateSortableVideoId()
        assertTrue("Newer video ID should sort before older ID", newer < older)
    }

    @Test
    fun storageAndFirestoreErrors_mapToClearHindiMessages() {
        val storageRepo = GlasyerStorageRepository(
            context = context,
            storage = FirebaseStorage.getInstance(),
            auth = FirebaseAuth.getInstance()
        )

        val networkErr = storageRepo.mapStorageErrorToHindi(FirebaseNetworkException("no network"))
        assertTrue(networkErr.contains("इंटरनेट"))

        val permErr = mapFirestoreErrorHindi(
            FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
        assertTrue(permErr.contains("अनुमति अस्वीकृत"))

        val authErr = mapFirestoreErrorHindi(
            FirebaseFirestoreException("Unauth", FirebaseFirestoreException.Code.UNAUTHENTICATED)
        )
        assertTrue(authErr.contains("प्रमाणीकरण"))
    }

    @Test
    fun storageInspection_validatesMimeSizeAndMissingFileSafely() {
        val storageRepo = GlasyerStorageRepository(
            context = context,
            storage = FirebaseStorage.getInstance(),
            auth = FirebaseAuth.getInstance()
        )

        val textFile = File(context.cacheDir, "not_a_video.txt").apply {
            writeText("hello")
        }
        val badMimeResult = storageRepo.validateVideoUri(Uri.fromFile(textFile))
        assertTrue(badMimeResult.isFailure)
        assertTrue(badMimeResult.exceptionOrNull()?.message?.contains("असमर्थित वीडियो फ़ॉर्मेट") == true)

        val missingFile = File(context.cacheDir, "missing_video_file.mp4").apply {
            if (exists()) delete()
        }
        val missingResult = storageRepo.inspectContentUri(Uri.fromFile(missingFile))
        assertTrue(missingResult.isFailure)
        assertTrue(missingResult.exceptionOrNull()?.message?.contains("उपलब्ध नहीं") == true)

        val validMp4 = File(context.cacheDir, "sample_clip.mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02))
        }
        val goodInfo = storageRepo.inspectContentUri(Uri.fromFile(validMp4))
        assertTrue(goodInfo.isSuccess)
        assertEquals("sample_clip.mp4", goodInfo.getOrNull()?.displayName)
        assertEquals("video/mp4", goodInfo.getOrNull()?.mimeType)
        assertEquals(3L, goodInfo.getOrNull()?.sizeBytes)
    }
}
