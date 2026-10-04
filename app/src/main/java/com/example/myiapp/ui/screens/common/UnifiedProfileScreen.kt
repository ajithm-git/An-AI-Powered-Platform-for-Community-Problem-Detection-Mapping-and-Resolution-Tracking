@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myiapp.ui.screens.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.data.repository.AuthRepository
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  UnifiedProfileScreen — Premium redesign
 * ═══════════════════════════════════════════════════════════════════
 */
@Composable
fun UnifiedProfileScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authRepository = remember { AuthRepository(sessionManager) }
    val adminRepository = remember { AdminRepository() }
    val problemRepository = remember { ProblemRepository() }

    // ─────────────────────────────────────────
    // State
    // ─────────────────────────────────────────
    var name by rememberSaveable { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var communityName by remember { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    // Role-specific stats
    var s1 by remember { mutableStateOf("—") }; var l1 by remember { mutableStateOf("Stats") }
    var s2 by remember { mutableStateOf("—") }; var l2 by remember { mutableStateOf("Stats") }
    var s3 by remember { mutableStateOf("—") }; var l3 by remember { mutableStateOf("Stats") }

    // ─────────────────────────────────────────
    // Load session + role-specific stats
    // ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        try {
            name = sessionManager.getName().orEmpty()
            email = sessionManager.getEmail().orEmpty()
            role = sessionManager.getRole().orEmpty()
            phone = sessionManager.getPhone().orEmpty()
            communityName = sessionManager.getCommunityName().orEmpty()
            avatarUrl = sessionManager.getAvatarUrl()

            when (role) {
                "Staff" -> {
                    l1 = "Active"; l2 = "Resolved"; l3 = "Rating"
                    problemRepository.getMaintainerDashboard().fold(
                        onSuccess = { d ->
                            s1 = d.assignedToMe.count { it.status != "Resolved" }.toString()
                            s2 = d.assignedToMe.count { it.status == "Resolved" }.toString()
                            s3 = String.format("%.1f", d.stats.averageRating)
                        },
                        onFailure = { }
                    )
                }
                "Admin", "SuperAdmin" -> {
                    l1 = "Users"; l2 = "Problems"; l3 = "Rating"
                    adminRepository.getDashboard().fold(
                        onSuccess = { res ->
                            val s = res.stats
                            s1 = s?.users?.total?.toString() ?: "0"
                            s2 = s?.problems?.total?.toString() ?: "0"
                            s3 = String.format("%.1f", s?.performance?.avgRating ?: 0.0)
                        },
                        onFailure = { }
                    )
                }
                else -> {
                    l1 = "Reports"; l2 = "Resolved"; l3 = "Community"
                    problemRepository.getMyProblems().fold(
                        onSuccess = { res ->
                            s1 = res.problems.size.toString()
                            s2 = res.problems.count { it.status == "Resolved" }.toString()
                            s3 = communityName.take(8).ifBlank { "Member" }
                        },
                        onFailure = { }
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoaded = true
        }
    }

    // Role-based accent colors
    val accent = when (role) {
        "Staff" -> AccentBlue
        "Admin", "SuperAdmin" -> AdminPurple
        else -> PrimaryGreen
    }
    val darkAccent = when (role) {
        "Staff" -> AccentBlueDark
        "Admin", "SuperAdmin" -> AdminPurpleDark
        else -> PrimaryGreenDark
    }
    val softAccent = when (role) {
        "Staff" -> AccentBlue.copy(alpha = 0.12f)
        "Admin", "SuperAdmin" -> AdminPurpleSoft
        else -> PrimaryGreenSoft
    }

    if (!isLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGreen)
        }
        return
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─────────────────────────────────
            // Gradient header banner
            // ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                // Main gradient background
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent, darkAccent)
                            )
                        )
                )

                // Decorative circles
                Box(
                    Modifier
                        .size(140.dp)
                        .offset(x = (-40).dp, y = (-40).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Box(
                    Modifier
                        .size(90.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-20).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                )

                // Top bar
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
                            tint = Color.White
                        )
                    }
                    Text(
                        "Profile",
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Edit / Save button
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(enabled = !isSaving) {
                                if (isEditing) {
                                    if (name.isBlank()) {
                                        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isSaving = true
                                        scope.launch {
                                            authRepository.updateProfile(
                                                name = name.trim(),
                                                phone = phone.trim()
                                            ).fold(
                                                onSuccess = {
                                                    sessionManager.updateUserInfo(name.trim(), phone.trim())
                                                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                                    isEditing = false
                                                },
                                                onFailure = { e ->
                                                    Toast.makeText(
                                                        context,
                                                        e.message ?: "Update failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                            isSaving = false
                                        }
                                    }
                                } else {
                                    isEditing = true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditing) "Save" else "Edit",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // ─── Avatar ────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            color = softAccent
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(accent, darkAccent)
                                            )
                                        )
                                ) {
                                    Text(
                                        name.take(1).uppercase(),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Name ──────────────────────────
            Text(
                name.ifBlank { "User" },
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // ─── Role badge + community ────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (role) {
                                "Staff" -> Icons.Default.Engineering
                                "Admin", "SuperAdmin" -> Icons.Default.AdminPanelSettings
                                else -> Icons.Default.Person
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            roleLabel(role).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                if (communityName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        communityName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Stats row ─────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(s1, l1, accent, Modifier.weight(1f))
                StatCard(s2, l2, accent, Modifier.weight(1f))
                StatCard(s3, l3, accent, Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))

            // ─── Contact Info ──────────────────
            InfoSection("CONTACT INFORMATION") {
                ProfileEntry(
                    icon = Icons.Default.Person,
                    label = "Full Name",
                    value = name,
                    editable = isEditing,
                    accent = accent,
                    onValueChange = { name = it }
                )
                SectionDivider()
                ProfileEntry(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = email,
                    editable = false,
                    accent = accent,
                    onValueChange = {}
                )
                SectionDivider()
                ProfileEntry(
                    icon = Icons.Default.Phone,
                    label = "Phone",
                    value = phone,
                    editable = isEditing,
                    accent = accent,
                    onValueChange = { phone = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Account Security ──────────────
            InfoSection("ACCOUNT SECURITY") {
                ActionRow(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    subtitle = "Keep your account secure",
                    accent = accent,
                    onClick = { showPasswordDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Account Info ──────────────────
            InfoSection("ACCOUNT") {
                ProfileEntry(
                    icon = Icons.Default.Info,
                    label = "Account Status",
                    value = "Active",
                    editable = false,
                    accent = accent,
                    onValueChange = {}
                )
            }

            Spacer(Modifier.height(32.dp))

            // ─── Logout ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        clip = false,
                        ambientColor = ErrorRed.copy(alpha = 0.3f),
                        spotColor = ErrorRed.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ErrorRed, Color(0xFFB91C1C))
                        )
                    )
                    .clickable(enabled = !isLoggingOut) {
                        isLoggingOut = true
                        scope.launch {
                            authRepository.logout()
                            isLoggingOut = false
                            onLogout()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "LOGOUT ACCOUNT",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Version ───────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Community AI · v3.1.0",
                    fontSize = 11.sp,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Change Password Dialog
    if (showPasswordDialog) {
        ChangePasswordDialog(
            accent = accent,
            onDismiss = { showPasswordDialog = false },
            onSubmit = { current, new ->
                scope.launch {
                    authRepository.changePassword(current, new).fold(
                        onSuccess = {
                            Toast.makeText(
                                context,
                                "Password changed successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            showPasswordDialog = false
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                context,
                                e.message ?: "Password change failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        )
    }
}

// ═════════════════════════════════════════════
// STAT CARD
// ═════════════════════════════════════════════

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                color.copy(alpha = 0.18f),
                                color.copy(alpha = 0.08f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    value.take(2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═════════════════════════════════════════════
// INFO SECTION
// ═════════════════════════════════════════════

@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryGreen)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = TextTertiary,
                letterSpacing = 1.sp
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionDivider() {
    Divider(
        modifier = Modifier.padding(start = 76.dp, end = 20.dp),
        color = BorderLight.copy(alpha = 0.5f),
        thickness = 1.dp
    )
}

// ═════════════════════════════════════════════
// PROFILE ENTRY
// ═════════════════════════════════════════════

@Composable
private fun ProfileEntry(
    icon: ImageVector,
    label: String,
    value: String,
    editable: Boolean,
    accent: Color,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.15f),
                            accent.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 11.sp,
                color = TextTertiary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))

            if (editable) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (icon) {
                            Icons.Default.Phone -> KeyboardType.Phone
                            Icons.Default.Email -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = accent
                    )
                )
            } else {
                Text(
                    value.ifBlank { "—" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// ACTION ROW
// ═════════════════════════════════════════════

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.15f),
                            accent.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(BackgroundLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════
// CHANGE PASSWORD DIALOG
// ═════════════════════════════════════════════

@Composable
private fun ChangePasswordDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onSubmit: (current: String, newPassword: String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var currentVisible by rememberSaveable { mutableStateOf(false) }
    var newVisible by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.2f),
                                    accent.copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Change Password",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Enter your current and new password",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; localError = null },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (currentVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { currentVisible = !currentVisible }) {
                            Icon(
                                if (currentVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = accent
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = if (newVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newVisible = !newVisible }) {
                            Icon(
                                if (newVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = accent
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = accent
                    )
                )

                localError?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ErrorRedSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it,
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        current.isBlank() -> localError = "Enter current password"
                        newPassword.length < 6 -> localError = "New password must be at least 6 characters"
                        newPassword != confirmPassword -> localError = "Passwords do not match"
                        else -> onSubmit(current, newPassword)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(46.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Update Password", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}