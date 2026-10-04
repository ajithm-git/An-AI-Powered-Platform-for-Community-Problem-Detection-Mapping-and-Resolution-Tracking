package com.example.myiapp.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.Community
import com.example.myiapp.data.repository.CommunityRepository
import com.example.myiapp.ui.theme.AccentBlue
import com.example.myiapp.ui.theme.BackgroundLight
import com.example.myiapp.ui.theme.BorderLight
import com.example.myiapp.ui.theme.ErrorRed
import com.example.myiapp.ui.theme.ErrorRedSoft
import com.example.myiapp.ui.theme.PrimaryGreen
import com.example.myiapp.ui.theme.PrimaryGreenDark
import com.example.myiapp.ui.theme.PrimaryGreenSoft
import com.example.myiapp.ui.theme.SurfaceWhite
import com.example.myiapp.ui.theme.TextPrimary
import com.example.myiapp.ui.theme.TextSecondary
import com.example.myiapp.ui.theme.TextTertiary
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
// Admin purple accent (matches Admin shell)
// ─────────────────────────────────────────────
private val AdminPurple = Color(0xFF7C3AED)
private val AdminPurpleDark = Color(0xFF4C1D95)
private val AdminPurpleSoft = Color(0xFFEDE9FE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommunitiesScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val communityRepository = remember { CommunityRepository() }

    // ─── State ─────────────────────────────
    var communities by remember { mutableStateOf<List<Community>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Dialog state
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCommunity by remember { mutableStateOf<Community?>(null) }
    var deletingCommunity by remember { mutableStateOf<Community?>(null) }

    // ─────────────────────────────────────────
    // Load communities (SuperAdmin view)
    // ─────────────────────────────────────────
    fun loadCommunities(showSpinner: Boolean = true) {
        if (showSpinner) isLoading = true else isRefreshing = true
        errorMessage = null

        scope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Please log in again."
                isLoading = false
                isRefreshing = false
                return@launch
            }

            val result = communityRepository.getAllCommunitiesAdmin()
            if (result.isSuccess && result.getOrNull()?.success == true) {
                communities = result.getOrNull()?.communities ?: emptyList()
            } else {
                errorMessage = result.exceptionOrNull()?.message
                    ?: "Failed to load communities"
            }
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadCommunities() }

    // Filter + sort: active first, then alphabetical
    val visibleCommunities = communities
        .filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.slug.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<Community> { it.isActive }
                .thenBy { it.name.lowercase() }
        )

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Manage Communities",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "SuperAdmin · ${communities.size} total",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { loadCommunities(showSpinner = false) },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AdminPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Community", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->

        // ─── Loading ─────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple)
            }
            return@Scaffold
        }

        // ─── Error (empty list) ─────────────
        if (errorMessage != null && communities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorRedSoft
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            errorMessage!!,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { loadCommunities() }) {
                            Text("Retry", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@Scaffold
        }

        // ─── Empty list ──────────────────────
        if (communities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(AdminPurpleSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Apartment,
                            contentDescription = null,
                            tint = AdminPurple,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "No communities yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tap the button below to create your first community.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        // ─── List with search ────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search communities…", fontSize = 13.sp, color = TextTertiary)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextTertiary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextTertiary
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite
                )
            )

            if (visibleCommunities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No communities match \"$searchQuery\"",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(visibleCommunities, key = { it.id }) { community ->
                        CommunityAdminCard(
                            community = community,
                            onEdit = { editingCommunity = community },
                            onToggle = {
                                scope.launch {
                                    val token = sessionManager.getToken() ?: return@launch
                                    val result = communityRepository.toggleCommunity(token, community.id)
                                    if (result.isSuccess && result.getOrNull()?.success == true) {
                                        val updated = result.getOrNull()?.community
                                        if (updated != null) {
                                            communities = communities.map {
                                                if (it.id == updated.id) updated else it
                                            }
                                        }
                                        Toast.makeText(
                                            context,
                                            "Community ${if (updated?.isActive == true) "activated" else "deactivated"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            result.exceptionOrNull()?.message ?: "Failed to toggle",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onDelete = { deletingCommunity = community }
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // Create dialog
    // ─────────────────────────────────────────
    if (showCreateDialog) {
        CommunityFormDialog(
            title = "New Community",
            initialName = "",
            initialDescription = "",
            initialAddress = "",
            accent = AdminPurple,
            onDismiss = { showCreateDialog = false },
            onSubmit = { name, desc, addr ->
                scope.launch {
                    val token = sessionManager.getToken() ?: return@launch
                    val result = communityRepository.createCommunity(
                        token = token,
                        name = name,
                        description = desc.takeIf { it.isNotBlank() },
                        address = addr.takeIf { it.isNotBlank() }
                    )
                    if (result.isSuccess && result.getOrNull()?.success == true) {
                        Toast.makeText(context, "Community created!", Toast.LENGTH_SHORT).show()
                        showCreateDialog = false
                        loadCommunities(showSpinner = false)
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "Failed to create",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    // ─────────────────────────────────────────
    // Edit dialog
    // ─────────────────────────────────────────
    if (editingCommunity != null) {
        CommunityFormDialog(
            title = "Edit Community",
            initialName = editingCommunity!!.name,
            initialDescription = editingCommunity!!.description,
            initialAddress = editingCommunity!!.address,
            accent = AdminPurple,
            onDismiss = { editingCommunity = null },
            onSubmit = { name, desc, addr ->
                scope.launch {
                    val token = sessionManager.getToken() ?: return@launch
                    val result = communityRepository.updateCommunity(
                        token = token,
                        communityId = editingCommunity!!.id,
                        name = name,
                        description = desc,
                        address = addr
                    )
                    if (result.isSuccess && result.getOrNull()?.success == true) {
                        Toast.makeText(context, "Community updated!", Toast.LENGTH_SHORT).show()
                        editingCommunity = null
                        loadCommunities(showSpinner = false)
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "Failed to update",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    // ─────────────────────────────────────────
    // Delete confirmation (requires typing name)
    // ─────────────────────────────────────────
    if (deletingCommunity != null) {
        DeleteCommunityDialog(
            community = deletingCommunity!!,
            onDismiss = { deletingCommunity = null },
            onConfirm = {
                scope.launch {
                    val token = sessionManager.getToken() ?: return@launch
                    val result = communityRepository.deleteCommunity(token, deletingCommunity!!.id)
                    if (result.isSuccess && result.getOrNull()?.success == true) {
                        Toast.makeText(context, "Community deleted", Toast.LENGTH_SHORT).show()
                        deletingCommunity = null
                        loadCommunities(showSpinner = false)
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "Failed to delete",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

// ═════════════════════════════════════════════
// Community admin card
// ═════════════════════════════════════════════
@Composable
private fun CommunityAdminCard(
    community: Community,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ─── Top row: avatar + name + status ─
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    try {
                                        Color(android.graphics.Color.parseColor(community.color))
                                    } catch (e: Exception) {
                                        AdminPurple
                                    },
                                    AdminPurpleDark
                                )
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Apartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        community.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (community.description.isNotBlank()) {
                        Text(
                            community.description,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (community.isActive) PrimaryGreenSoft else ErrorRedSoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (community.isActive) PrimaryGreenDark else ErrorRed,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            if (community.isActive) "Active" else "Inactive",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (community.isActive) PrimaryGreenDark else ErrorRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ─── Meta row ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = AdminPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${community.memberCount} members",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (community.address.isNotBlank()) {
                    Text(
                        "📍 ${community.address}",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF1F5F9)
            ) {
                Text(
                    "slug: ${community.slug}",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = BorderLight)

            Spacer(modifier = Modifier.height(4.dp))

            // ─── Actions ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggle) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (community.isActive) ErrorRed else PrimaryGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (community.isActive) "Deactivate" else "Activate",
                        color = if (community.isActive) ErrorRed else PrimaryGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = AdminPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete) {
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

// ═════════════════════════════════════════════
// Reusable form dialog (create + edit)
// ═════════════════════════════════════════════
@Composable
private fun CommunityFormDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    initialAddress: String,
    accent: Color,
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, address: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceWhite,
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        },
        text = {
            Column {
                if (error != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ErrorRedSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            error!!,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name *") },
                    placeholder = { Text("e.g., NIE North Campus") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Short description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        error = "Name must be at least 2 characters"
                        return@Button
                    }
                    onSubmit(name.trim(), description.trim(), address.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// ═════════════════════════════════════════════
// Delete confirmation — requires typing the name
// ═════════════════════════════════════════════
@Composable
private fun DeleteCommunityDialog(
    community: Community,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var typed by rememberSaveable { mutableStateOf("") }

    val confirmed = typed.trim().equals(community.name.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceWhite,
        title = {
            Text(
                "Delete community?",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "This will hide \"${community.name}\" from the register dropdown. " +
                            "It will fail if there are still active members.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Type the community name to confirm:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(community.name, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ErrorRed,
                        unfocusedBorderColor = BorderLight
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    disabledContainerColor = ErrorRed.copy(alpha = 0.4f)
                )
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
