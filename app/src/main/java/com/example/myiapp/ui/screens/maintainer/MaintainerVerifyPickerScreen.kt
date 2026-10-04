package com.example.myiapp.ui.screens.maintainer

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.myiapp.data.model.Problem
import com.example.myiapp.ui.navigation.Routes
import com.example.myiapp.ui.theme.*
import com.example.myiapp.ui.viewmodel.MaintainerWorkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintainerVerifyPickerScreen(
    navController: NavController,
    token: String,
    viewModel: MaintainerWorkViewModel = viewModel()
) {
    LaunchedEffect(token) {
        viewModel.loadMyWork(token)
    }

    val state by viewModel.uiState.collectAsState()
    val pending = state.notFinished

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        
        // ─── Center Header ──────────────────
        Box(
            modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White, BackgroundLight))).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).background(AccentBlue.copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Verification Center", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text("Verify resolutions using AI-powered YOLO", fontSize = 13.sp, color = TextSecondary)
            }
        }

        // ─── List ───────────────────────────
        if (state.isLoading && pending.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        } else if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), Alignment.Center) {
                Text("No tasks ready for verification.", color = TextTertiary, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    VerifyInfoCard()
                }

                items(pending, key = { it.id }) { problem ->
                    VerifyTaskCard(
                        problem = problem,
                        onClick = { navController.navigate(Routes.problemDetail(problem.id)) }
                    )
                }
                
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun VerifyInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF6FF),
        border = BorderStroke(1.dp, Color(0xFFDBEAFE))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Select a task that has a completed checklist to perform AI verification.", fontSize = 12.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VerifyTaskCard(problem: Problem, onClick: () -> Unit) {
    val ready = problem.hasChecklist() && problem.isChecklistDone()
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (ready) Color.White else Color.White.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ready) 4.dp else 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = if (ready) PrimaryGreenSoft else BackgroundLight) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Assignment, null, modifier = Modifier.size(12.dp), tint = if (ready) PrimaryGreenDark else TextTertiary)
                        Spacer(Modifier.width(6.dp))
                        Text(if (ready) "READY" else "IN PROGRESS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (ready) PrimaryGreenDark else TextTertiary)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(problem.priority.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (problem.priority == "Urgent") ErrorRed else TextTertiary)
            }
            
            Spacer(Modifier.height(16.dp))
            Text(problem.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(problem.location, fontSize = 13.sp, color = TextSecondary)
            
            if (problem.hasChecklist()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { problem.checklistProgressPercent() / 100f },
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                        color = if (ready) PrimaryGreen else AccentBlue,
                        trackColor = BackgroundLight
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${problem.workChecklist?.completedStepsCount}/${problem.workChecklist?.steps?.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
