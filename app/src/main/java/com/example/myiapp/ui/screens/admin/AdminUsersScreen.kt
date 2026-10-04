package com.example.myiapp.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AdminUser
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onAddUserClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val token = sessionManager.getToken().orEmpty()
        if (token.isNotBlank()) {
            val result = adminRepository.getUsers(token)
            if (result.isSuccess) users = result.getOrNull()?.users ?: emptyList()
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        // ─── Search Bar ─────────────────────
        Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 1.dp) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                placeholder = { Text("Search users...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AdminPurple) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AdminPurple, unfocusedBorderColor = BorderLight)
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AdminPurple) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(users.filter { it.name.contains(searchQuery, true) }) { user ->
                    AdminUserTile(user) { onUserClick(user.id) }
                }
            }
        }
    }
}

@Composable
private fun AdminUserTile(user: AdminUser, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = SurfaceWhite
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(AdminPurple.copy(0.1f)), Alignment.Center) {
                if (!user.avatarUrl.isNullOrBlank()) AsyncImage(user.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Black, color = AdminPurple)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(user.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(user.role, fontSize = 12.sp, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            }
            
            if (user.isActive) {
                Box(Modifier.background(PrimaryGreenSoft, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = PrimaryGreenDark)
                }
            } else {
                Box(Modifier.background(ErrorRedSoft, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("INACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = ErrorRed)
                }
            }
            
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BorderLight)
        }
    }
}

private val AdminPurple = Color(0xFF7C3AED)
