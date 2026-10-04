package com.example.myiapp.ui.screens.admin

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminStats
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToMaintainer: () -> Unit, // Renamed from Maintainers to match param name in Task
    onNavigateToProblems: () -> Unit,
    onNavigateToCommunities: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var communityName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        communityName = sessionManager.getCommunityName().orEmpty()
        val token = sessionManager.getToken().orEmpty()
        if (token.isNotBlank()) {
            val res = adminRepository.getDashboard(token)
            if (res.isSuccess) stats = res.getOrNull()?.stats
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AdminPurple) }
        } else if (stats != null) {
            val s = stats!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ─── High-Impact KPI Banner ───────
                item {
                    AdminKPIBanner(s, communityName)
                }

                // ─── Resource Grid ────────────────
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResourceCard("TOTAL USERS", s.users.total.toString(), Icons.Default.Group, AdminPurple, Modifier.weight(1f))
                        ResourceCard("ACTIVE STAFF", s.users.staff.toString(), Icons.Default.Engineering, AccentBlue, Modifier.weight(1f))
                    }
                }

                // ─── System Health ────────────────
                item {
                    SectionTitleLarge("System Integrity")
                }
                
                item {
                    HealthCard(
                        verificationRate = s.performance.verificationRate,
                        avgResolveTime = s.performance.avgResolveHours
                    )
                }

                // ─── Critical Alerts ──────────────
                if (s.problems.highPriorityOpen > 0) {
                    item {
                        AlertBanner(
                            message = "${s.problems.highPriorityOpen} CRITICAL ISSUES REQUIRE ATTENTION",
                            onClick = onNavigateToProblems
                        )
                    }
                }

                // ─── Management Grid ──────────────
                item {
                    SectionTitleLarge("Command & Control")
                }
                
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminActionRow("Issue Tracking", "Monitor and reassign tasks", Icons.Default.Task, PrimaryGreen, onNavigateToProblems)
                        AdminActionRow("User Registry", "Manage permissions and roles", Icons.Default.ManageAccounts, AdminPurple, onNavigateToUsers)
                        AdminActionRow("Team Performance", "Staff workload and ratings", Icons.Default.Assessment, Color(0xFFF59E0B), onNavigateToMaintainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKPIBanner(s: AdminStats, community: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        color = AdminPurple,
        shadowElevation = 8.dp
    ) {
        Box(Modifier.background(Brush.horizontalGradient(listOf(AdminPurple, Color(0xFF4C1D95))))) {
            Column(Modifier.padding(24.dp)) {
                Text(community.uppercase(), color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.problems.resolved}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        Text("Issues Resolved", color = Color.White.copy(0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            color = Color.White.copy(0.2f),
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        CircularProgressIndicator(
                            progress = { s.performance.verificationRate / 100f },
                            color = Color.White,
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, tint = color.copy(0.3f), modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
        }
    }
}

@Composable
private fun HealthCard(verificationRate: Int, avgResolveTime: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Efficiency Index", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$verificationRate%", color = PrimaryGreen, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { verificationRate / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = PrimaryGreen,
                trackColor = BackgroundLight
            )
            Spacer(Modifier.height(16.dp))
            Row {
                HealthMetric("AVG RESOLVE", "${avgResolveTime}h", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(BorderLight))
                HealthMetric("UPTIME", "99.9%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary)
    }
}

@Composable
private fun AlertBanner(message: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = ErrorRed
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun AdminActionRow(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(color.copy(0.1f), CircleShape), Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 12.sp, color = TextTertiary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BorderLight)
        }
    }
}

@Composable
private fun SectionTitleLarge(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = TextTertiary,
        letterSpacing = 1.5.sp
    )
}

private val AdminPurple = Color(0xFF7C3AED)
