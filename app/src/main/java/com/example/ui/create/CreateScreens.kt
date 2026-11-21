package com.example.ui.create

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.GlasyerStorageRepository
import com.example.model.GlasyerCategories
import com.example.model.UploadProgressState
import com.example.ui.theme.GlasyerLiveCoral
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CREATE_LOG_TAG = "GlasyerCreateScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChoiceSheet(
    onUploadVideo: () -> Unit,
    onCreateShort: () -> Unit,
    onCreatePost: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = "बनाएं",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            CreateOptionRow(
                icon = Icons.Filled.VideoLibrary,
                title = "वीडियो अपलोड करें",
                subtitle = "गैलरी या फ़ाइलों से लंबा वीडियो या लाइव स्ट्रीम जोड़ें",
                onClick = onUploadVideo,
                tag = "create_upload_video"
            )
            CreateOptionRow(
                icon = Icons.Filled.Videocam,
                title = "शॉर्ट बनाएं",
                subtitle = "60 सेकंड तक के वर्टिकल वीडियो अपने आप शॉर्ट बन जाते हैं",
                onClick = onCreateShort,
                tag = "create_short"
            )
            CreateOptionRow(
                icon = Icons.Filled.PostAdd,
                title = "पोस्ट",
                subtitle = "टेक्स्ट, फ़ोटो (10 तक) या कम्युनिटी पोल साझा करें",
                onClick = onCreatePost,
                tag = "create_post"
            )
        }
    }
}

