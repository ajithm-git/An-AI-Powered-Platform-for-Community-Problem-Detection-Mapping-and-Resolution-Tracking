package com.example.myiapp.ui.screens.admin

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminMaintainer
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMaintainersScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onMaintainerClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    var maintainers by remember { mutableStateOf<List<AdminMaintainer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val token = sessionManager.getToken().orEmpty()
        if (token.isNotBlank()) {
            val result = adminRepository.getMaintainers(token)
            if (result.isSuccess) maintainers = result.getOrNull()?.maintainers ?: emptyList()
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        Box(Modifier.fillMaxWidth().background(AdminPurple).padding(24.dp)) {
            Column {
                Text("Operational Team", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("${maintainers.size} staff members online", color = Color.White.copy(0.7f), fontSize = 13.sp)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AdminPurple) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(maintainers) { m ->
                    MaintainerControlTile(m) { onMaintainerClick(m.id) }
                }
            }
        }
    }
}

@Composable
private fun MaintainerControlTile(m: AdminMaintainer, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = SurfaceWhite
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(AccentBlue.copy(0.1f)), Alignment.Center) {
                if (!m.avatarUrl.isNullOrBlank()) AsyncImage(m.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Engineering, null, tint = AccentBlue)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(m.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, Modifier.size(12.dp), tint = Color(0xFFF59E0B))
                    Text(" ${m.stats.avgRating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Text(" • ${m.stats.activeTasks} active tasks", fontSize = 12.sp, color = TextTertiary)
                }
            }
            
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BorderLight)
        }
    }
}

private val AdminPurple = Color(0xFF7C3AED)
