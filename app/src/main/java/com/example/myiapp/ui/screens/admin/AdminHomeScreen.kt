package com.example.myiapp.ui.screens.admin

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminMaintainer
import com.example.myiapp.data.model.AdminStats
import com.example.myiapp.data.model.AdminUser
import com.example.myiapp.data.model.CommunityData
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.navigation.Routes
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AdminHomeScreen — Admin & SuperAdmin Command Center
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Tabs (5):
 *  • Insight  — Dashboard KPIs
 *  • Issues   — All problems in the community
 *  • Users    — Manage residents/staff/admins
 *  • Team     — Maintainer workload & performance
 *  • Regions  — Community management (SuperAdmin only)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    sessionManager: SessionManager,
    navController: NavController
) {
    val context = LocalContext.current

    var userName by remember { mutableStateOf("Admin") }
    var userRole by remember { mutableStateOf("Admin") }
    var communityName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            userName = sessionManager.getName() ?: "Admin"
            userRole = sessionManager.getRole() ?: "Admin"
            communityName = sessionManager.getCommunityName() ?: ""
        } catch (e: Exception) {
            Toast.makeText(context, "Session error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val isSuperAdmin = userRole == "SuperAdmin"

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            AdminHeader(
                userName = userName,
                userRole = userRole,
                communityName = communityName,
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        },
        bottomBar = {
            AdminBottomNav(
                selectedTab = selectedTab,
                isSuperAdmin = isSuperAdmin,
                onTabSelected = { selectedTab = it },
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
                label = "admin_tab"
            ) { tab ->
                when (tab) {
                    0 -> AdminInsightTab(
                        navController = navController,
                        onNavigateToIssuesTab = { selectedTab = 1 }
                    )
                    1 -> AdminIssuesTab(navController)
                    2 -> AdminUsersTab(navController)
                    3 -> AdminTeamTab(navController)
                    4 -> {
                        if (isSuperAdmin) AdminRegionsTab(navController)
                        else Box(Modifier.fillMaxSize())
                    }
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
private fun AdminHeader(
    userName: String,
    userRole: String,
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
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AdminPurple.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AdminPurple, AdminPurpleDark)
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
                    userRole.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = AdminPurple,
                    letterSpacing = 1.sp
                )
                Text(
                    userName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
// TAB 1 — INSIGHT (Dashboard)
// ═════════════════════════════════════════════

@Composable
private fun AdminInsightTab(
    navController: NavController,
    onNavigateToIssuesTab: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { AdminRepository() }

    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        repository.getDashboard().fold(
            onSuccess = { response ->
                if (response.success && response.stats != null) {
                    stats = response.stats
                    error = null
                } else {
                    error = response.message ?: "Failed to load dashboard"
                }
            },
            onFailure = { e ->
                error = e.message ?: "Network error"
            }
        )
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdminPurple)
        }

        error != null && stats == null -> Column(
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
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) { Text("Retry") }
        }

        else -> {
            val s = stats!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    // Hero card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = AdminPurple
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                "COMMUNITY HEALTH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.8f),
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row {
                                HeroStat(
                                    value = "${s.problems.total}",
                                    label = "Total",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStat(
                                    value = "${s.problems.open + s.problems.inProgress}",
                                    label = "Active",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStat(
                                    value = "${s.problems.resolved}",
                                    label = "Resolved",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ─── USERS ──────────────────────
                item { SectionTitle("USERS") }
                item {
                    Row(Modifier.padding(horizontal = 20.dp)) {
                        StatCard(
                            icon = Icons.Default.Group,
                            value = "${s.users.total}",
                            label = "Total",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        StatCard(
                            icon = Icons.Default.Home,
                            value = "${s.users.residents}",
                            label = "Residents",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row(Modifier.padding(horizontal = 20.dp)) {
                        StatCard(
                            icon = Icons.Default.Build,
                            value = "${s.users.staff}",
                            label = "Staff",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        StatCard(
                            icon = Icons.Default.AdminPanelSettings,
                            value = "${s.users.admins}",
                            label = "Admins",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ─── PERFORMANCE ────────────────
                item { SectionTitle("PERFORMANCE", topPadding = 20.dp) }
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceWhite,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            PerformanceRow(
                                label = "Avg Assign Time",
                                value = "${s.performance.avgAssignHours}h"
                            )
                            Spacer(Modifier.height(12.dp))
                            PerformanceRow(
                                label = "Avg Resolution Time",
                                value = "${s.performance.avgResolveHours}h"
                            )
                            Spacer(Modifier.height(12.dp))
                            PerformanceRow(
                                label = "Avg Rating",
                                value = "${s.performance.avgRating} ★ (${s.performance.totalRatings})"
                            )
                            Spacer(Modifier.height(12.dp))
                            PerformanceRow(
                                label = "AI Verification Rate",
                                value = "${s.performance.verificationRate}%"
                            )
                        }
                    }
                }

                // ─── ATTENTION ──────────────────
                if (s.problems.highPriorityOpen > 0 || s.problems.pendingFeedback > 0) {
                    item { SectionTitle("ATTENTION", topPadding = 20.dp) }

                    if (s.problems.highPriorityOpen > 0) {
                        item {
                            AlertCard(
                                icon = Icons.Default.PriorityHigh,
                                color = ErrorRed,
                                title = "${s.problems.highPriorityOpen} high priority issues",
                                subtitle = "Tap to review in Issues tab",
                                onClick = onNavigateToIssuesTab
                            )
                        }
                    }
                    if (s.problems.pendingFeedback > 0) {
                        item {
                            AlertCard(
                                icon = Icons.Default.StarBorder,
                                color = WarningAmber,
                                title = "${s.problems.pendingFeedback} awaiting feedback",
                                subtitle = "Residents haven't rated yet",
                                onClick = onNavigateToIssuesTab
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = AdminPurple, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun PerformanceRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun AlertCard(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

// ═════════════════════════════════════════════
// TAB 2 — ISSUES
// ═════════════════════════════════════════════

@Composable
private fun AdminIssuesTab(navController: NavController) {
    val repository = remember { ProblemRepository() }
    var problems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repository.getProblems(limit = 50).fold(
            onSuccess = { problems = it.problems },
            onFailure = { }
        )
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdminPurple)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { SectionTitle("ALL PROBLEMS (${problems.size})") }

        if (problems.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, null, Modifier.size(64.dp), tint = BorderLight)
                    Spacer(Modifier.height(12.dp))
                    Text("No problems yet", color = TextSecondary)
                }
            }
        } else {
            items(problems, key = { it.id }) { problem ->
                AdminProblemRow(problem) {
                    navController.navigate(Routes.problemDetail(problem.id))
                }
            }
        }
    }
}

@Composable
private fun AdminProblemRow(problem: Problem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor(problem.status))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    problem.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${problem.status} • ${problem.location}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

// ═════════════════════════════════════════════
// TAB 3 — USERS
// ═════════════════════════════════════════════

@Composable
private fun AdminUsersTab(navController: NavController) {
    val repository = remember { AdminRepository() }
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var roleFilter by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        repository.getUsers(role = roleFilter, limit = 100).fold(
            onSuccess = { users = it.users },
            onFailure = { }
        )
        isLoading = false
    }

    LaunchedEffect(roleFilter) { load() }

    Column(Modifier.fillMaxSize()) {
        // Role filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = roleFilter == null,
                onClick = { roleFilter = null },
                label = { Text("All") }
            )
            FilterChip(
                selected = roleFilter == "Resident",
                onClick = { roleFilter = "Resident" },
                label = { Text("Residents") }
            )
            FilterChip(
                selected = roleFilter == "Staff",
                onClick = { roleFilter = "Staff" },
                label = { Text("Staff") }
            )
            FilterChip(
                selected = roleFilter == "Admin",
                onClick = { roleFilter = "Admin" },
                label = { Text("Admins") }
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdminPurple)
            }
        } else if (users.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PersonOff, null, Modifier.size(56.dp), tint = BorderLight)
                Spacer(Modifier.height(12.dp))
                Text("No users found", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    AdminUserRow(user) {
                        navController.navigate(Routes.adminUserEdit(user.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserRow(user: AdminUser, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(roleSoftColor(user.role)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.name.take(1).uppercase(),
                    color = roleColor(user.role),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    user.email,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = roleSoftColor(user.role)
            ) {
                Text(
                    roleLabel(user.role),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = roleColor(user.role)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// TAB 4 — TEAM (Maintainers)
// ═════════════════════════════════════════════

@Composable
private fun AdminTeamTab(navController: NavController) {
    val repository = remember { AdminRepository() }
    var maintainers by remember { mutableStateOf<List<AdminMaintainer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        repository.getMaintainers().fold(
            onSuccess = { maintainers = it.maintainers },
            onFailure = { }
        )
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdminPurple)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { SectionTitle("MAINTAINERS (${maintainers.size})") }

        if (maintainers.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Engineering, null, Modifier.size(64.dp), tint = BorderLight)
                    Spacer(Modifier.height(12.dp))
                    Text("No maintainers assigned", color = TextSecondary)
                }
            }
        } else {
            items(maintainers, key = { it.id }) { maintainer ->
                MaintainerRow(maintainer) {
                    navController.navigate(Routes.adminMaintainerDetail(maintainer.id))
                }
            }
        }
    }
}

@Composable
private fun MaintainerRow(maintainer: AdminMaintainer, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AdminPurpleSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        maintainer.name.take(1).uppercase(),
                        color = AdminPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        maintainer.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        "${maintainer.role} • ${maintainer.email}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (maintainer.stats.activeTasks >= 5) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ErrorRedSoft
                    ) {
                        Text(
                            "BUSY",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = ErrorRed
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniStat("Active", "${maintainer.stats.activeTasks}")
                MiniStat("Resolved", "${maintainer.stats.resolvedTasks}")
                MiniStat("Rating", String.format("%.1f★", maintainer.stats.avgRating))
                MiniStat("Done %", "${maintainer.stats.completionRate}%")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 10.sp, color = TextTertiary)
    }
}

// ═════════════════════════════════════════════
// TAB 5 — REGIONS (SuperAdmin only)
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminRegionsTab(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AdminRepository() }

    var communities by remember { mutableStateOf<List<CommunityData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Create community dialog
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<CommunityData?>(null) }

    suspend fun load(silent: Boolean = false) {
        if (!silent) isLoading = true else isRefreshing = true
        error = null

        repository.getAllCommunitiesAdmin().fold(
            onSuccess = { response ->
                communities = response.communities
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load communities"
            }
        )
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(Unit) { load() }

    fun toggleCommunity(community: CommunityData) {
        scope.launch {
            repository.toggleCommunity(community.id).fold(
                onSuccess = { response ->
                    if (response.success) {
                        Toast.makeText(
                            context,
                            "${community.name} ${if (community.isActive) "deactivated" else "activated"}",
                            Toast.LENGTH_SHORT
                        ).show()
                        load(silent = true)
                    } else {
                        Toast.makeText(context, response.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: "Network error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun deleteCommunity(community: CommunityData) {
        scope.launch {
            repository.deleteCommunity(community.id).fold(
                onSuccess = { response ->
                    if (response.success) {
                        Toast.makeText(context, "Community deleted", Toast.LENGTH_SHORT).show()
                        showDeleteConfirm = null
                        load(silent = true)
                    } else {
                        Toast.makeText(context, response.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(context, e.message ?: "Network error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Scaffold(
        containerColor = BackgroundLight,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AdminPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create community")
            }
        }
    ) { padding ->
        when {
            isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple)
            }

            error != null && communities.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(56.dp), tint = TextTertiary)
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { scope.launch { load() } },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
                ) { Text("Retry") }
            }

            communities.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Apartment, null, Modifier.size(64.dp), tint = AdminPurpleSoft)
                Spacer(Modifier.height(16.dp))
                Text(
                    "No communities yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap + to create your first community",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "COMMUNITIES (${communities.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextTertiary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                scope.launch { load(silent = true) }
                            },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AdminPurple,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = AdminPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                items(communities, key = { it.id }) { community ->
                    CommunityRow(
                        community = community,
                        onToggle = { toggleCommunity(community) },
                        onDelete = { showDeleteConfirm = community }
                    )
                }
            }
        }
    }

    // ─── Create Community Dialog ─────────────
    if (showCreateDialog) {
        CreateCommunityDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, address, contactEmail ->
                scope.launch {
                    repository.createCommunity(
                        name = name,
                        description = description.takeIf { it.isNotBlank() },
                        address = address.takeIf { it.isNotBlank() },
                        contactEmail = contactEmail.takeIf { it.isNotBlank() }
                    ).fold(
                        onSuccess = { response ->
                            if (response.success) {
                                Toast.makeText(context, "Community created", Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                                load(silent = true)
                            } else {
                                Toast.makeText(
                                    context,
                                    response.message ?: "Failed to create",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                context,
                                e.message ?: "Network error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        )
    }

    // ─── Delete Confirm ─────────────────────
    showDeleteConfirm?.let { community ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceWhite,
            title = { Text("Delete community?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${community.name}\" will be permanently hidden. " +
                            "You can only delete a community with no active members.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { deleteCommunity(community) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CommunityRow(
    community: CommunityData,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AdminPurpleSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        community.name.take(1).uppercase(),
                        color = AdminPurple,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            community.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (community.isActive) PrimaryGreenSoft else ErrorRedSoft
                        ) {
                            Text(
                                if (community.isActive) "ACTIVE" else "INACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = if (community.isActive) PrimaryGreenDark else ErrorRed
                            )
                        }
                    }
                    if (community.description.isNotBlank()) {
                        Text(
                            community.description,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniStat("Members", "${community.memberCount}")
                Spacer(Modifier.width(20.dp))
                MiniStat("Problems", "${community.problemCount}")
                Spacer(Modifier.weight(1f))

                // Toggle button
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (community.isActive) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (community.isActive) "Deactivate" else "Activate",
                        tint = if (community.isActive) WarningAmber else PrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete button
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommunityDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, address: String, contactEmail: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceWhite,
        title = {
            Text("Create Community", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreating,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminPurple,
                        unfocusedBorderColor = BorderLight
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    enabled = !isCreating,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminPurple,
                        unfocusedBorderColor = BorderLight
                    )
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreating,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminPurple,
                        unfocusedBorderColor = BorderLight
                    )
                )
                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text("Contact Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreating,
                    shape = RoundedCornerShape(12.dp),
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
                    isCreating = true
                    onCreate(name.trim(), description.trim(), address.trim(), contactEmail.trim())
                },
                enabled = name.trim().length >= 2 && !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// ═════════════════════════════════════════════
// BOTTOM NAV
// ═════════════════════════════════════════════

@Composable
private fun AdminBottomNav(
    selectedTab: Int,
    isSuperAdmin: Boolean,
    onTabSelected: (Int) -> Unit,
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
            AdminNavItem(
                icon = Icons.Default.Dashboard,
                label = "Insight",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )
            AdminNavItem(
                icon = Icons.Default.Task,
                label = "Issues",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )
            AdminNavItem(
                icon = Icons.Default.Group,
                label = "Users",
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
            AdminNavItem(
                icon = Icons.Default.Engineering,
                label = "Team",
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) }
            )
            if (isSuperAdmin) {
                AdminNavItem(
                    icon = Icons.Default.Apartment,
                    label = "Regions",
                    selected = selectedTab == 4,
                    onClick = { onTabSelected(4) }
                )
            } else {
                AdminNavItem(
                    icon = Icons.Outlined.Person,
                    label = "Profile",
                    selected = false,
                    onClick = onProfileClick
                )
            }
        }
    }
}

@Composable
private fun AdminNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) AdminPurple else TextTertiary
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
// HELPERS
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