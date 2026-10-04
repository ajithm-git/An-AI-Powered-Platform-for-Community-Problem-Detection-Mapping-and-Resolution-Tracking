package com.example.myiapp.ui.screens.resident

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.navigation.Routes
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════
//   LOCAL NAV TAB ENUM
// ═══════════════════════════════════════════════════════════════════

enum class LocalNavTab {
    HOME, CHATBOT, CAMERA, NOTIFICATIONS, PROFILE
}

// ═══════════════════════════════════════════════════════════════════
//   RESIDENT HOME SCREEN
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentHomeScreen(
    sessionManager: SessionManager,
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    val problemRepository = remember { ProblemRepository() }

    var userName by remember { mutableStateOf("Resident") }
    var communityName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    var problems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All Issues") }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    val categories = remember {
        listOf("All Issues", "Plumbing", "Lighting", "Security", "Cleanliness", "Structural")
    }

    // ─── Load data ───────────────────────────
    LaunchedEffect(Unit) {
        try {
            userName = sessionManager.getName() ?: "Resident"
            communityName = sessionManager.getCommunityName() ?: ""
            avatarUrl = sessionManager.getAvatarUrl()

            problemRepository.getProblems(limit = 50).fold(
                onSuccess = { response ->
                    problems = response.problems
                    error = null
                },
                onFailure = { e ->
                    error = e.message ?: "Failed to load problems"
                }
            )
        } catch (e: Exception) {
            error = e.message ?: "Unexpected error"
        } finally {
            isLoading = false
        }
    }

    // ─── Filters ─────────────────────────────
    val filteredProblems = remember(problems, selectedCategory) {
        if (selectedCategory == "All Issues") problems
        else problems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val activeProblems = remember(filteredProblems) {
        filteredProblems.filter { it.status != "Resolved" }
    }
    val resolvedProblems = remember(filteredProblems) {
        filteredProblems.filter { it.status == "Resolved" }
    }

    // ═════════════════════════════════════════════
    // SCAFFOLD
    // ═════════════════════════════════════════════
    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            ResidentBottomNav(
                accentColor = PrimaryGreen,
                currentTab = LocalNavTab.HOME,
                notificationCount = 0,
                onTabSelected = { tab ->
                    when (tab) {
                        LocalNavTab.HOME -> { /* already here */ }
                        LocalNavTab.CHATBOT -> navController.navigate(Routes.CHATBOT)
                        LocalNavTab.CAMERA -> navController.navigate(Routes.REPORT)
                        LocalNavTab.NOTIFICATIONS -> navController.navigate(Routes.NOTIFICATIONS)
                        LocalNavTab.PROFILE -> navController.navigate(Routes.PROFILE)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when {
                isLoading -> LoadingState()
                error != null && problems.isEmpty() -> ErrorState(
                    message = error!!,
                    onRetry = {
                        scope.launch {
                            isLoading = true
                            problemRepository.getProblems(limit = 50).fold(
                                onSuccess = {
                                    problems = it.problems
                                    error = null
                                },
                                onFailure = { error = it.message }
                            )
                            isLoading = false
                        }
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        HomeHeader(
                            greeting = greeting,
                            userName = userName,
                            avatarUrl = avatarUrl,
                            onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                            onProfileClick = { navController.navigate(Routes.PROFILE) }
                        )
                    }

                    item {
                        CommunityHeroCard(
                            communityName = communityName.ifBlank { "Community" },
                            activeCount = activeProblems.size,
                            resolvedCount = resolvedProblems.size,
                            onReportClick = { navController.navigate(Routes.REPORT) }
                        )
                    }

                    item {
                        CategoryChips(
                            categories = categories,
                            selected = selectedCategory,
                            onSelect = { selectedCategory = it }
                        )
                    }

                    if (resolvedProblems.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recently Fixed",
                                subtitle = "Fixes verified by AI",
                                paddingTop = 8.dp
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(resolvedProblems.take(5), key = { it.id }) { problem ->
                                    FixedProblemCard(problem) {
                                        navController.navigate(Routes.problemDetail(problem.id))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Active Issues",
                            subtitle = "${activeProblems.size} in your community",
                            paddingTop = 22.dp
                        )
                    }

                    if (activeProblems.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(activeProblems, key = { it.id }) { problem ->
                            ProblemCard(problem) {
                                navController.navigate(Routes.problemDetail(problem.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// HEADER
// ═════════════════════════════════════════════

@Composable
private fun HomeHeader(
    greeting: String,
    userName: String,
    avatarUrl: String?,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(PrimaryGreenSoft)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    userName.take(1).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryGreenDark
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                userName.split(" ").firstOrNull() ?: userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onNotificationsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════
// HERO CARD
// ═════════════════════════════════════════════

@Composable
private fun CommunityHeroCard(
    communityName: String,
    activeCount: Int,
    resolvedCount: Int,
    onReportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = PrimaryGreen.copy(alpha = 0.35f),
                spotColor = PrimaryGreen.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PrimaryGreen, PrimaryGreenDark)
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Apartment,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    communityName.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$activeCount Active Issues",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 28.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$resolvedCount issues fixed this month",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable { onReportClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = PrimaryGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Report",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreenDark
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// CATEGORY CHIPS
// ═════════════════════════════════════════════

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories) { category ->
            val isSelected = selected == category
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.0f else 0.98f,
                animationSpec = tween(180),
                label = "chipScale"
            )

            Surface(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(category) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) PrimaryGreen else Color.White,
                shadowElevation = if (isSelected) 6.dp else 1.dp,
                border = if (isSelected) null else BorderStroke(1.dp, BorderLight)
            ) {
                Text(
                    category,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// SECTION HEADER
// ═════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    paddingTop: androidx.compose.ui.unit.Dp = 8.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = paddingTop, bottom = 12.dp)
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 12.sp,
            color = TextTertiary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═════════════════════════════════════════════
// FIXED PROBLEM CARD
// ═════════════════════════════════════════════

@Composable
private fun FixedProblemCard(problem: Problem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .height(150.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceElevated)
            .clickable { onClick() }
    ) {
        val imageUrl = problem.afterImage?.takeIf { it.isNotBlank() }
            ?: problem.beforeImage?.takeIf { it.isNotBlank() }

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = problem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GarbagePlaceholder(modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "AI VERIFIED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryGreenDark,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                "Fixed by ${problem.assignedToName.ifBlank { "Staff" }}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                problem.title,
                fontSize = 15.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════
// PROBLEM CARD
// ═════════════════════════════════════════════

@Composable
private fun ProblemCard(problem: Problem, onClick: () -> Unit) {
    val priorityColorValue = priorityColor(problem.priority)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceElevated)
            ) {
                val imageUrl = problem.beforeImage?.takeIf { it.isNotBlank() }
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = problem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    GarbagePlaceholder(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(problem.status)
                    Spacer(Modifier.width(6.dp))
                    PriorityChip(problem.priority)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    problem.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        problem.location,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${problem.likesCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(14.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${problem.comments.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryGreenSoft
                    ) {
                        Text(
                            problem.category,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreenDark
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .fillMaxHeight()
                .background(priorityColorValue)
        )
    }
}

// ═════════════════════════════════════════════
// CHIPS
// ═════════════════════════════════════════════

@Composable
private fun StatusChip(status: String) {
    val color = statusColor(status)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            status.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun PriorityChip(priority: String) {
    val color = priorityColor(priority)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            priority.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 0.4.sp
        )
    }
}

// ═════════════════════════════════════════════
// PLACEHOLDER & STATES
// ═════════════════════════════════════════════

@Composable
private fun GarbagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = PrimaryGreenDark.copy(alpha = 0.45f),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = PrimaryGreen,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextTertiary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen,
                contentColor = Color.White
            )
        ) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PrimaryGreenSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = PrimaryGreen
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "All Clear!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "No active issues reported.",
            fontSize = 13.sp,
            color = TextTertiary
        )
    }
}

// ═════════════════════════════════════════════
// INLINE BOTTOM NAV — floating pill with center FAB
// ═════════════════════════════════════════════

@Composable
private fun ResidentBottomNav(
    accentColor: Color,
    currentTab: LocalNavTab,
    notificationCount: Int,
    onTabSelected: (LocalNavTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = accentColor.copy(alpha = 0.25f)
                ),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = currentTab == LocalNavTab.HOME,
                    accentColor = accentColor,
                    onClick = { onTabSelected(LocalNavTab.HOME) }
                )

                NavItem(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "AI",
                    selected = currentTab == LocalNavTab.CHATBOT,
                    accentColor = accentColor,
                    onClick = { onTabSelected(LocalNavTab.CHATBOT) }
                )

                Spacer(modifier = Modifier.width(64.dp))

                NavItem(
                    icon = Icons.Default.Notifications,
                    label = "Alerts",
                    selected = currentTab == LocalNavTab.NOTIFICATIONS,
                    accentColor = accentColor,
                    badgeCount = notificationCount,
                    onClick = { onTabSelected(LocalNavTab.NOTIFICATIONS) }
                )

                NavItem(
                    icon = Icons.Outlined.Person,
                    label = "Profile",
                    selected = currentTab == LocalNavTab.PROFILE,
                    accentColor = accentColor,
                    onClick = { onTabSelected(LocalNavTab.PROFILE) }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(64.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = accentColor.copy(alpha = 0.5f),
                    spotColor = accentColor.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.75f))
                    )
                )
                .clickable { onTabSelected(LocalNavTab.CAMERA) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Report",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = tween(220),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(220),
        label = "iconAlpha"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 22.dp else 0.dp,
        animationSpec = tween(220),
        label = "indicatorWidth"
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accentColor else TextTertiary,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha)
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(ErrorRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accentColor else TextTertiary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) accentColor else Color.Transparent)
        )
    }
}