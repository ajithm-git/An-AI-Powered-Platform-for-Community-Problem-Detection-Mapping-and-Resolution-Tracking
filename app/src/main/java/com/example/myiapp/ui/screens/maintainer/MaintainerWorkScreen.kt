package com.example.myiapp.ui.screens.maintainer

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.myiapp.data.model.Problem
import com.example.myiapp.ui.navigation.Routes
import com.example.myiapp.ui.theme.*
import com.example.myiapp.ui.viewmodel.MaintainerWorkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerWorkScreen(
    navController: NavController,
    token: String,
    viewModel: MaintainerWorkViewModel = viewModel()
) {
    LaunchedEffect(token) {
        viewModel.loadMyWork(token)
    }

    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        
        // ─── Header Summary ─────────────────
        MaintainerStatsBanner(
            todo = state.notFinished.size,
            done = state.finished.size,
            onRefresh = { viewModel.refresh(token) },
            isRefreshing = state.isRefreshing
        )

        // ─── Modern Tabs ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkTabChip("PENDING", selectedTab == 0, state.notFinished.size) { selectedTab = 0 }
            WorkTabChip("COMPLETED", selectedTab == 1, state.finished.size) { selectedTab = 1 }
        }

        // ─── Content ────────────────────────
        Box(Modifier.weight(1f)) {
            val list = if (selectedTab == 0) state.notFinished else state.finished
            
            if (state.isLoading && list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
            } else if (list.isEmpty()) {
                WorkEmptyState(selectedTab == 0)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(list, key = { it.id }) { problem ->
                        MaintainerTaskCard(
                            problem = problem,
                            onClick = { navController.navigate(Routes.problemDetail(problem.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintainerStatsBanner(todo: Int, done: Int, onRefresh: () -> Unit, isRefreshing: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Operational Flow", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$todo", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AccentBlue)
                    Text(" Active Tasks", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                }
            }
            
            IconButton(onClick = onRefresh, modifier = Modifier.background(BackgroundLight, CircleShape)) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, tint = AccentBlue)
            }
        }
    }
}

@Composable
private fun WorkTabChip(label: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentBlue else SurfaceWhite,
        border = if (selected) null else BorderStroke(1.dp, BorderLight),
        modifier = Modifier.height(40.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (selected) Color.White else TextSecondary, letterSpacing = 1.sp)
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(if (selected) Color.White.copy(0.2f) else BackgroundLight, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("$count", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun MaintainerTaskCard(problem: Problem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = SurfaceWhite
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // Priority Indicator
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (problem.priority == "Urgent") ErrorRed else if (problem.priority == "High") Color(0xFFF59E0B) else Color(0xFFCBD5E1)))
            
            Spacer(Modifier.width(20.dp))
            
            Column(Modifier.weight(1f)) {
                Text(problem.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${problem.category} • ${problem.location}", fontSize = 12.sp, color = TextSecondary)
                
                if (problem.hasChecklist()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { problem.checklistProgressPercent() / 100f },
                            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                            color = AccentBlue,
                            trackColor = BackgroundLight
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${problem.checklistProgressPercent()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BorderLight)
        }
    }
}

@Composable
private fun WorkEmptyState(isToDo: Boolean) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (isToDo) Icons.Default.DoneAll else Icons.Default.Assignment, null, Modifier.size(64.dp), tint = BorderLight)
        Spacer(Modifier.height(16.dp))
        Text(if (isToDo) "Operational Excellence" else "No Completed Work", fontWeight = FontWeight.Bold, color = TextSecondary)
        Text(if (isToDo) "All assignments are currently handled." else "Complete tasks to see them here.", fontSize = 13.sp, color = TextTertiary, textAlign = TextAlign.Center)
    }
}
