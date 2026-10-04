package com.example.myiapp.ui.screens.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminMaintainer
import com.example.myiapp.data.model.AdminUser
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.model.ProblemResponse
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.BackgroundLight
import com.example.myiapp.ui.theme.BorderLight
import com.example.myiapp.ui.theme.ErrorRed
import com.example.myiapp.ui.theme.ErrorRedSoft
import com.example.myiapp.ui.theme.PrimaryGreen
import com.example.myiapp.ui.theme.PrimaryGreenDark
import com.example.myiapp.ui.theme.PrimaryGreenSoft
import com.example.myiapp.ui.theme.SurfaceElevated
import com.example.myiapp.ui.theme.SurfaceWhite
import com.example.myiapp.ui.theme.TextPrimary
import com.example.myiapp.ui.theme.TextSecondary
import com.example.myiapp.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// ─────────────────────────────────────────────
// Admin purple accent
// ─────────────────────────────────────────────
private val AdminPurple = Color(0xFF7C3AED)
private val AdminPurpleDark = Color(0xFF4C1D95)
private val AdminPurpleSoft = Color(0xFFEDE9FE)

// Constants
private val CATEGORIES = listOf(
    "Lighting", "Plumbing", "Electrical", "Elevator",
    "Security", "Cleanliness", "Structural", "HVAC",
    "Parking", "Noise", "Other"
)

private val PRIORITIES = listOf("Low", "Medium", "High", "Urgent")
private val STATUSES = listOf("Open", "Assigned", "In Progress", "Resolved", "Rejected")
private val FEEDBACK_STATUSES = listOf("pending", "submitted")

// Status colors
private fun statusColor(status: String): Color = when (status) {
    "Open" -> Color(0xFFF59E0B)
    "Assigned" -> Color(0xFF3B82F6)
    "In Progress" -> Color(0xFF8B5CF6)
    "Resolved" -> Color(0xFF10B981)
    "Rejected" -> Color(0xFFEF4444)
    else -> TextTertiary
}

private fun priorityColor(priority: String): Color = when (priority) {
    "Low" -> Color(0xFF10B981)
    "Medium" -> Color(0xFFF59E0B)
    "High" -> Color(0xFFEF4444)
    "Urgent" -> Color(0xFFDC2626)
    else -> TextTertiary
}

private fun verdictColor(verdict: String): Color = when (verdict) {
    "VERIFIED" -> Color(0xFF10B981)
    "PARTIAL" -> Color(0xFFF59E0B)
    "NOT_VERIFIED" -> Color(0xFFEF4444)
    else -> TextTertiary
}

private fun priorityOrPurple(priority: String): Color =
    if (priority == "Urgent" || priority == "High") priorityColor(priority) else AdminPurple

