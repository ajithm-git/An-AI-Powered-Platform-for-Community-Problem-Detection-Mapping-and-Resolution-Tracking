package com.example.myiapp.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminUser
import com.example.myiapp.data.model.MaintainerDetailStats
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════
// AdminMaintainerDetailScreen
// ═════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMaintainerDetailScreen(
    sessionManager: SessionManager,
    maintainerId: String,
    onBack: () -> Unit,
    onProblemClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    // ─── State ───────────────────────────────
    var maintainer by remember { mutableStateOf<AdminUser?>(null) }
    var stats by remember { mutableStateOf<MaintainerDetailStats?>(null) }
    var activeProblems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var resolvedProblems by remember { mutableStateOf<List<Problem>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Reassign sheet
    var showReassignSheet by remember { mutableStateOf(false) }
    var problemToReassign by remember { mutableStateOf<Problem?>(null) }

    // ─── Load ────────────────────────────────
    fun loadDetail(showSpinner: Boolean = true) {
        if (showSpinner) isLoading = true else isRefreshing = true
        errorMessage = null

        scope.launch {
            adminRepository.getMaintainerDetail(maintainerId).fold(
                onSuccess = { body ->
                    if (body.success) {
                        maintainer = body.maintainer
                        stats = body.stats
                        activeProblems = body.activeProblems
                        resolvedProblems = body.resolvedProblems
                    } else {
                        errorMessage = body.message ?: "Failed to load maintainer"
                    }
                    isLoading = false
                    isRefreshing = false
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to load maintainer"
                    isLoading = false
                    isRefreshing = false
                }
            )
        }
    }

    LaunchedEffect(maintainerId) { loadDetail() }

    val currentList = if (selectedTab == 0) activeProblems else resolvedProblems

    // ─── UI ──────────────────────────────────
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        "Maintainer Detail",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { loadDetail(showSpinner = false) },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AdminPurple,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = AdminPurple
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple)
            }
            return@Scaffold
        }

        // Not found
        if (maintainer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMessage ?: "Maintainer not found",
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        val m = maintainer!!
        val s = stats ?: MaintainerDetailStats()
        val roleC = roleAccentColor(m.role)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // ─── Profile header ──────────────
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(roleC, roleC.copy(alpha = 0.75f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!m.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = m.avatarUrl,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(72.dp).clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = initials(m.name),
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    m.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = roleC.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            m.role,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = roleC,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (!m.isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = ErrorRedSoft
                                        ) {
                                            Text(
                                                "Inactive",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ErrorRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ContactRow(Icons.Default.Email, m.email)
                        Spacer(modifier = Modifier.height(6.dp))
                        ContactRow(Icons.Default.Phone, m.phone.ifBlank { "Not provided" })
                        if (m.communityName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            ContactRow(Icons.Default.Badge, m.communityName)
                        }
                    }
                }
            }

            // ─── Stats grid ──────────────────
            item { SectionHeader("Workload & Performance", accent = AdminPurple) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BigStatTile(
                        label = "Active",
                        value = s.activeTasks.toString(),
                        subtext = "tasks",
                        color = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    BigStatTile(
                        label = "Resolved",
                        value = s.resolvedTasks.toString(),
                        subtext = "total",
                        color = PrimaryGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BigStatTile(
                        label = "Avg Assign",
                        value = if (s.avgAssignHours > 0) "${s.avgAssignHours}h" else "—",
                        subtext = "response",
                        color = AdminPurple,
                        modifier = Modifier.weight(1f)
                    )
                    BigStatTile(
                        label = "Avg Resolve",
                        value = if (s.avgResolveHours > 0) "${s.avgResolveHours}h" else "—",
                        subtext = "from assign",
                        color = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BigStatTile(
                        label = "Rating",
                        value = if (s.totalRatings > 0) String.format("%.1f", s.avgRating) else "—",
                        subtext = "${s.totalRatings} reviews",
                        color = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    BigStatTile(
                        label = "Verified",
                        value = s.verifiedCount.toString(),
                        subtext = "by YOLO",
                        color = PrimaryGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Completion bar ──────────────
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Completion Rate",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${s.completionRate}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdminPurple
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.completionRate / 100f },
                            color = AdminPurple,
                            trackColor = AdminPurpleSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // ─── Tabs ────────────────────────
            item { SectionHeader("Tasks", accent = AdminPurple) }

            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = AdminPurple
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Active",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                                if (activeProblems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CountBadge(
                                        count = activeProblems.size,
                                        selected = selectedTab == 0
                                    )
                                }
                            }
                        },
                        selectedContentColor = AdminPurple,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Resolved",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                                if (resolvedProblems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CountBadge(
                                        count = resolvedProblems.size,
                                        selected = selectedTab == 1
                                    )
                                }
                            }
                        },
                        selectedContentColor = AdminPurple,
                        unselectedContentColor = TextSecondary
                    )
                }
            }

            // ─── Problem list ────────────────
            if (currentList.isEmpty()) {
                item {
                    EmptyTasksState(
                        isActiveTab = selectedTab == 0,
                        maintainerFirstName = m.name.split(" ").firstOrNull() ?: "This maintainer"
                    )
                }
            } else {
                items(currentList, key = { it.id }) { problem ->
                    MaintainerProblemCard(
                        problem = problem,
                        showReassign = selectedTab == 0,
                        onProblemClick = { onProblemClick(problem.id) },
                        onReassignClick = {
                            problemToReassign = problem
                            showReassignSheet = true
                        }
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // Reassign sheet
    // ─────────────────────────────────────────
    if (showReassignSheet && problemToReassign != null) {
        ReassignBottomSheet(
            sessionManager = sessionManager,
            currentProblemId = problemToReassign!!.id,
            currentMaintainerId = maintainerId,
            currentMaintainerName = maintainer?.name ?: "",
            onDismiss = {
                showReassignSheet = false
                problemToReassign = null
            },
            onReassigned = {
                showReassignSheet = false
                problemToReassign = null
                Toast.makeText(context, "Task reassigned", Toast.LENGTH_SHORT).show()
                loadDetail(showSpinner = false)
            }
        )
    }
}

// ═════════════════════════════════════════════
// REUSABLE COMPONENTS
// ═════════════════════════════════════════════

@Composable
private fun SectionHeader(text: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun ContactRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun BigStatTile(
    label: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                subtext,
                fontSize = 10.sp,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int, selected: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (selected) AdminPurple else TextTertiary.copy(alpha = 0.2f)
    ) {
        Text(
            "$count",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun EmptyTasksState(
    isActiveTab: Boolean,
    maintainerFirstName: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(AdminPurpleSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Assignment,
                    contentDescription = null,
                    tint = AdminPurple,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                if (isActiveTab) "No active tasks" else "No resolved tasks yet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isActiveTab)
                    "$maintainerFirstName is fully available"
                else
                    "Tasks will appear here once resolved",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MaintainerProblemCard(
    problem: Problem,
    showReassign: Boolean,
    onProblemClick: () -> Unit,
    onReassignClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onProblemClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusAccentColor(problem.status).copy(alpha = 0.15f)
                ) {
                    Text(
                        problem.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusAccentColor(problem.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = priorityAccentColor(problem.priority).copy(alpha = 0.15f)
                ) {
                    Text(
                        problem.priority,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = priorityAccentColor(problem.priority),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    problem.category,
                    fontSize = 10.sp,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                problem.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "📍 ${problem.location}",
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${problem.reporter?.name ?: "Unknown"} (${problem.reporter?.role ?: "Resident"})",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                if (showReassign) {
                    TextButton(
                        onClick = onReassignClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = AdminPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Reassign",
                            fontSize = 11.sp,
                            color = AdminPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// REASSIGN BOTTOM SHEET
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReassignBottomSheet(
    sessionManager: SessionManager,
    currentProblemId: String,
    currentMaintainerId: String,
    currentMaintainerName: String,
    onDismiss: () -> Unit,
    onReassigned: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var maintainers by remember { mutableStateOf<List<com.example.myiapp.data.model.AdminMaintainer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedMaintainerId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        adminRepository.getMaintainers().fold(
            onSuccess = { response ->
                maintainers = response.maintainers.filter { it.id != currentMaintainerId }
            },
            onFailure = { e ->
                errorMessage = e.message ?: "Failed to load maintainers"
            }
        )
        isLoading = false
    }

    fun submit() {
        if (selectedMaintainerId.isNullOrBlank()) {
            errorMessage = "Please select a maintainer"
            return
        }
        isSubmitting = true
        errorMessage = null

        scope.launch {
            adminRepository.reassignProblem(
                problemId = currentProblemId,
                maintainerId = selectedMaintainerId!!,
                reason = reason.takeIf { it.isNotBlank() }
            ).fold(
                onSuccess = { response ->
                    isSubmitting = false
                    if (response.success) {
                        onReassigned()
                    } else {
                        errorMessage = response.message ?: "Failed to reassign"
                    }
                },
                onFailure = { e ->
                    isSubmitting = false
                    errorMessage = e.message ?: "Failed to reassign"
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .background(BorderLight, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Reassign Task",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Currently assigned to $currentMaintainerName",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AdminPurple)
                }
            } else if (maintainers.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No other maintainers available in this community.",
                        fontSize = 13.sp,
                        color = WarningAmber,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    "Select new maintainer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    maintainers.forEach { m ->
                        val selected = selectedMaintainerId == m.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) AdminPurpleSoft else SurfaceWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitting) {
                                    selectedMaintainerId = m.id
                                    errorMessage = null
                                }
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) AdminPurple else BorderLight,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) AdminPurple else SurfaceElevated
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials(m.name),
                                        color = if (selected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        m.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "${m.role} · ${m.stats.activeTasks} active",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AdminPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it; errorMessage = null },
                label = { Text("Reason (optional)") },
                placeholder = { Text("e.g., Ravi is on leave") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    cursorColor = AdminPurple
                )
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorRedSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage!!,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting && !selectedMaintainerId.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "REASSIGN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════

private fun initials(name: String): String {
    return name.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }
}

private fun roleAccentColor(role: String): Color = when (role) {
    "Admin", "SuperAdmin" -> AdminPurple
    "Staff" -> AccentBlue
    else -> PrimaryGreen
}

private fun statusAccentColor(status: String): Color = when (status) {
    "Open" -> WarningAmber
    "Assigned" -> AccentBlue
    "In Progress" -> Color(0xFF8B5CF6)
    "Resolved" -> PrimaryGreen
    "Rejected" -> ErrorRed
    else -> TextTertiary
}

private fun priorityAccentColor(priority: String): Color = when (priority) {
    "Urgent" -> Color(0xFFDC2626)
    "High" -> ErrorRed
    "Medium" -> WarningAmber
    "Low" -> PrimaryGreen
    else -> TextTertiary
}