package com.example.myiapp.ui.screens.maintainer

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.navigation.Routes
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MaintainerHomeScreen — Maintainer's Command Center
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Tabs:
 *  • My Work       — Priority-sorted assigned tasks
 *  • Operations    — Dashboard stats + recent activity
 *  • AI Verify     — Problems awaiting YOLO verification
 *  • Messages      — Navigate to chat
 *  • Profile       — Navigate to profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerHomeScreen(
    sessionManager: SessionManager,
    navController: NavController
) {
    val context = LocalContext.current

    var userName by remember { mutableStateOf("Maintainer") }
    var communityName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            userName = sessionManager.getName() ?: "Maintainer"
            communityName = sessionManager.getCommunityName() ?: ""
        } catch (e: Exception) {
            Toast.makeText(context, "Session error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            MaintainerHeader(
                userName = userName,
                communityName = communityName,
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        },
        bottomBar = {
            MaintainerBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onChatClick = { navController.navigate(Routes.CHATBOT) },
                onProfileClick = { navController.navigate(Routes.PROFILE) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "maintainer_tab"
            ) { tab ->
                when (tab) {
                    0 -> MaintainerWorkTab(
                        navController = navController,
                        onRefreshReady = {}
                    )
                    1 -> MaintainerOperationsTab(navController)
                    2 -> MaintainerVerifyTab(navController)
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// HEADER
// ═════════════════════════════════════════════

@Composable
private fun MaintainerHeader(
    userName: String,
    communityName: String,
    onNotificationsClick: () -> Unit
) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AccentBlue, Color(0xFF1D4ED8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Command Center",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue,
                    letterSpacing = 0.5.sp
                )
                Text(
                    userName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                if (communityName.isNotBlank()) {
                    Text(
                        communityName,
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            }

            Surface(
                onClick = onNotificationsClick,
                shape = CircleShape,
                color = BackgroundLight,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Notifications,
                        "Alerts",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// TAB 1 — MY WORK
// ═════════════════════════════════════════════

@Composable
private fun MaintainerWorkTab(
    navController: NavController,
    onRefreshReady: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProblemRepository() }

    var notFinished by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var finished by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        repository.getMyWork().fold(
            onSuccess = { response ->
                notFinished = response.notFinished
                finished = response.finished
                error = null
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load tasks"
            }
        )
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }

        error != null && notFinished.isEmpty() -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(56.dp), tint = TextTertiary)
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { scope.launch { load() } },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Retry") }
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                WorkloadSummary(
                    notFinishedCount = notFinished.size,
                    finishedCount = finished.size
                )
            }

            if (notFinished.isNotEmpty()) {
                item {
                    SectionTitle("ACTIVE TASKS (${notFinished.size})")
                }
                items(notFinished, key = { it.id }) { problem ->
                    TaskCard(problem, isFinished = false) {
                        navController.navigate(Routes.problemDetail(problem.id))
                    }
                }
            } else {
                item {
                    EmptyTasksState(
                        title = "All Caught Up!",
                        subtitle = "No active tasks assigned to you."
                    )
                }
            }

            if (finished.isNotEmpty()) {
                item {
                    SectionTitle("COMPLETED (${finished.size})", topPadding = 16.dp)
                }
                items(finished.take(15), key = { it.id }) { problem ->
                    TaskCard(problem, isFinished = true) {
                        navController.navigate(Routes.problemDetail(problem.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkloadSummary(notFinishedCount: Int, finishedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "YOUR WORKLOAD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$notFinishedCount",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentBlue
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "active",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Box(
                Modifier.width(1.dp).height(50.dp).background(BorderLight)
            )

            Spacer(Modifier.width(20.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "RESOLVED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$finishedCount",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryGreen
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "done",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(problem: Problem, isFinished: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority indicator bar
            Box(
                Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor(problem.priority))
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        problem.priority.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = priorityColor(problem.priority),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        problem.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    problem.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        Modifier.size(12.dp),
                        tint = TextTertiary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        problem.location,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                if (isFinished) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                null,
                tint = if (isFinished) PrimaryGreen else TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════
// TAB 2 — OPERATIONS (dashboard)
// ═════════════════════════════════════════════

@Composable
private fun MaintainerOperationsTab(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProblemRepository() }

    var dashboard by remember { mutableStateOf<com.example.myiapp.data.model.MaintainerDashboardResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repository.getMaintainerDashboard().fold(
            onSuccess = { dashboard = it },
            onFailure = { e ->
                Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
            }
        )
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }
        return
    }

    val stats = dashboard?.stats
    val assignedToMe = dashboard?.assignedToMe ?: emptyList()
    val highPriority = dashboard?.highPriority ?: emptyList()
    val awaitingFeedback = dashboard?.awaitingFeedback ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                color = AccentBlue
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "PERFORMANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatCell(
                            value = "${stats?.totalFeedback ?: 0}",
                            label = "Ratings",
                            modifier = Modifier.weight(1f)
                        )
                        StatCell(
                            value = String.format("%.1f", stats?.averageRating ?: 0.0),
                            label = "Avg ★",
                            modifier = Modifier.weight(1f)
                        )
                        StatCell(
                            value = "${assignedToMe.size}",
                            label = "Assigned",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (highPriority.isNotEmpty()) {
            item { SectionTitle("HIGH PRIORITY", topPadding = 4.dp) }
            items(highPriority.take(5), key = { "hp_${it.id}" }) { problem ->
                TaskCard(problem, isFinished = false) {
                    navController.navigate(Routes.problemDetail(problem.id))
                }
            }
        }

        if (awaitingFeedback.isNotEmpty()) {
            item { SectionTitle("AWAITING FEEDBACK (${awaitingFeedback.size})", topPadding = 16.dp) }
            items(awaitingFeedback.take(5), key = { "af_${it.id}" }) { problem ->
                TaskCard(problem, isFinished = true) {
                    navController.navigate(Routes.problemDetail(problem.id))
                }
            }
        }

        if (assignedToMe.isNotEmpty()) {
            item { SectionTitle("RECENT ASSIGNMENTS", topPadding = 16.dp) }
            items(assignedToMe.take(10), key = { "am_${it.id}" }) { problem ->
                TaskCard(problem, isFinished = false) {
                    navController.navigate(Routes.problemDetail(problem.id))
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ═════════════════════════════════════════════
// TAB 3 — AI VERIFY (needs verification)
// ═════════════════════════════════════════════

@Composable
private fun MaintainerVerifyTab(navController: NavController) {
    val repository = remember { ProblemRepository() }
    var problems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repository.getAssignedProblems().fold(
            onSuccess = { response ->
                // Only show In Progress tasks that need verification
                problems = response.problems.filter {
                    it.status == "In Progress" || it.status == "Assigned"
                }
            },
            onFailure = { }
        )
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                color = PrimaryGreenSoft
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = PrimaryGreenDark,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "AI Verification",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreenDark
                        )
                        Text(
                            "Capture an after-photo to trigger YOLO verification",
                            fontSize = 12.sp,
                            color = PrimaryGreenDark.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (problems.isEmpty()) {
            item {
                EmptyTasksState(
                    title = "Nothing to Verify",
                    subtitle = "No in-progress tasks awaiting verification."
                )
            }
        } else {
            item { SectionTitle("AWAITING VERIFICATION (${problems.size})", topPadding = 4.dp) }
            items(problems, key = { it.id }) { problem ->
                TaskCard(problem, isFinished = false) {
                    navController.navigate(Routes.problemDetail(problem.id))
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// BOTTOM NAVIGATION
// ═════════════════════════════════════════════

@Composable
private fun MaintainerBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MaintainerNavItem(
                icon = Icons.Default.Assignment,
                label = "My Work",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )
            MaintainerNavItem(
                icon = Icons.Default.Dashboard,
                label = "Operations",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )
            MaintainerNavItem(
                icon = Icons.Default.Verified,
                label = "AI Verify",
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
            MaintainerNavItem(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = "Messages",
                selected = false,
                onClick = onChatClick
            )
            MaintainerNavItem(
                icon = Icons.Outlined.Person,
                label = "Profile",
                selected = false,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
private fun MaintainerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) AccentBlue else TextTertiary
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = weight,
            color = color,
            maxLines = 1
        )
    }
}

// ═════════════════════════════════════════════
// SHARED HELPERS
// ═════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String, topPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = TextTertiary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = topPadding,
            bottom = 10.dp
        )
    )
}

@Composable
private fun EmptyTasksState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.TaskAlt,
            null,
            modifier = Modifier.size(64.dp),
            tint = BorderLight
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = TextTertiary)
    }
}

private fun priorityColor(priority: String): Color = when (priority) {
    "Urgent" -> ErrorRed
    "High" -> WarningAmber
    "Medium" -> AccentBlue
    "Low" -> TextTertiary
    else -> TextTertiary
}