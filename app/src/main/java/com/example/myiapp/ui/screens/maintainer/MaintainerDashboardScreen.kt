package com.example.myiapp.ui.screens.maintainer

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.MaintainerStats
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerDashboardScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onProblemClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val problemRepository = remember { ProblemRepository() }

    var assignedToMe by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var stats by remember { mutableStateOf(MaintainerStats()) }
    var isLoading by remember { mutableStateOf(true) }
    var communityName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        communityName = sessionManager.getCommunityName().orEmpty()
        val token = sessionManager.getToken().orEmpty()
        if (token.isNotBlank()) {
            val res = problemRepository.getMaintainerDashboard()
            if (res.isSuccess) {
                val data = res.getOrNull()!!
                assignedToMe = data.assignedToMe
                stats = data.stats
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            TopAppBar(
                title = { Text("Operations Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ─── Performance Banner ───────────
                item {
                    PerformanceBanner(stats, communityName)
                }

                // ─── Operational Metrics ──────────
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("AVG RESOLUTION", "N/A", AccentBlue, Modifier.weight(1f))
                        MetricCard("REVIEWS", "${stats.totalFeedback}", Color(0xFFF59E0B), Modifier.weight(1f))
                    }
                }

                // ─── Tasks Overview ──────────────
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Urgent Actions", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = (-0.5).sp)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = CircleShape, color = ErrorRedSoft) {
                            Text(assignedToMe.count { it.priority == "Urgent" }.toString(), Modifier.padding(horizontal = 10.dp, vertical = 2.dp), color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                val urgent = assignedToMe.filter { it.priority == "Urgent" || it.priority == "High" }
                if (urgent.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { Text("No critical issues pending", color = TextTertiary, fontSize = 13.sp) } }
                } else {
                    items(urgent) { problem ->
                        DashboardProblemCard(problem) { onProblemClick(problem.id) }
                    }
                }
                
                // ─── Recent Feedback ────────────
                item {
                    Text("Quality Control", Modifier.padding(horizontal = 20.dp, vertical = 16.dp), fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                }
                
                val feedback = assignedToMe.filter { it.hasResidentFeedback() }.take(3)
                if (feedback.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) { Text("Waiting for resident ratings", color = TextTertiary, fontSize = 13.sp) } }
                } else {
                    items(feedback) { p ->
                        ResidentFeedbackCard(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceBanner(stats: MaintainerStats, community: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(Modifier.background(Brush.horizontalGradient(listOf(AccentBlue, Color(0xFF1D4ED8))))) {
            Column(Modifier.padding(24.dp)) {
                Text(community.uppercase(), color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Maintenance Rating", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (stats.totalFeedback > 0) "★ ${stats.averageRating}" else "No Ratings", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { (stats.averageRating / 5.0).toFloat() }, color = Color.White, strokeWidth = 6.dp, trackColor = Color.White.copy(0.2f), strokeCap = StrokeCap.Round, modifier = Modifier.fillMaxSize())
                        Icon(Icons.Default.TrendingUp, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun DashboardProblemCard(p: Problem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (p.priority == "Urgent") ErrorRed else Color(0xFFF59E0B), CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.location, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BorderLight)
        }
    }
}

@Composable
private fun ResidentFeedbackCard(p: Problem) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row { repeat(5) { i -> Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = if (i < (p.feedback?.rating ?: 0)) Color(0xFFF59E0B) else BorderLight) } }
                Spacer(Modifier.weight(1f))
                Text(p.feedback?.submittedAt ?: "", fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text(p.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (!p.feedback?.comment.isNullOrBlank()) {
                Text("\"${p.feedback!!.comment}\"", fontSize = 12.sp, color = TextSecondary, fontStyle = FontStyle.Italic)
            }
        }
    }
}