@Composable
private fun CreateOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadVideoScreen(
    initialMode: String, // "video" | "short"
    uploadProgress: UploadProgressState?,
    uploadError: String?,
    canRetry: Boolean = false,
    onClearError: () -> Unit = {},
    onCancelUpload: () -> Unit = {},
    onRetryUpload: () -> Unit = {},
    onPublish: (
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
        ageRestricted: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var tagsInput by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("सब") }
    var visibility by rememberSaveable { mutableStateOf("public") }
    var commentsEnabled by rememberSaveable { mutableStateOf(true) }
    var ageRestricted by rememberSaveable { mutableStateOf(false) }
    var isLiveMode by rememberSaveable { mutableStateOf(false) }

    var videoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var thumbnailUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var videoDisplayName by rememberSaveable { mutableStateOf("") }
    var videoMimeType by rememberSaveable { mutableStateOf("video/mp4") }
    var videoSizeBytes by rememberSaveable { mutableLongStateOf(0L) }
    var durationSec by rememberSaveable { mutableLongStateOf(0L) }
    var isVertical by rememberSaveable { mutableStateOf(initialMode == "short") }

    var frameCaptureAttempt by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var emptyPickerNotice by remember { mutableStateOf<String?>(null) }
    var manualStepFocus by remember { mutableIntStateOf(-1) }

    val videoUri = remember(videoUriString) { videoUriString?.let { Uri.parse(it) } }
    val thumbnailUri = remember(thumbnailUriString) { thumbnailUriString?.let { Uri.parse(it) } }

    // Section vertical offsets for smooth scroll-to-step navigation
    val sectionOffsets = remember { mutableStateListOf(0, 0, 0, 0) }

    val handlePickedVideo: (Uri?, String) -> Unit = { uri, source ->
        if (uri == null) {
            Log.w(
                CREATE_LOG_TAG,
                "Video picker [$source] returned null URI (cancelled or no media available)."
            )
            emptyPickerNotice = if (source == "PhotoPicker") {
                "गैलरी में वीडियो नहीं दिखा या चयन रद्द किया गया। डाउनलोड्स, SD कार्ड या अन्य फ़ोल्डर से वीडियो चुनने के लिए 'फ़ाइलों से वीडियो चुनें' का उपयोग करें।"
            } else {
                "फ़ाइल चयन रद्द किया गया। कृपया अपलोड करने के लिए एक वीडियो फ़ाइल चुनें।"
            }
        } else {
            val inspected = inspectSelectedVideo(context, uri, source)
            inspected.onSuccess { meta ->
                onClearError()
                errorMsg = null
                emptyPickerNotice = null
                videoUriString = meta.uri.toString()
                videoDisplayName = meta.displayName
                videoMimeType = meta.mimeType
                videoSizeBytes = meta.sizeBytes
                durationSec = meta.durationSec
                isVertical = meta.isVertical
                if (meta.autoThumbUri != null) {
                    thumbnailUriString = meta.autoThumbUri.toString()
                }
                manualStepFocus = 1
            }.onFailure { err ->
                errorMsg = err.localizedMessage ?: "चयनित वीडियो पढ़ा नहीं जा सका। कृपया दूसरी फ़ाइल चुनें।"
            }
        }
    }

    val handlePickedThumbnail: (Uri?, String) -> Unit = { uri, source ->
        if (uri == null) {
            Log.d(CREATE_LOG_TAG, "Thumbnail picker [$source] cancelled or returned null.")
        } else {
            tryTakePersistableReadPermission(context, uri)
            val validation = GlasyerStorageRepository(context).validateImageUri(uri)
            validation.onSuccess {
                onClearError()
                errorMsg = null
                thumbnailUriString = uri.toString()
            }.onFailure { err ->
                errorMsg = err.localizedMessage ?: "थंबनेल छवि पढ़ी नहीं जा सकी।"
            }
        }
    }

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedVideo(uri, "PhotoPicker") }

    val filesVideoPicker = rememberLauncherForActivityResult(
        contract = OpenVideoDocumentContract()
    ) { uri -> handlePickedVideo(uri, "DocumentsUI") }

    val galleryThumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedThumbnail(uri, "PhotoPickerImage") }

    val filesThumbPicker = rememberLauncherForActivityResult(
        contract = OpenImageDocumentContract()
    ) { uri -> handlePickedThumbnail(uri, "DocumentsUIImage") }

    val detectedType = when {
        isLiveMode -> "live"
        durationSec in 1L..60L && isVertical -> "short"
        initialMode == "short" && durationSec <= 60L -> "short"
        else -> "video"
    }

    val parsedTags = remember(tagsInput) {
        tagsInput.split(",", " ")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(10)
    }

    val stepCompleted = remember(videoUri, title, thumbnailUri) {
        listOf(
            videoUri != null,
            title.trim().isNotEmpty(),
            thumbnailUri != null,
            videoUri != null && title.trim().isNotEmpty()
        )
    }

    val autoActiveStep = when {
        videoUri == null -> 0
        title.trim().isEmpty() -> 1
        thumbnailUri == null -> 2
        else -> 3
    }
    val activeStep = if (manualStepFocus in 0..3) manualStepFocus else autoActiveStep

    LaunchedEffect(uploadProgress) {
        if (uploadProgress != null) {
            manualStepFocus = 3
        }
    }

    val fieldColors = studioTextFieldColors()
    val isUploading = uploadProgress != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Premium GLASYER Studio Header
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onBack,
                            enabled = !isUploading,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("upload_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "वापस",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("GLAS")
                                        }
                                        withStyle(
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("YER")
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = "स्टूडियो",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (detectedType == "short") "शॉर्ट प्रकाशित करें" else "वीडियो अपलोड करें",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    StudioStepIndicator(
                        activeStep = activeStep,
                        stepCompleted = stepCompleted,
                        reducedMotion = reducedMotion,
                        onStepClick = { targetStep ->
                            manualStepFocus = targetStep
                            val targetY = sectionOffsets.getOrElse(targetStep) { 0 }
                            scope.launch {
                                if (reducedMotion) {
                                    scrollState.scrollTo(targetY)
                                } else {
                                    scrollState.animateScrollTo(targetY)
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Scrollable 4-section Creator Workspace
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // SECTION 01 – वीडियो चुनें
                StudioSectionCard(
                    sectionNumber = "01",
                    sectionTitle = "वीडियो चुनें",
                    sectionSubtitle = "फ़ोन गैलरी या फ़ाइल ब्राउज़र से वीडियो जोड़ें",
                    isCompleted = stepCompleted[0],
                    isActive = activeStep == 0,
                    testTagName = "section_01_card",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        sectionOffsets[0] = coords.positionInParent().y.roundToInt()
                    }
                ) {
                    if (videoUri == null) {
                        VideoSelectionDropzone(
                            enabled = !isUploading,
                            emptyPickerNotice = emptyPickerNotice,
                            onPickFromGallery = {
                                emptyPickerNotice = null
                                galleryVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onPickFromFiles = {
                                emptyPickerNotice = null
                                filesVideoPicker.launch(arrayOf("video/*"))
                            }
                        )
                    } else {
                        SelectedVideoPreviewCard(
                            videoName = videoDisplayName.ifBlank { "चयनित वीडियो" },
                            mimeType = videoMimeType,
                            durationSec = durationSec,
                            sizeBytes = videoSizeBytes,
                            previewThumbUri = thumbnailUri,
                            isShortDetected = detectedType == "short",
                            enabled = !isUploading,
                            onReplaceFromGallery = {
                                galleryVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onReplaceFromFiles = {
                                filesVideoPicker.launch(arrayOf("video/*"))
                            },
                            onRemove = {
                                videoUriString = null
                                thumbnailUriString = null
                                videoDisplayName = ""
                                videoSizeBytes = 0L
                                durationSec = 0L
                                isVertical = initialMode == "short"
                                errorMsg = null
                                onClearError()
                                manualStepFocus = 0
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // SECTION 02 – वीडियो की जानकारी
                StudioSectionCard(
                    sectionNumber = "02",
                    sectionTitle = "वीडियो की जानकारी",
                    sectionSubtitle = "शीर्षक, विवरण और खोज टैग जोड़ें",
                    isCompleted = stepCompleted[1],
                    isActive = activeStep == 1,
                    testTagName = "section_02_card",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        sectionOffsets[1] = coords.positionInParent().y.roundToInt()
                    }
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(150)
                            errorMsg = null
                        },
                        enabled = !isUploading,
                        label = { Text("शीर्षक *") },
                        placeholder = { Text("दर्शकों का ध्यान खींचने वाला शीर्षक लिखें") },
                        supportingText = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("स्पष्ट और खोज-योग्य शीर्षक चुनें")
                                Text("${title.length}/150")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(2000) },
                        enabled = !isUploading,
                        label = { Text("विवरण और #हैशटैग") },
                        placeholder = { Text("वीडियो के बारे में विस्तार से लिखें...") },
                        supportingText = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("लिंक, समय-सूची या #हैशटैग जोड़ें")
                                Text("${description.length}/2000")
                            }
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_description_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it.take(120) },
                        enabled = !isUploading,
                        label = { Text("टैग (कॉमा या स्पेस से अलग करें)") },
                        placeholder = { Text("उदा. हिंदी, व्लॉग, टेक") },
                        supportingText = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${parsedTags.size}/10 टैग")
                                Text("${tagsInput.length}/120")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_tags_input")
                    )

                    if (parsedTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parsedTags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // SECTION 03 – थंबनेल और कैटेगरी
                StudioSectionCard(
                    sectionNumber = "03",
                    sectionTitle = "थंबनेल और कैटेगरी",
                    sectionSubtitle = "कवर फ़ोटो, फ़्रेम कैप्चर और श्रेणी चुनें",
                    isCompleted = stepCompleted[2],
                    isActive = activeStep == 2,
                    testTagName = "section_03_card",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        sectionOffsets[2] = coords.positionInParent().y.roundToInt()
                    }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        if (thumbnailUri != null) {
                            AsyncImage(
                                model = thumbnailUri,
                                contentDescription = "चयनित थंबनेल",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .testTag("thumbnail_preview_image")
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "थंबनेल प्रीव्यू यहाँ दिखेगा",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                galleryThumbPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isUploading,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("pick_custom_thumbnail_button")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("कस्टम थंबनेल")
                        }
                        OutlinedButton(
                            onClick = {
                                filesThumbPicker.launch(arrayOf("image/*"))
                            },
                            enabled = !isUploading,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("pick_file_thumbnail_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("फ़ाइल से")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val activeVideoUri = videoUri ?: return@OutlinedButton
                            val fractions = listOf(0.15f, 0.35f, 0.60f, 0.80f)
                            val frac = fractions[frameCaptureAttempt % fractions.size]
                            frameCaptureAttempt++
                            val captured = captureVideoFrame(context, activeVideoUri, frac)
                            if (captured != null) {
                                thumbnailUriString = captured.toString()
                                errorMsg = null
                            } else {
                                errorMsg = "वीडियो से थंबनेल कैप्चर नहीं हो सका। कृपया कस्टम थंबनेल चुनें।"
                            }
                        },
                        enabled = !isUploading && videoUri != null,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (videoUri != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("capture_thumbnail_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("वीडियो से थंबनेल कैप्चर करें")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "कैटेगरी चुनें",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        GlasyerCategories.all.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (!isUploading) selectedCategory = cat },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = !isUploading,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shorts_detection_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (detectedType == "short") Icons.Filled.AutoAwesome else Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    videoUri == null -> "ऑटो-डिटेक्शन: 60 सेकंड तक के वर्टिकल वीडियो स्वतः GLASYER शॉर्ट्स में प्रकाशित होते हैं।"
                                    detectedType == "short" -> "ऑटो-डिटेक्शन सक्रिय: यह वीडियो ${formatDurationSec(durationSec)} और वर्टिकल है — इसे 'शॉर्ट्स' के रूप में प्रकाशित किया जाएगा।"
                                    detectedType == "live" -> "लाइव मोड सक्रिय: दर्शक रियल-टाइम लाइव चैट का उपयोग कर सकेंगे।"
                                    else -> "फ़ॉर्मेट पहचान: मानक वीडियो (${formatDurationSec(durationSec)}) के रूप में प्रकाशित होगा।"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // SECTION 04 – पब्लिश सेटिंग
                StudioSectionCard(
                    sectionNumber = "04",
                    sectionTitle = "पब्लिश सेटिंग",
                    sectionSubtitle = "दृश्यता, टिप्पणियाँ और अंतिम समीक्षा",
                    isCompleted = stepCompleted[3],
                    isActive = activeStep == 3,
                    testTagName = "section_04_card",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        sectionOffsets[3] = coords.positionInParent().y.roundToInt()
                    }
                ) {
                    Text(
                        text = "दृश्यता (Visibility)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    VisibilityOptionSelector(
                        selectedVisibility = visibility,
                        enabled = !isUploading,
                        onSelect = { visibility = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioSettingSwitchRow(
                            title = "टिप्पणियाँ चालू रखें",
                            subtitle = "दर्शक आपके वीडियो पर टिप्पणी और चर्चा कर सकते हैं",
                            checked = commentsEnabled,
                            enabled = !isUploading,
                            onCheckedChange = { commentsEnabled = it }
                        )
                        StudioSettingSwitchRow(
                            title = "18+ आयु-प्रतिबंधित सामग्री",
                            subtitle = "केवल वयस्क दर्शकों के लिए उपयुक्त चिह्नित करें",
                            checked = ageRestricted,
                            enabled = !isUploading,
                            onCheckedChange = { ageRestricted = it }
                        )
                        StudioSettingSwitchRow(
                            title = "लाइव स्ट्रीम के रूप में चिह्नित करें",
                            subtitle = "लाइव बैज और रियल-टाइम चैट सक्रिय करें",
                            checked = isLiveMode,
                            enabled = !isUploading,
                            onCheckedChange = { isLiveMode = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    UploadSummaryCard(
                        videoName = videoDisplayName.ifBlank { null },
                        durationSec = durationSec,
                        sizeBytes = videoSizeBytes,
                        detectedType = detectedType,
                        category = selectedCategory,
                        visibility = visibility,
                        hasThumbnail = thumbnailUri != null
                    )

                    if (uploadProgress != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        UploadProgressCard(
                            progress = uploadProgress,
                            reducedMotion = reducedMotion,
                            onCancelUpload = onCancelUpload
                        )
                    }

                    val bottomError = errorMsg ?: uploadError
                    if (bottomError != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        UploadErrorRetryBanner(
                            message = bottomError,
                            canRetry = canRetry && errorMsg == null,
                            onRetry = onRetryUpload
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val pickedVideo = videoUri
                            if (pickedVideo == null) {
                                errorMsg = "कृपया गैलरी से एक वास्तविक वीडियो चुनें।"
                                return@Button
                            }
                            if (title.trim().isEmpty()) {
                                errorMsg = "कृपया वीडियो का शीर्षक लिखें।"
                                return@Button
                            }
                            errorMsg = null
                            onClearError()
                            val durLabel = if (detectedType == "live") "LIVE" else formatDurationSec(durationSec)
                            onPublish(
                                pickedVideo,
                                thumbnailUri,
                                title.trim(),
                                description.trim(),
                                parsedTags,
                                selectedCategory,
                                detectedType,
                                visibility,
                                durationSec,
                                durLabel,
                                commentsEnabled,
                                ageRestricted
                            )
                        },
                        enabled = !isUploading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("publish_video_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveMode) Icons.Filled.LiveTv else Icons.Filled.Publish,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUploading) "अपलोड हो रहा है..." else "प्रकाशित करें",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CreatePostScreen(
    onPublishPost: (text: String, imageUris: List<Uri>, pollOptions: List<String>) -> Unit,
    onBack: () -> Unit
) {
    var postText by remember { mutableStateOf("") }
    val selectedImages = remember { mutableStateListOf<Uri>() }
    val pollOptions = remember { mutableStateListOf<String>() }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        uris.take(10 - selectedImages.size).forEach { uri ->
            selectedImages.add(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस")
            }
            Text("कम्युनिटी पोस्ट बनाएं", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = postText,
            onValueChange = { postText = it.take(2000) },
            label = { Text("अपने दर्शकों के साथ कुछ साझा करें...") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(16.dp),
            colors = studioTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("post_text_input")
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    multiImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = selectedImages.size < 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("फ़ोटो जोड़ें (${selectedImages.size}/10)")
            }
            OutlinedButton(
                onClick = {
                    if (pollOptions.isEmpty()) {
                        pollOptions.add("")
                        pollOptions.add("")
                    } else if (pollOptions.size < 6) {
                        pollOptions.add("")
                    }
                },
                enabled = pollOptions.size < 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (pollOptions.isEmpty()) "पोल जोड़ें" else "विकल्प जोड़ें")
            }
        }

        if (selectedImages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedImages.forEach { img ->
                    AsyncImage(
                        model = img,
                        contentDescription = "पोस्ट छवि",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        if (pollOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("पोल विकल्प (अधिकतम 6)", style = MaterialTheme.typography.labelLarge)
            pollOptions.forEachIndexed { idx, opt ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = opt,
                        onValueChange = { pollOptions[idx] = it.take(80) },
                        label = { Text("विकल्प ${idx + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = studioTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pollOptions.removeAt(idx) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "विकल्प हटाएं")
                    }
                }
            }
        }

        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg!!, color = GlasyerLiveCoral, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (postText.trim().isEmpty()) {
                    errorMsg = "कृपया पोस्ट का टेक्स्ट लिखें।"
                    return@Button
                }
                errorMsg = null
                onPublishPost(postText.trim(), selectedImages.toList(), pollOptions.toList())
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("publish_post_button")
        ) {
            Text("पोस्ट प्रकाशित करें")
        }
    }
}
