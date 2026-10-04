package com.example.myiapp.ui.screens.admin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminUser
import com.example.myiapp.data.model.AnalyzeProblemRequest
import com.example.myiapp.data.model.YoloDetection
import com.example.myiapp.data.network.RetrofitClient
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

// ═══════════════════════════════════════════════════════════════════
//   TOP-LEVEL HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════

/**
 * Decode a Uri into a Bitmap. Returns null on failure.
 */
private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Resize a bitmap so its longest side is <= maxSide pixels.
 */
private fun resizeBitmap(bitmap: Bitmap, maxSide: Int = 1024): Bitmap {
    val scale = if (bitmap.width > bitmap.height)
        maxSide.toFloat() / bitmap.width.toFloat()
    else
        maxSide.toFloat() / bitmap.height.toFloat()

    return if (scale < 1f) {
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else {
        bitmap
    }
}

/**
 * Convert a bitmap to a JPEG data URI: `data:image/jpeg;base64,...`
 */
private fun bitmapToDataUri(bitmap: Bitmap): String {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}

/**
 * Create a FileProvider URI for the camera to write into.
 */
private fun createCameraUri(context: Context): Uri {
    val file = File(context.cacheDir, "admin_capture.jpg")
    if (file.exists()) {
        file.delete()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

/**
 * Grant read+write URI permission to every app that can handle IMAGE_CAPTURE.
 */
private fun grantCameraUriPermissions(context: Context, uri: Uri) {
    try {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolvers = context.packageManager.queryIntentActivities(cameraIntent, 0)
        for (resolver in resolvers) {
            context.grantUriPermission(
                resolver.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    } catch (_: Exception) {
        // Ignored — legacy path still works
    }
}

/**
 * Check if the camera permission is granted. Returns Boolean.
 */
private fun hasCameraPermission(context: Context): Boolean {
    val status: Int = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    )
    return status == PackageManager.PERMISSION_GRANTED
}

// ═══════════════════════════════════════════════════════════════════
//   MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminCreateProblemScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onProblemCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    // ─── Step state ──────────────────────────
    var step by rememberSaveable { mutableIntStateOf(1) }

    // ─── Reporter ────────────────────────────
    var residents by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoadingResidents by remember { mutableStateOf(true) }
    var selectedReporter by remember { mutableStateOf<AdminUser?>(null) }
    var reporterDropdownExpanded by remember { mutableStateOf(false) }
    var reporterSearchQuery by remember { mutableStateOf("") }

    // ─── Image ───────────────────────────────
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // ─── AI Analysis ─────────────────────────
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var yoloDetections by remember { mutableStateOf<List<YoloDetection>>(emptyList()) }
    var yoloModelName by remember { mutableStateOf("") }
    var yoloInferenceMs by remember { mutableIntStateOf(0) }
    var aiSummary by remember { mutableStateOf("") }
    var aiRecommendedAction by remember { mutableStateOf("") }
    var aiSeverity by remember { mutableStateOf("Medium") }

    // ─── Form ────────────────────────────────
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Other") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "Lighting", "Plumbing", "Electrical", "Elevator",
        "Security", "Cleanliness", "Structural", "HVAC",
        "Parking", "Noise", "Other"
    )
    val priorities = listOf("Low", "Medium", "High", "Urgent")

    // ═════════════════════════════════════════════
    // Top-level val lambdas — safe scoping
    // ═════════════════════════════════════════════

    // Process a captured/picked image URI → bitmap + base64 + analyze
    val onImageReady: (Uri) -> Unit = { uri ->
        val raw = decodeBitmapFromUri(context, uri)
        if (raw != null) {
            val resized = resizeBitmap(raw)
            val dataUri = bitmapToDataUri(resized)

            imageBitmap = resized
            imageBase64 = dataUri

            // Reset AI state
            yoloDetections = emptyList()
            aiSummary = ""
            analysisError = null

            // Move to analyzing step
            step = 2

            // Kick off AI analysis
            isAnalyzing = true
            analysisError = null
            scope.launch {
                try {
                    val api = RetrofitClient.apiService
                    val response = api.analyzeProblem(
                        AnalyzeProblemRequest(
                            beforeImage = dataUri,
                            locationHint = location.takeIf { it.isNotBlank() }
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.success) {
                            body.yolo?.let { yolo ->
                                yoloDetections = yolo.detections
                                yoloModelName = yolo.modelLoaded
                                yoloInferenceMs = yolo.inferenceMs
                            }
                            body.draft?.let { draft ->
                                title = draft.title
                                description = draft.description
                                category = if (categories.contains(draft.category))
                                    draft.category else "Other"
                                priority = if (priorities.contains(draft.priority))
                                    draft.priority else "Medium"
                                aiSummary = draft.recommendedAction
                                aiSeverity = draft.severity
                            }
                            step = 3
                        } else {
                            analysisError = body.message ?: "AI analysis failed"
                            step = 3
                        }
                    } else {
                        analysisError = "Could not reach AI service"
                        step = 3
                    }
                } catch (e: Exception) {
                    analysisError = e.message ?: "AI analysis failed"
                    step = 3
                }
                isAnalyzing = false
            }
        } else {
            Toast.makeText(context, "Image processing failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = photoUri
            if (uri != null) {
                onImageReady(uri)
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImageReady(uri)
        }
    }

    // Permission launcher → after grant, launch camera
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraUri(context)
            photoUri = uri
            grantCameraUriPermissions(context, uri)
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera button handler
    val onCameraClick: () -> Unit = {
        if (hasCameraPermission(context)) {
            val uri = createCameraUri(context)
            photoUri = uri
            grantCameraUriPermissions(context, uri)
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Submit handler — uses if/else (not `when` expression)
    val onSubmit: () -> Unit = {
        val reporter = selectedReporter
        if (reporter == null) {
            errorMessage = "Please select a resident"
            step = 1
        } else if (title.trim().length < 5) {
            errorMessage = "Title must be at least 5 characters"
        } else if (location.isBlank()) {
            errorMessage = "Location is required"
        } else {
            isSubmitting = true
            errorMessage = null
            scope.launch {
                adminRepository.createProblemOnBehalf(
                    reporterId = reporter.id,
                    title = title.trim(),
                    category = category,
                    location = location.trim(),
                    description = description.trim(),
                    priority = priority,
                    beforeImage = imageBase64
                ).fold(
                    onSuccess = { response ->
                        isSubmitting = false
                        val problemId = response.problem?.id
                        if (response.success && !problemId.isNullOrBlank()) {
                            Toast.makeText(context, "Problem created", Toast.LENGTH_SHORT).show()
                            onProblemCreated(problemId)
                        } else {
                            errorMessage = response.message ?: "Failed to create problem"
                        }
                    },
                    onFailure = { e ->
                        isSubmitting = false
                        errorMessage = e.message ?: "Failed to create problem"
                    }
                )
            }
        }
    }

    // ═════════════════════════════════════════════
    // Load residents
    // ═════════════════════════════════════════════

    LaunchedEffect(Unit) {
        adminRepository.getUsers(role = "Resident", limit = 200).fold(
            onSuccess = { response ->
                residents = response.users
            },
            onFailure = { e ->
                errorMessage = "Failed to load residents: ${e.message}"
            }
        )
        isLoadingResidents = false
    }

    val filteredResidents = remember(residents, reporterSearchQuery) {
        if (reporterSearchQuery.isBlank()) {
            residents
        } else {
            residents.filter { u ->
                u.name.contains(reporterSearchQuery, ignoreCase = true) ||
                        u.email.contains(reporterSearchQuery, ignoreCase = true) ||
                        u.phone.contains(reporterSearchQuery, ignoreCase = true)
            }
        }
    }

    // ═════════════════════════════════════════════
    // UI
    // ═════════════════════════════════════════════

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            Surface(
                color = AdminPurple,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isAnalyzing) {
                                // locked
                            } else if (step > 1) {
                                step -= 1
                            } else {
                                onBack()
                            }
                        },
                        enabled = !isAnalyzing
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Create on Behalf",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (step) {
                                1 -> "Select reporter + add photo"
                                2 -> "Analyzing image…"
                                else -> "Review & submit"
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "STEP $step/3",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedVisibility(visible = step == 1, enter = fadeIn(), exit = fadeOut()) {
                Step1ReporterAndPhoto(
                    residents = filteredResidents,
                    isLoadingResidents = isLoadingResidents,
                    selectedReporter = selectedReporter,
                    onReporterSelected = { selectedReporter = it },
                    searchQuery = reporterSearchQuery,
                    onSearchChange = { reporterSearchQuery = it },
                    reporterDropdownExpanded = reporterDropdownExpanded,
                    onDropdownExpandedChange = { reporterDropdownExpanded = it },
                    imageBitmap = imageBitmap,
                    onCameraClick = onCameraClick,
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onSkipPhoto = { step = 3 }
                )
            }

            AnimatedVisibility(visible = step == 2, enter = fadeIn(), exit = fadeOut()) {
                Step2Analyzing()
            }

            AnimatedVisibility(visible = step == 3, enter = fadeIn(), exit = fadeOut()) {
                Step3Review(
                    imageBitmap = imageBitmap,
                    selectedReporter = selectedReporter,
                    yoloDetections = yoloDetections,
                    yoloModelName = yoloModelName,
                    yoloInferenceMs = yoloInferenceMs,
                    aiSummary = aiSummary,
                    aiRecommendedAction = aiRecommendedAction,
                    aiSeverity = aiSeverity,
                    analysisError = analysisError,
                    title = title,
                    onTitleChange = { title = it; errorMessage = null },
                    description = description,
                    onDescriptionChange = { description = it },
                    location = location,
                    onLocationChange = { location = it; errorMessage = null },
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
                    errorMessage = errorMessage,
                    onRetakePhoto = { step = 1 },
                    onSubmit = onSubmit
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// STEP 1 — Reporter + Photo
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step1ReporterAndPhoto(
    residents: List<AdminUser>,
    isLoadingResidents: Boolean,
    selectedReporter: AdminUser?,
    onReporterSelected: (AdminUser) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    reporterDropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    imageBitmap: Bitmap?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSkipPhoto: () -> Unit
) {
    Column(Modifier.padding(20.dp)) {

        Text(
            "WHO IS REPORTING?",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))

        if (isLoadingResidents) {
            Box(
                Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple, modifier = Modifier.size(28.dp))
            }
        } else if (residents.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WarningAmber.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No residents in your community yet.",
                    fontSize = 13.sp,
                    color = WarningAmber,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            ExposedDropdownMenuBox(
                expanded = reporterDropdownExpanded,
                onExpandedChange = { onDropdownExpandedChange(!reporterDropdownExpanded) }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .clickable { onDropdownExpandedChange(true) }
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AdminPurpleSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedReporter != null) {
                                Text(
                                    selectedReporter.name.take(1).uppercase(),
                                    color = AdminPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AdminPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (selectedReporter != null) {
                                Text(
                                    selectedReporter.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    selectedReporter.email,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text("Select resident", fontSize = 15.sp, color = TextTertiary)
                                Text(
                                    "${residents.size} available",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            if (reporterDropdownExpanded) Icons.Default.ArrowDropUp
                            else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }
                }

                ExposedDropdownMenu(
                    expanded = reporterDropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) },
                    modifier = Modifier.background(SurfaceWhite)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search by name / email…", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, null, tint = TextTertiary)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdminPurple,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    if (residents.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching residents", fontSize = 13.sp, color = TextTertiary)
                        }
                    } else {
                        residents.take(30).forEach { resident ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(AdminPurpleSoft),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                resident.name.take(1).uppercase(),
                                                color = AdminPurple,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                resident.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                resident.email,
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onReporterSelected(resident)
                                    onDropdownExpandedChange(false)
                                    onSearchChange("")
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "PHOTO (OPTIONAL)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "AI will detect objects and draft the report",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceWhite)
                .border(2.dp, BorderLight, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    imageBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = TextTertiary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No photo attached", fontSize = 13.sp, color = TextTertiary)
                    Text("Skip to create manually", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) {
                Icon(Icons.Default.Camera, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Camera", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AdminPurple)
            ) {
                Icon(
                    Icons.Default.Collections,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AdminPurple
                )
                Spacer(Modifier.width(8.dp))
                Text("Gallery", color = AdminPurple, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSkipPhoto,
            enabled = selectedReporter != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AdminPurple,
                disabledContainerColor = AdminPurple.copy(alpha = 0.4f)
            )
        ) {
            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "CONTINUE WITHOUT PHOTO",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ═════════════════════════════════════════════
// STEP 2 — Analyzing
// ═════════════════════════════════════════════

@Composable
private fun Step2Analyzing() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .background(AdminPurpleSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = AdminPurple,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Analyzing photo…",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "YOLO is detecting objects\nGemini is drafting the report",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = AdminPurple)
    }
}

// ═════════════════════════════════════════════
// STEP 3 — Review & Submit
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Step3Review(
    imageBitmap: Bitmap?,
    selectedReporter: AdminUser?,
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
    errorMessage: String?,
    onRetakePhoto: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(Modifier.padding(20.dp)) {

        if (selectedReporter != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AdminPurpleSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = AdminPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Reporting on behalf of",
                            fontSize = 11.sp,
                            color = AdminPurpleDark
                        )
                        Text(
                            selectedReporter.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminPurpleDark
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (imageBitmap != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
            ) {
                Image(
                    imageBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clickable { onRetakePhoto() },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.9f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = TextPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Retake",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (analysisError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WarningAmber.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI unavailable: please fill in manually",
                        fontSize = 12.sp,
                        color = WarningAmber
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

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
                    Surface(shape = RoundedCornerShape(10.dp), color = AdminPurpleSoft) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                det.className.replace("_", " "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdminPurple
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                det.confidencePercent(),
                                fontSize = 10.sp,
                                color = AdminPurple.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Model: $yoloModelName · ${yoloInferenceMs}ms",
                fontSize = 10.sp,
                color = TextTertiary
            )
            Spacer(Modifier.height(16.dp))
        }

        if (aiSummary.isNotBlank() || aiRecommendedAction.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AdminPurpleSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = AdminPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI DIAGNOSTIC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = AdminPurpleDark,
                            letterSpacing = 1.sp
                        )
                    }
                    if (aiRecommendedAction.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            aiRecommendedAction,
                            fontSize = 13.sp,
                            color = AdminPurpleDark,
                            lineHeight = 18.sp
                        )
                    }
                    if (aiSeverity.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Severity: $aiSeverity",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminPurpleDark.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AdminPurple,
                unfocusedBorderColor = BorderLight,
                cursorColor = AdminPurple
            )
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { onCategoryExpandedChange(!categoryExpanded) }
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
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
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = priorityExpanded,
            onExpandedChange = { onPriorityExpandedChange(!priorityExpanded) }
        ) {
            OutlinedTextField(
                value = priority,
                onValueChange = {},
                readOnly = true,
                label = { Text("Priority") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(priorityExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
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
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Location") },
            placeholder = { Text("e.g. Block B, 3rd Floor") },
            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = AdminPurple) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AdminPurple,
                unfocusedBorderColor = BorderLight,
                cursorColor = AdminPurple
            )
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            shape = RoundedCornerShape(14.dp),
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AdminPurple,
                unfocusedBorderColor = BorderLight,
                cursorColor = AdminPurple
            )
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ErrorRedSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        errorMessage,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !isSubmitting &&
                    title.trim().length >= 5 &&
                    location.isNotBlank() &&
                    selectedReporter != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = AdminPurple,
                disabledContainerColor = AdminPurple.copy(alpha = 0.4f)
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "CREATE PROBLEM",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ═════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════

private fun priorityColor(priority: String): Color = when (priority) {
    "Urgent" -> Color(0xFFDC2626)
    "High" -> ErrorRed
    "Medium" -> WarningAmber
    "Low" -> PrimaryGreen
    else -> TextTertiary
}