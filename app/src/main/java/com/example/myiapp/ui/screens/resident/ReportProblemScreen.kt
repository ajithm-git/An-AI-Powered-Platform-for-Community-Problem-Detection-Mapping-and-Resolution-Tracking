package com.example.myiapp.ui.screens.resident

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.ProblemDraft
import com.example.myiapp.data.model.YoloDetection
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ReportProblemScreen — 3-step AI-powered problem reporting
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Step 1: CAPTURE   — Take a photo or pick from gallery
 *  Step 2: ANALYZE   — YOLO + Gemini draft (title, category, priority)
 *  Step 3: REVIEW    — Edit the draft, add location, submit
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportProblemScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onProblemCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val problemRepository = remember { ProblemRepository() }

    // ─────────────────────────────────────────
    // State
    // ─────────────────────────────────────────
    var step by rememberSaveable { mutableIntStateOf(1) }   // 1=Capture, 2=Analyze, 3=Review

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var yoloDetections by remember { mutableStateOf<List<YoloDetection>>(emptyList()) }
    var yoloModelName by remember { mutableStateOf("") }
    var yoloInferenceMs by remember { mutableStateOf(0) }
    var aiSummary by remember { mutableStateOf("") }
    var aiRecommendedAction by remember { mutableStateOf("") }
    var aiSeverity by remember { mutableStateOf("Medium") }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Other") }
    var priority by rememberSaveable { mutableStateOf("Medium") }

    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf(
        "Lighting", "Plumbing", "Electrical", "Elevator",
        "Security", "Cleanliness", "Structural", "HVAC",
        "Parking", "Noise", "Other"
    )
    val priorities = listOf("Low", "Medium", "High", "Urgent")

    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun runAiAnalysis() {
        val base64 = imageBase64 ?: return
        isAnalyzing = true
        analysisError = null

        scope.launch {
            problemRepository.analyzeProblem(base64, location.takeIf { it.isNotBlank() }).fold(
                onSuccess = { response ->
                    if (response.success) {
                        // Capture YOLO info
                        response.yolo?.let { yolo ->
                            yoloDetections = yolo.detections
                            yoloModelName = yolo.modelLoaded
                            yoloInferenceMs = yolo.inferenceMs
                        }

                        // Auto-fill form from AI draft (user can edit later)
                        response.draft?.let { draft: ProblemDraft ->
                            title = draft.title
                            description = draft.description
                            category = draft.category.takeIf { categories.contains(it) } ?: "Other"
                            priority = draft.priority.takeIf { priorities.contains(it) } ?: "Medium"
                            aiSummary = draft.recommendedAction
                            aiSeverity = draft.severity
                        }

                        step = 3
                    } else {
                        analysisError = response.message ?: "AI analysis failed"
                        // Let user proceed manually anyway
                        step = 3
                    }
                    isAnalyzing = false
                },
                onFailure = { e ->
                    analysisError = e.message ?: "Network error during analysis"
                    isAnalyzing = false
                    // Still allow manual submission
                    step = 3
                }
            )
        }
    }

    // ─────────────────────────────────────────
    // Image processing
    // ─────────────────────────────────────────
    fun processUri(uri: Uri) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()

            if (bitmap != null) {
                // Resize to 1024px max side for efficient upload
                val maxSide = 1024
                val scale = if (bitmap.width > bitmap.height)
                    maxSide.toFloat() / bitmap.width
                else
                    maxSide.toFloat() / bitmap.height
                val resized = if (scale < 1f)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                else bitmap

                imageBitmap = resized
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
                imageBase64 = "data:image/jpeg;base64," +
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                // Reset analysis state
                yoloDetections = emptyList()
                aiSummary = ""
                analysisError = null

                // Move to analysis step
                step = 2
                runAiAnalysis()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Image processing failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────
    // Camera/gallery launchers
    // ─────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) processUri(photoUri!!)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) processUri(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "camera_capture.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val file = File(context.cacheDir, "camera_capture.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ─────────────────────────────────────────
    // Submit
    // ─────────────────────────────────────────
    fun submit() {
        when {
            title.trim().length < 5 -> {
                Toast.makeText(context, "Title must be at least 5 characters", Toast.LENGTH_SHORT).show()
                return
            }
            location.isBlank() -> {
                Toast.makeText(context, "Location is required", Toast.LENGTH_SHORT).show()
                return
            }
        }

        isSubmitting = true
        scope.launch {
            problemRepository.createProblem(
                title = title.trim(),
                category = category,
                location = location.trim(),
                description = description.trim(),
                priority = priority,
                beforeImage = imageBase64
            ).fold(
                onSuccess = { res ->
                    isSubmitting = false
                    val problemId = res.problem?.id
                    if (res.success && !problemId.isNullOrBlank()) {
                        Toast.makeText(context, "Report submitted!", Toast.LENGTH_SHORT).show()
                        onProblemCreated(problemId)
                    } else {
                        Toast.makeText(
                            context,
                            res.message ?: "Submission failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onFailure = { e ->
                    isSubmitting = false
                    Toast.makeText(
                        context,
                        e.message ?: "Submission failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            1 -> "Report a Problem"
                            2 -> "Analyzing…"
                            else -> "Review & Submit"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (step > 1 && !isAnalyzing) step -= 1 else onBack()
                        },
                        enabled = !isAnalyzing
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Step indicator
            StepDots(current = step, total = 3)

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "step_transition"
            ) { current ->
                when (current) {
                    1 -> CaptureStep(
                        imageBitmap = imageBitmap,
                        onCameraClick = { launchCamera() },
                        onGalleryClick = { galleryLauncher.launch("image/*") }
                    )
                    2 -> AnalyzingStep()
                    3 -> ReviewStep(
                        imageBitmap = imageBitmap,
                        yoloDetections = yoloDetections,
                        yoloModelName = yoloModelName,
                        yoloInferenceMs = yoloInferenceMs,
                        aiSummary = aiSummary,
                        aiRecommendedAction = aiRecommendedAction,
                        aiSeverity = aiSeverity,
                        analysisError = analysisError,
                        title = title,
                        onTitleChange = { title = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        location = location,
                        onLocationChange = { location = it },
                        category = category,
                        onCategoryChange = { category = it },
                        categoryExpanded = categoryExpanded,
                        onCategoryExpandedChange = { categoryExpanded = it },
                        categories = categories,
                        priority = priority,
                        onPriorityChange = { priority = it },
                        priorityExpanded = priorityExpanded,
                        onPriorityExpandedChange = { priorityExpanded = it },
                        priorities = priorities,
                        isSubmitting = isSubmitting,
                        onRetakePhoto = { step = 1 },
                        onSubmit = { submit() }
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// STEP 1 — CAPTURE
// ═════════════════════════════════════════════

@Composable
private fun CaptureStep(
    imageBitmap: Bitmap?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceWhite)
                .border(2.dp, BorderLight, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    imageBitmap.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .background(PrimaryGreenSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            null,
                            Modifier.size(40.dp),
                            tint = PrimaryGreen
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Capture or upload a photo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI will analyze it and pre-fill the report",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Camera, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Camera", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryGreen)
            ) {
                Icon(Icons.Default.Collections, null, Modifier.size(20.dp), tint = PrimaryGreen)
                Spacer(Modifier.width(8.dp))
                Text("Gallery", color = PrimaryGreen, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Tips
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PrimaryGreenSoft
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lightbulb,
                        null,
                        tint = PrimaryGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "For best results",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreenDark
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Photograph the issue clearly\n" +
                            "• Get the full area in frame\n" +
                            "• Ensure good lighting",
                    fontSize = 12.sp,
                    color = PrimaryGreenDark.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// STEP 2 — ANALYZING
// ═════════════════════════════════════════════

@Composable
private fun AnalyzingStep() {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(140.dp)
                .background(PrimaryGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Analyzing your photo…",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "YOLO is detecting objects\nGemini is drafting your report",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(32.dp))

        CircularProgressIndicator(color = PrimaryGreen)
    }
}

// ═════════════════════════════════════════════
// STEP 3 — REVIEW
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewStep(
    imageBitmap: Bitmap?,
    yoloDetections: List<YoloDetection>,
    yoloModelName: String,
    yoloInferenceMs: Int,
    aiSummary: String,
    aiRecommendedAction: String,
    aiSeverity: String,
    analysisError: String?,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    categoryExpanded: Boolean,
    onCategoryExpandedChange: (Boolean) -> Unit,
    categories: List<String>,
    priority: String,
    onPriorityChange: (String) -> Unit,
    priorityExpanded: Boolean,
    onPriorityExpandedChange: (Boolean) -> Unit,
    priorities: List<String>,
    isSubmitting: Boolean,
    onRetakePhoto: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Photo thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(SurfaceElevated)
        ) {
            if (imageBitmap != null) {
                Image(
                    imageBitmap.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    )
                )
            }

            // Retake button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable { onRetakePhoto() },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.9f)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp), tint = TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Retake", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            // AI badge
            if (yoloDetections.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.9f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "AI analyzed in ${yoloInferenceMs}ms",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreenDark
                        )
                    }
                }
            }
        }

        Column(Modifier.padding(20.dp)) {
            // Error (if AI failed)
            analysisError?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = WarningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI unavailable: please fill in the details manually",
                            fontSize = 12.sp,
                            color = WarningAmber
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // YOLO detections
            if (yoloDetections.isNotEmpty()) {
                Text(
                    "DETECTED OBJECTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(yoloDetections) { det ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryGreenSoft
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    det.className.replace("_", " "),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreenDark
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    det.confidencePercent(),
                                    fontSize = 10.sp,
                                    color = PrimaryGreenDark.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // AI summary
            if (aiSummary.isNotBlank() || aiRecommendedAction.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryGreenSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = PrimaryGreenDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI DIAGNOSTIC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = PrimaryGreenDark,
                                letterSpacing = 1.sp
                            )
                        }
                        if (aiRecommendedAction.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                aiRecommendedAction,
                                fontSize = 13.sp,
                                color = PrimaryGreenDark,
                                lineHeight = 18.sp
                            )
                        }
                        if (aiSeverity.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Severity: $aiSeverity",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreenDark.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Title
            ReportField(
                value = title,
                onValueChange = onTitleChange,
                label = "Title",
                placeholder = "Brief summary of the issue"
            )

            Spacer(Modifier.height(14.dp))

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { onCategoryExpandedChange(!categoryExpanded) }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { onCategoryExpandedChange(false) }
                ) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                onCategoryChange(c)
                                onCategoryExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Priority dropdown
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { onPriorityExpandedChange(!priorityExpanded) }
            ) {
                OutlinedTextField(
                    value = priority,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Priority") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(priorityExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )
                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { onPriorityExpandedChange(false) }
                ) {
                    priorities.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(priorityColor(p), CircleShape)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(p)
                                }
                            },
                            onClick = {
                                onPriorityChange(p)
                                onPriorityExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Location
            ReportField(
                value = location,
                onValueChange = onLocationChange,
                label = "Location",
                placeholder = "e.g. Block B, 3rd Floor",
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, null, tint = PrimaryGreen)
                }
            )

            Spacer(Modifier.height(14.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderLight
                )
            )

            Spacer(Modifier.height(24.dp))

            // Submit
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White
                ),
                enabled = !isSubmitting && title.trim().length >= 5 && location.isNotBlank()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SUBMIT REPORT",
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ═════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════

@Composable
private fun ReportField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        leadingIcon = leadingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = BorderLight
        )
    )
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..total).forEach { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) PrimaryGreen else BorderLight)
            )
            if (i < total) {
                Spacer(
                    Modifier
                        .width(if (i == current) 8.dp else 20.dp)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

private fun priorityColor(priority: String): Color = when (priority) {
    "Urgent" -> ErrorRed
    "High" -> Color(0xFFF59E0B)
    "Medium" -> AccentBlue
    "Low" -> TextTertiary
    else -> TextTertiary
}