// ═════════════════════════════════════════════
// MAIN SCREEN
// ═════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProblemEditScreen(
    sessionManager: SessionManager,
    problemId: String?,          // null = create mode
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }
    val problemRepository = remember { ProblemRepository() }

    val isCreateMode = problemId.isNullOrBlank()

    // ─── Form state ─────────────────────────
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Other") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var status by rememberSaveable { mutableStateOf("Open") }
    var feedbackStatus by rememberSaveable { mutableStateOf("pending") }

    var assignedTo by rememberSaveable { mutableStateOf<String?>(null) }
    var assignedToName by rememberSaveable { mutableStateOf("") }

    var reporterId by rememberSaveable { mutableStateOf<String?>(null) }
    var reporterName by rememberSaveable { mutableStateOf("") }
    var reporterRole by rememberSaveable { mutableStateOf("Resident") }

    var beforeImage by rememberSaveable { mutableStateOf<String?>(null) }
    var afterImage by rememberSaveable { mutableStateOf<String?>(null) }

    // Loaded data
    var problem by remember { mutableStateOf<Problem?>(null) }
    var residents by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var maintainers by remember { mutableStateOf<List<AdminMaintainer>>(emptyList()) }

    // Dropdowns
    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var feedbackExpanded by remember { mutableStateOf(false) }
    var assigneeExpanded by remember { mutableStateOf(false) }
    var reporterExpanded by remember { mutableStateOf(false) }

    // UI state
    var isFetching by remember { mutableStateOf(!isCreateMode) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun clearError() { if (errorMessage != null) errorMessage = null }

    // ─────────────────────────────────────────
    // Load initial data
    // ─────────────────────────────────────────
    LaunchedEffect(problemId) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            errorMessage = "Session expired"
            isFetching = false
            return@LaunchedEffect
        }

        val usersResult = adminRepository.getUsers(role = "Resident", limit = 200)
        if (usersResult.isSuccess && usersResult.getOrNull()?.success == true) {
            residents = (usersResult.getOrNull()?.users ?: emptyList())
                .sortedBy { it.name.lowercase() }
        }

        val maintainersResult = adminRepository.getMaintainers()
        if (maintainersResult.isSuccess && maintainersResult.getOrNull()?.success == true) {
            maintainers = maintainersResult.getOrNull()?.maintainers ?: emptyList()
        }

        if (!isCreateMode) {
            val result = problemRepository.getProblemById(problemId!!)
            if (result.isSuccess && result.getOrNull()?.success == true) {
                val p = result.getOrNull()?.problem
                if (p != null) {
                    problem = p
                    title = p.title
                    description = p.description
                    location = p.location
                    category = p.category
                    priority = p.priority
                    status = p.status
                    feedbackStatus = p.feedbackStatus
                    assignedTo = p.assignedTo
                    assignedToName = p.assignedToName
                    reporterId = p.reporter?.id
                    reporterName = p.reporter?.name ?: ""
                    reporterRole = p.reporter?.role ?: "Resident"
                    beforeImage = p.beforeImage
                    afterImage = p.afterImage
                } else {
                    errorMessage = "Problem not found"
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to load problem"
            }
        }
        isFetching = false
    }

    // ─────────────────────────────────────────
    // Image compress
    // ─────────────────────────────────────────
    fun compressToBase64(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxSide = 1024
            val w = bitmap.width
            val h = bitmap.height
            if (w > maxSide || h > maxSide) {
                val scale = maxSide.toFloat() / maxOf(w, h)
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (w * scale).toInt(),
                    (h * scale).toInt(),
                    true
                )
            }

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            null
        }
    }

    var pendingImageTarget by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val base64 = compressToBase64(uri)
            if (base64 != null) {
                when (pendingImageTarget) {
                    "before" -> beforeImage = base64
                    "after" -> afterImage = base64
                }
            } else {
                Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
        pendingImageTarget = null
    }

    // ─────────────────────────────────────────
    // Validate / Save / Delete / Override
    // ─────────────────────────────────────────
    fun validate(): String? {
        if (title.trim().length < 5) return "Title must be at least 5 characters"
        if (location.trim().isBlank()) return "Please enter location"
        if (description.trim().length < 10) return "Description must be at least 10 characters"
        if (isCreateMode && reporterId.isNullOrBlank()) return "Please select a resident as reporter"
        return null
    }

    fun save() {
        val err = validate()
        if (err != null) {
            errorMessage = err
            return
        }
        errorMessage = null
        isSaving = true

        scope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired"
                isSaving = false
                return@launch
            }

            val result: Result<ProblemResponse>

            if (isCreateMode) {
                result = adminRepository.createProblemOnBehalf(
                    reporterId = reporterId!!,
                    title = title.trim(),
                    category = category,
                    location = location.trim(),
                    description = description.trim(),
                    priority = priority,
                    beforeImage = beforeImage
                )
            } else {
                result = adminRepository.adminEditProblem(
                    problemId = problemId!!,
                    title = title.trim(),
                    description = description.trim(),
                    location = location.trim(),
                    category = category,
                    priority = priority,
                    status = status,
                    assignedTo = assignedTo,
                    beforeImage = beforeImage,
                    afterImage = afterImage,
                    feedbackStatus = feedbackStatus
                )
            }

            isSaving = false

            if (result.isSuccess && result.getOrNull()?.success == true) {
                Toast.makeText(
                    context,
                    if (isCreateMode) "Problem created" else "Problem updated",
                    Toast.LENGTH_SHORT
                ).show()
                onSaved()
            } else {
                errorMessage = result.exceptionOrNull()?.message
                    ?: result.getOrNull()?.message
                            ?: "Failed to save"
            }
        }
    }

    fun performDelete() {
        if (problemId.isNullOrBlank()) return
        isDeleting = true
        scope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired"
                isDeleting = false
                return@launch
            }
            val result = problemRepository.deleteProblem(problemId)
            isDeleting = false
            if (result.isSuccess && result.getOrNull()?.success == true) {
                Toast.makeText(context, "Problem deleted", Toast.LENGTH_LONG).show()
                onDeleted()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete"
            }
        }
    }

    fun performOverride(verdict: String, note: String) {
        if (problemId.isNullOrBlank()) return
        scope.launch {
            val token = sessionManager.getToken() ?: return@launch
            val result = adminRepository.overrideVerification(
                problemId = problemId,
                verdict = verdict,
                note = note
            )
            if (result.isSuccess && result.getOrNull()?.success == true) {
                problem = result.getOrNull()?.problem
                status = problem?.status ?: status
                Toast.makeText(context, "Verification overridden", Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to override"
            }
        }
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            Surface(
                color = SurfaceWhite,
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
                    IconButton(
                        onClick = onBack,
                        enabled = !isSaving && !isDeleting
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isCreateMode) "Add Problem" else "Edit Problem",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (isCreateMode) "Log on behalf of a resident"
                            else "Full admin control over all fields",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isFetching) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── Current status banner (edit) ──
            if (!isCreateMode && problem != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusColor(status).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            tint = statusColor(status)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Current status: $status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = statusColor(status)
                            )
                            Text(
                                "Created ${problem!!.createdAt ?: ""}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Create mode info ──────────────
            if (isCreateMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AdminPurpleSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = AdminPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Select a resident below — the problem will be logged under their name.",
                            fontSize = 12.sp,
                            color = AdminPurpleDark,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Reporter ──────────────────────
            if (isCreateMode) {
                SectionLabel("Reporter (Resident)")
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = reporterExpanded,
                    onExpandedChange = {
                        if (!isSaving && !isDeleting) reporterExpanded = !reporterExpanded
                    }
                ) {
                    OutlinedTextField(
                        value = reporterName.ifBlank { "Select a resident" },
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary)
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = reporterExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSaving && !isDeleting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdminPurple,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = reporterExpanded,
                        onDismissRequest = { reporterExpanded = false }
                    ) {
                        if (residents.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No residents available", color = TextTertiary) },
                                onClick = { reporterExpanded = false },
                                enabled = false
                            )
                        } else {
                            residents.forEach { resident ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(resident.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${resident.email} · ${resident.communityName}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        reporterId = resident.id
                                        reporterName = resident.name
                                        reporterRole = resident.role
                                        reporterExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                SectionLabel("Reporter")
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(PrimaryGreenSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                reporterName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                "$reporterRole · Not editable",
                                fontSize = 11.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Title ─────────────────────────
            SectionLabel("Title")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; clearError() },
                placeholder = { Text("Short title of the problem") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isSaving && !isDeleting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Location ──────────────────────
            SectionLabel("Location")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it; clearError() },
                placeholder = { Text("e.g., Building A, Floor 3") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isSaving && !isDeleting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Description ───────────────────
            SectionLabel("Description")
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; clearError() },
                placeholder = { Text("Describe the problem in detail…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSaving && !isDeleting,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Category + Priority ───────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = {
                            if (!isSaving && !isDeleting) categoryExpanded = !categoryExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category", fontSize = 12.sp) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSaving && !isDeleting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AdminPurple,
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = SurfaceWhite
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { category = cat; categoryExpanded = false }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = priorityExpanded,
                        onExpandedChange = {
                            if (!isSaving && !isDeleting) priorityExpanded = !priorityExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = priority,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority", fontSize = 12.sp) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSaving && !isDeleting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = priorityOrPurple(priority),
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = SurfaceWhite,
                                focusedTextColor = priorityColor(priority),
                                unfocusedTextColor = priorityColor(priority)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false }
                        ) {
                            PRIORITIES.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            p,
                                            color = priorityColor(p),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = { priority = p; priorityExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Status + Feedback (edit only) ─
            if (!isCreateMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = {
                                if (!isSaving && !isDeleting) statusExpanded = !statusExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = status,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status", fontSize = 12.sp) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isSaving && !isDeleting,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = statusColor(status),
                                    unfocusedBorderColor = BorderLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    focusedTextColor = statusColor(status),
                                    unfocusedTextColor = statusColor(status)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                STATUSES.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                s,
                                                color = statusColor(s),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        onClick = { status = s; statusExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = feedbackExpanded,
                            onExpandedChange = {
                                if (!isSaving && !isDeleting) feedbackExpanded = !feedbackExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = feedbackStatus,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Feedback", fontSize = 12.sp) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = feedbackExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isSaving && !isDeleting,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AdminPurple,
                                    unfocusedBorderColor = BorderLight,
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = feedbackExpanded,
                                onDismissRequest = { feedbackExpanded = false }
                            ) {
                                FEEDBACK_STATUSES.forEach { f ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                f.replaceFirstChar { it.uppercase() },
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        onClick = { feedbackStatus = f; feedbackExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ─── Assigned To ───────────────
                SectionLabel("Assigned To")
                Spacer(modifier = Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = assigneeExpanded,
                    onExpandedChange = {
                        if (!isSaving && !isDeleting) assigneeExpanded = !assigneeExpanded
                    }
                ) {
                    OutlinedTextField(
                        value = assignedToName.ifBlank { "Unassigned" },
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = assigneeExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSaving && !isDeleting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdminPurple,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = assigneeExpanded,
                        onDismissRequest = { assigneeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned", color = TextTertiary) },
                            onClick = {
                                assignedTo = null
                                assignedToName = ""
                                assigneeExpanded = false
                            }
                        )
                        HorizontalDivider(color = BorderLight)
                        maintainers.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(m.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${m.role} · ${m.stats.activeTasks} active",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    assignedTo = m.id
                                    assignedToName = m.name
                                    assigneeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Images ────────────────────────
            SectionLabel("Before Photo")
            Spacer(modifier = Modifier.height(6.dp))
            ImageTile(
                imageData = beforeImage,
                label = "Before",
                enabled = !isSaving && !isDeleting,
                onPickClick = {
                    pendingImageTarget = "before"
                    imagePicker.launch("image/*")
                },
                onRemoveClick = { beforeImage = null }
            )
            Spacer(modifier = Modifier.height(14.dp))

            SectionLabel("After Photo")
            Spacer(modifier = Modifier.height(6.dp))
            ImageTile(
                imageData = afterImage,
                label = "After",
                enabled = !isSaving && !isDeleting,
                onPickClick = {
                    pendingImageTarget = "after"
                    imagePicker.launch("image/*")
                },
                onRemoveClick = { afterImage = null }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── YOLO Verification (edit) ──────
            if (!isCreateMode && problem?.resolutionVerification != null) {
                val v = problem!!.resolutionVerification!!
                SectionLabel("YOLO Verification")
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = verdictColor(v.verdict).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (v.verdict) {
                                    "VERIFIED" -> Icons.Default.CheckCircle
                                    "NOT_VERIFIED" -> Icons.Default.Warning
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = verdictColor(v.verdict),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (v.verdict) {
                                    "VERIFIED" -> "✅ Verified"
                                    "PARTIAL" -> "⚠️ Partial"
                                    "NOT_VERIFIED" -> "❌ Not Verified"
                                    else -> "ℹ️ Unverifiable"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = verdictColor(v.verdict)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${v.confidence}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = verdictColor(v.verdict)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(v.summary, fontSize = 12.sp, color = TextPrimary, lineHeight = 17.sp)

                        if (v.adminOverride) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White
                            ) {
                                Text(
                                    "⚡ Admin overrode this verdict${
                                        if (v.adminOverrideNote.isNotBlank()) ": ${v.adminOverrideNote}"
                                        else ""
                                    }",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showOverrideDialog = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AdminPurple
                            ),
                            enabled = !isSaving && !isDeleting
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Override verdict", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Error ─────────────────────────
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRedSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage!!,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Save button ───────────────────
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSaving && !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isCreateMode) "CREATE PROBLEM" else "SAVE CHANGES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // ─── Delete (edit mode) ────────────
            if (!isCreateMode) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSaving && !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = ErrorRed,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DELETE PROBLEM", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ─────────────────────────────────────────
    // Delete confirm dialog
    // ─────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceWhite,
            title = { Text("Delete problem?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"$title\" will be soft-deleted. It won't be visible in feeds, but data is retained for audit.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        performDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // ─────────────────────────────────────────
    // Override verification dialog (4 verdicts)
    // ─────────────────────────────────────────
    if (showOverrideDialog) {
        var overrideVerdict by remember { mutableStateOf("VERIFIED") }
        var overrideNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showOverrideDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceWhite,
            title = {
                Text("Override Verification", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Manually set the resolution verdict. This will update the problem status and notify the reporter.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Two rows of 2 verdict options (4 total)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OverrideOption(
                            label = "✅ Verified",
                            selected = overrideVerdict == "VERIFIED",
                            color = Color(0xFF10B981),
                            onClick = { overrideVerdict = "VERIFIED" },
                            modifier = Modifier.weight(1f)
                        )
                        OverrideOption(
                            label = "⚠️ Partial",
                            selected = overrideVerdict == "PARTIAL",
                            color = Color(0xFFF59E0B),
                            onClick = { overrideVerdict = "PARTIAL" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OverrideOption(
                            label = "❌ Reject",
                            selected = overrideVerdict == "NOT_VERIFIED",
                            color = Color(0xFFEF4444),
                            onClick = { overrideVerdict = "NOT_VERIFIED" },
                            modifier = Modifier.weight(1f)
                        )
                        OverrideOption(
                            label = "ℹ️ Unverifiable",
                            selected = overrideVerdict == "UNVERIFIABLE",
                            color = TextTertiary,
                            onClick = { overrideVerdict = "UNVERIFIABLE" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = overrideNote,
                        onValueChange = { overrideNote = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdminPurple,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverrideDialog = false
                        performOverride(overrideVerdict, overrideNote)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverrideDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Reusable components
// ─────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        letterSpacing = 0.3.sp
    )
}

@Composable
private fun ImageTile(
    imageData: String?,
    label: String,
    enabled: Boolean,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderLight, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageData.isNullOrBlank()) {
            AsyncImage(
                model = imageData,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Remove button
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clickable(enabled = enabled) { onRemoveClick() }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }

            // Change button
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clickable(enabled = enabled) { onPickClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Change",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = enabled) { onPickClick() },
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap to upload $label photo",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun OverrideOption(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.15f) else SurfaceElevated,
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() }
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) color else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) color else TextSecondary
            )
        }
    }
}