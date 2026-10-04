@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.myiapp.ui.screens.common

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.*
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ProblemDetailScreen — Premium redesign
 * ═══════════════════════════════════════════════════════════════════
 */
@Composable
fun ProblemDetailScreen(
    sessionManager: SessionManager,
    problemId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val problemRepository = remember { ProblemRepository() }
    val scrollState = rememberScrollState()

    var problem by remember { mutableStateOf<Problem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUserId by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("Resident") }

    var commentText by remember { mutableStateOf("") }
    var isPostingComment by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var isLiking by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun loadProblem(silent: Boolean = false) {
        if (!silent) isLoading = true
        problemRepository.getProblemById(problemId).fold(
            onSuccess = { response ->
                problem = response.problem
                val role = currentUserRole
                val currentProblem = response.problem
                if (role == "Resident" &&
                    currentProblem != null &&
                    currentProblem.status == "Resolved" &&
                    currentProblem.residentViewedAt.isNullOrBlank()
                ) {
                    problemRepository.markProblemViewed(problemId)
                }
            },
            onFailure = {
                if (!silent) Toast.makeText(
                    context,
                    it.message ?: "Failed to load problem",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        isLoading = false
    }

    LaunchedEffect(problemId) {
        currentUserId = sessionManager.getUserId().orEmpty()
        currentUserRole = sessionManager.getRole().orEmpty()
        loadProblem()
    }

    // ─── Camera launcher ─────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            scope.launch {
                isVerifying = true
                try {
                    val stream = context.contentResolver.openInputStream(photoUri!!)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()

                    if (bitmap != null) {
                        val out = ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"

                        problemRepository.verifyResolution(problemId, dataUri).fold(
                            onSuccess = { res ->
                                problem = res.problem
                                Toast.makeText(
                                    context,
                                    res.verification?.summary ?: "Verification complete",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    context,
                                    e.message ?: "Verification failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Image error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isVerifying = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "verify_capture.jpg")
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
            val file = File(context.cacheDir, "verify_capture.jpg")
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
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Problem Details",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "ID: ${problemId.takeLast(8)}",
                            fontSize = 10.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { scope.launch { loadProblem(silent = true) } }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PrimaryGreen
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGreen)
                }
            }

            problem == null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(ErrorRedSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = ErrorRed
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Problem not found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            else -> {
                val p = problem!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                ) {
                    DetailHeroSection(p) { url ->
                        zoomUrl = url
                        showZoom = true
                    }

                    Column(Modifier.padding(20.dp)) {
                        // Status + priority + category
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadgePremium(p.status)
                            Spacer(Modifier.width(10.dp))
                            PriorityBadgePremium(p.priority)
                            Spacer(Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PrimaryGreenSoft
                            ) {
                                Text(
                                    p.category,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreenDark
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            p.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            lineHeight = 30.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentBlue
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                p.location,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        ReporterRow(
                            reporter = p.reporter,
                            createdAt = p.createdAt,
                            liked = p.isLikedBy(currentUserId),
                            likesCount = p.likesCount,
                            likeEnabled = !isLiking,
                            onLikeClick = {
                                scope.launch {
                                    isLiking = true
                                    problemRepository.toggleLike(p.id).fold(
                                        onSuccess = { res ->
                                            problem = p.copy(
                                                likesCount = res.likesCount,
                                                likedBy = if (res.liked)
                                                    p.likedBy + currentUserId
                                                else
                                                    p.likedBy - currentUserId
                                            )
                                        },
                                        onFailure = { }
                                    )
                                    isLiking = false
                                }
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        // Description card
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "DESCRIPTION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextTertiary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    p.description,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        p.aiDiagnostic?.let { diag ->
                            if (diag.summary.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                AIDiagnosticCard(diag)
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        if (p.status != "Resolved" &&
                            (currentUserRole == "Staff" || currentUserRole == "Admin")
                        ) {
                            MaintainerActionCard(
                                isVerifying = isVerifying,
                                onCapture = { launchCamera() }
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        if (p.status == "Resolved" && p.resolutionVerification != null) {
                            VerificationDeepDetail(p.resolutionVerification!!)
                            Spacer(Modifier.height(20.dp))
                        }

                        if (p.needsResidentFeedback() &&
                            p.reporter?.id == currentUserId
                        ) {
                            ResidentFeedbackPrompt(
                                canRate = p.canSubmitFeedbackNow(),
                                onRate = { showFeedbackDialog = true }
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        SectionTitleRow("TIMELINE", Icons.Default.Timeline)
                        ProblemTimelineDetailed(p)

                        Spacer(Modifier.height(24.dp))

                        // ★ Use AutoMirrored Comment icon
                        SectionTitleRow(
                            "DISCUSSION (${p.comments.size})",
                            Icons.AutoMirrored.Filled.Comment
                        )

                        if (p.comments.isEmpty()) {
                            EmptyCommentsRow()
                        } else {
                            p.comments.forEach { c ->
                                CommentBubble(c, c.authorId == currentUserId)
                                Spacer(Modifier.height(10.dp))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        CommentInputBar(
                            text = commentText,
                            isLoading = isPostingComment,
                            onTextChange = { commentText = it },
                            onSend = {
                                val content = commentText.trim()
                                if (content.isNotBlank()) {
                                    scope.launch {
                                        isPostingComment = true
                                        problemRepository.addComment(p.id, content).fold(
                                            onSuccess = { res ->
                                                res.comment?.let { newComment ->
                                                    problem = p.copy(
                                                        comments = p.comments + newComment
                                                    )
                                                    commentText = ""
                                                }
                                            },
                                            onFailure = { e ->
                                                Toast.makeText(
                                                    context,
                                                    e.message ?: "Failed to post",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                        isPostingComment = false
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    if (showZoom && zoomUrl != null) {
        ZoomDialog(zoomUrl) { showZoom = false }
    }

    if (showFeedbackDialog && problem != null) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { rating, comment ->
                scope.launch {
                    problemRepository.submitFeedback(problem!!.id, rating, comment).fold(
                        onSuccess = { res ->
                            problem = res.problem
                            showFeedbackDialog = false
                            Toast.makeText(
                                context,
                                "Thanks for your feedback!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                context,
                                e.message ?: "Failed to submit",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        )
    }
}

// ═════════════════════════════════════════════
// HERO SECTION
// ═════════════════════════════════════════════

@Composable
private fun DetailHeroSection(p: Problem, onImageClick: (String) -> Unit) {
    val showAfter = p.status == "Resolved" && !p.afterImage.isNullOrBlank()
    val hasBefore = !p.beforeImage.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color.Black)
    ) {
        if (showAfter) {
            Row(Modifier.fillMaxSize()) {
                HeroHalf(
                    url = p.beforeImage,
                    label = "BEFORE",
                    alignEnd = false,
                    onClick = { p.beforeImage?.let(onImageClick) }
                )
                Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
                HeroHalf(
                    url = p.afterImage,
                    label = "AFTER",
                    alignEnd = true,
                    onClick = { p.afterImage?.let(onImageClick) }
                )
            }
        } else if (hasBefore) {
            AsyncImage(
                model = p.beforeImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { p.beforeImage?.let(onImageClick) }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Tap to zoom",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = PrimaryGreenDark,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No photo attached",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreenDark
                    )
                    Text(
                        "Reported without an image",
                        fontSize = 11.sp,
                        color = PrimaryGreenDark.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeroHalf(
    url: String?,
    label: String,
    alignEnd: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onClick() }
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PrimaryGreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = PrimaryGreenDark.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        Surface(
            modifier = Modifier
                .align(if (alignEnd) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 3.dp
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 0.8.sp
            )
        }
    }
}

// ═════════════════════════════════════════════
// REPORTER ROW
// ═════════════════════════════════════════════

@Composable
private fun ReporterRow(
    reporter: ProblemReporter?,
    createdAt: String?,
    liked: Boolean,
    likesCount: Int,
    likeEnabled: Boolean,
    onLikeClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            reporter?.let { r ->
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                            )
                        )
                        .border(2.dp, PrimaryGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        r.name.take(1).uppercase(),
                        color = PrimaryGreenDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryGreenSoft
                        ) {
                            Text(
                                r.role,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = PrimaryGreenDark
                            )
                        }
                        if (!createdAt.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                createdAt,
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            } ?: Spacer(Modifier.weight(1f))

            val likeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = if (liked) 4.dp else 0.dp,
                        shape = RoundedCornerShape(20.dp),
                        clip = false,
                        ambientColor = if (liked) ErrorRed.copy(alpha = 0.3f) else Color.Transparent,
                        spotColor = if (liked) ErrorRed.copy(alpha = 0.3f) else Color.Transparent
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (liked) ErrorRedSoft else SurfaceElevated
                    )
                    .border(
                        width = if (liked) 1.5.dp else 1.dp,
                        color = if (liked) ErrorRed.copy(alpha = 0.4f) else BorderLight,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = likeInteraction,
                        indication = null,
                        enabled = likeEnabled,
                        onClick = onLikeClick
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (liked) ErrorRed else TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$likesCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (liked) ErrorRed else TextSecondary
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// SECTION TITLE
// ═════════════════════════════════════════════

@Composable
private fun SectionTitleRow(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PrimaryGreenSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryGreenDark,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = TextTertiary,
            letterSpacing = 1.sp
        )
    }
}

// ═════════════════════════════════════════════
// MAINTAINER ACTION CARD
// ═════════════════════════════════════════════

@Composable
private fun MaintainerActionCard(isVerifying: Boolean, onCapture: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = AccentBlue.copy(alpha = 0.35f),
                spotColor = AccentBlue.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AccentBlue, AccentBlueDark)
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ready to verify?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "AI will check your before/after photos",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Complete the work and capture a live photo to trigger YOLO verification.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCapture,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = AccentBlue
                ),
                enabled = !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = AccentBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CAPTURE & VERIFY", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// VERIFICATION DETAIL
// ═════════════════════════════════════════════

@Composable
private fun VerificationDeepDetail(v: ResolutionVerification) {
    val color = verdictColor(v.verdict)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.08f))
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AI VERIFICATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = color,
                            letterSpacing = 1.sp
                        )
                        Text(
                            v.verdict.replace("_", " "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Column(Modifier.padding(18.dp)) {
                Text(
                    v.summary,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )

                if (v.resolved.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    DetectionSection(
                        label = "FIXED",
                        icon = Icons.Default.CheckCircle,
                        color = PrimaryGreenDark,
                        bg = PrimaryGreenSoft,
                        items = v.resolved
                    )
                }

                if (v.stillPresent.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetectionSection(
                        label = "STILL PRESENT",
                        icon = Icons.Default.Warning,
                        color = ErrorRed,
                        bg = ErrorRedSoft,
                        items = v.stillPresent
                    )
                }

                if (v.newIssues.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetectionSection(
                        label = "NEW ISSUES",
                        icon = Icons.Default.NewReleases,
                        color = WarningAmber,
                        bg = WarningAmber.copy(alpha = 0.12f),
                        items = v.newIssues
                    )
                }

                if (v.adminOverride) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WarningAmber.copy(alpha = 0.15f)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Admin override: ${v.adminOverrideNote.ifBlank { "Manual verdict" }}",
                                fontSize = 12.sp,
                                color = WarningAmber,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionSection(
    label: String,
    icon: ImageVector,
    color: Color,
    bg: Color,
    items: List<String>
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { obj ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bg
                ) {
                    Text(
                        obj.replace("_", " "),
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// AI DIAGNOSTIC CARD
// ═════════════════════════════════════════════

@Composable
private fun AIDiagnosticCard(diag: AIDiagnostic) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PrimaryGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AI DIAGNOSTIC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryGreenDark,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Gemini analysis",
                            fontSize = 11.sp,
                            color = PrimaryGreenDark.copy(alpha = 0.7f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White
                    ) {
                        Text(
                            diag.severity,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryGreenDark
                        )
                    }
                }
            }

            Column(Modifier.padding(18.dp)) {
                Text(
                    diag.summary,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                if (diag.recommendedAction.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            diag.recommendedAction,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
                if (diag.estimatedHours > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Estimated ${diag.estimatedHours}h",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// RESIDENT FEEDBACK PROMPT
// ═════════════════════════════════════════════

@Composable
private fun ResidentFeedbackPrompt(canRate: Boolean, onRate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = PrimaryGreen.copy(alpha = 0.3f),
                spotColor = PrimaryGreen.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                )
            )
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "How was the fix?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreenDark
                    )
                    Text(
                        if (canRate) "Your rating helps the community"
                        else "View the before/after photos first",
                        fontSize = 11.sp,
                        color = PrimaryGreenDark.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRate,
                enabled = canRate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("RATE THIS FIX", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

// ═════════════════════════════════════════════
// TIMELINE
// ═════════════════════════════════════════════

@Composable
private fun ProblemTimelineDetailed(p: Problem) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            TimelineNode(
                Icons.Default.Add,
                "Reported",
                p.createdAt ?: "Initial",
                p.reporter?.name ?: "Resident",
                hasNext = true
            )
            if (!p.assignedAt.isNullOrBlank()) {
                TimelineNode(
                    Icons.Default.AssignmentInd,
                    "Assigned",
                    p.assignedAt!!,
                    p.assignedToName.ifBlank { "Staff" },
                    hasNext = p.status == "Resolved"
                )
            }
            if (p.status == "Resolved") {
                TimelineNode(
                    Icons.Default.CheckCircle,
                    "Resolved",
                    p.resolvedAt ?: "Completed",
                    "AI Verified",
                    hasNext = false
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(
    icon: ImageVector,
    title: String,
    time: String,
    actor: String,
    hasNext: Boolean
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PrimaryGreenDark
                )
            }
            if (hasNext) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    PrimaryGreenLight,
                                    PrimaryGreenLight.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = 18.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$time • $actor",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

// ═════════════════════════════════════════════
// COMMENTS
// ═════════════════════════════════════════════

@Composable
private fun EmptyCommentsRow() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Comment,   // ★ AutoMirrored
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No comments yet",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Text(
                "Be the first to say something",
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun CommentBubble(c: ProblemComment, isMine: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    c.authorName.take(1).uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryGreenDark
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = if (isMine) 4.dp else 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    ),
                    clip = false,
                    ambientColor = if (isMine)
                        PrimaryGreen.copy(alpha = 0.3f)
                    else
                        Color.Black.copy(alpha = 0.06f),
                    spotColor = if (isMine)
                        PrimaryGreen.copy(alpha = 0.3f)
                    else
                        Color.Black.copy(alpha = 0.06f)
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isMine) {
                        Brush.linearGradient(
                            colors = listOf(PrimaryGreen, PrimaryGreenDark)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color.White, Color.White)
                        )
                    }
                )
                .padding(12.dp)
        ) {
            Column {
                if (!isMine) {
                    Text(
                        c.authorName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryGreenDark
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    c.content,
                    fontSize = 14.sp,
                    color = if (isMine) Color.White else TextPrimary,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// COMMENT INPUT
// ═════════════════════════════════════════════

@Composable
private fun CommentInputBar(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = text.isNotBlank() && !isLoading

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Add a comment…", fontSize = 14.sp, color = TextTertiary)
                },
                shape = RoundedCornerShape(20.dp),
                enabled = !isLoading,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = PrimaryGreen
                )
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) {
                            Brush.linearGradient(
                                colors = listOf(PrimaryGreen, PrimaryGreenDark)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    PrimaryGreen.copy(alpha = 0.35f),
                                    PrimaryGreenDark.copy(alpha = 0.35f)
                                )
                            )
                        }
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// DIALOGS
// ═════════════════════════════════════════════

@Composable
private fun ZoomDialog(url: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().height(520.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black
        ) {
            Box {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String?) -> Unit
) {
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Rate This Fix",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                Text(
                    "How well was the problem resolved?",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (star <= rating) WarningAmber else TextTertiary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment.takeIf { it.isNotBlank() }) },
                enabled = rating in 1..5,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// ═════════════════════════════════════════════
// SMALL HELPERS
// ═════════════════════════════════════════════

@Composable
private fun StatusBadgePremium(status: String) {
    val color = statusColor(status)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color,
        shadowElevation = 3.dp
    ) {
        Text(
            status.uppercase(),
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun PriorityBadgePremium(priority: String) {
    val color = priorityColor(priority)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.5f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                priority.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}