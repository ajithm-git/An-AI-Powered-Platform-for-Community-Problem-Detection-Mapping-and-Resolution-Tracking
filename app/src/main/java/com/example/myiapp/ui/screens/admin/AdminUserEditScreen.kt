package com.example.myiapp.ui.screens.admin

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.CommunityData
import com.example.myiapp.data.repository.AdminRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch   // ← THE MISSING IMPORT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserEditScreen(
    sessionManager: SessionManager,
    userId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }

    val isCreateMode = userId.isNullOrBlank()

    // ─── Form state ──────────────────────────
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var selectedRole by rememberSaveable { mutableStateOf("Resident") }
    var selectedCommunityId by rememberSaveable { mutableStateOf<String?>(null) }
    var isActive by rememberSaveable { mutableStateOf(true) }

    // Snapshot for dirty-check (edit mode)
    var savedName by remember { mutableStateOf("") }
    var savedPhone by remember { mutableStateOf("") }
    var savedRole by remember { mutableStateOf("") }

    // Dropdowns
    var roleExpanded by remember { mutableStateOf(false) }
    var communityExpanded by remember { mutableStateOf(false) }

    // Communities
    var communities by remember { mutableStateOf<List<CommunityData>>(emptyList()) }

    // UI state
    var isFetching by remember { mutableStateOf(!isCreateMode) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Caller's role
    var myRole by remember { mutableStateOf("Admin") }

    val editableRoles = listOf("Resident", "Staff", "Admin")

    // ─────────────────────────────────────────
    // Load communities + user
    // ─────────────────────────────────────────
    LaunchedEffect(userId) {
        myRole = sessionManager.getRole() ?: "Admin"

        // Load communities
        if (myRole == "SuperAdmin") {
            adminRepository.getAllCommunitiesAdmin().fold(
                onSuccess = { response ->
                    communities = response.communities
                },
                onFailure = { e ->
                    errorMessage = "Failed to load communities: ${e.message}"
                }
            )
        } else {
            val cid = sessionManager.getCommunityId()
            val cname = sessionManager.getCommunityName() ?: ""
            if (cid != null) {
                communities = listOf(
                    CommunityData(id = cid, name = cname.ifBlank { "My Community" })
                )
                selectedCommunityId = cid
            }
        }

        // Load user details if edit mode
        if (!isCreateMode) {
            isFetching = true
            adminRepository.getUsers(limit = 200).fold(
                onSuccess = { response ->
                    if (response.success) {
                        val user = response.users.find { it.id == userId }
                        if (user != null) {
                            name = user.name
                            email = user.email
                            phone = user.phone
                            selectedRole = user.role
                            selectedCommunityId = user.communityId
                            isActive = user.isActive

                            savedName = user.name
                            savedPhone = user.phone
                            savedRole = user.role
                        } else {
                            errorMessage = "User not found"
                        }
                    } else {
                        errorMessage = response.message ?: "Failed to load user"
                    }
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to load user"
                }
            )
            isFetching = false
        }
    }

    // ─────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────
    fun validate(): String? {
        if (name.trim().length < 2) return "Name must be at least 2 characters"
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return "Please enter a valid email address"
        if (!phone.trim().matches(Regex("^[0-9+\\-\\s()]{7,20}$")))
            return "Please enter a valid phone number"
        if (isCreateMode && password.length < 6)
            return "Password must be at least 6 characters"
        if (selectedRole == "Admin" && myRole != "SuperAdmin")
            return "Only SuperAdmin can create Admin accounts"
        if (communities.isNotEmpty() && selectedCommunityId.isNullOrBlank())
            return "Please select a community"
        return null
    }

    // ─────────────────────────────────────────
    // Save — wrapped in scope.launch
    // ─────────────────────────────────────────
    fun save() {
        val err = validate()
        if (err != null) {
            errorMessage = err
            return
        }
        errorMessage = null
        isSaving = true

        scope.launch {
            if (isCreateMode) {
                adminRepository.createUser(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    phone = phone.trim(),
                    password = password,
                    role = selectedRole,
                    communityId = selectedCommunityId
                ).fold(
                    onSuccess = { response ->
                        isSaving = false
                        if (response.success) {
                            Toast.makeText(context, "User created", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            errorMessage = response.message ?: "Failed to create user"
                        }
                    },
                    onFailure = { e ->
                        isSaving = false
                        errorMessage = e.message ?: "Failed to create user"
                    }
                )
            } else {
                adminRepository.updateUser(
                    userId = userId!!,
                    name = name.trim(),
                    phone = phone.trim(),
                    role = selectedRole,
                    communityId = selectedCommunityId,
                    isActive = isActive
                ).fold(
                    onSuccess = { response ->
                        isSaving = false
                        if (response.success) {
                            Toast.makeText(context, "User updated", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            errorMessage = response.message ?: "Failed to update user"
                        }
                    },
                    onFailure = { e ->
                        isSaving = false
                        errorMessage = e.message ?: "Failed to update user"
                    }
                )
            }
        }
    }

    // ─────────────────────────────────────────
    // Delete — wrapped in scope.launch
    // ─────────────────────────────────────────
    fun performDelete() {
        if (userId.isNullOrBlank()) return
        isDeleting = true

        scope.launch {
            adminRepository.deleteUser(userId).fold(
                onSuccess = { response ->
                    isDeleting = false
                    if (response.success) {
                        Toast.makeText(context, "User deactivated", Toast.LENGTH_LONG).show()
                        onDeleted()
                    } else {
                        errorMessage = response.message ?: "Failed to delete user"
                    }
                },
                onFailure = { e ->
                    isDeleting = false
                    errorMessage = e.message ?: "Failed to delete user"
                }
            )
        }
    }

    // ─────────────────────────────────────────
    // Dirty check for save button
    // ─────────────────────────────────────────
    val hasChanges: Boolean = if (isCreateMode) {
        name.isNotBlank() || email.isNotBlank() || phone.isNotBlank() ||
                password.isNotBlank() || selectedRole != "Resident"
    } else {
        name.trim() != savedName ||
                phone.trim() != savedPhone ||
                selectedRole != savedRole
    }

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
                    IconButton(onClick = onBack, enabled = !isSaving && !isDeleting) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isCreateMode) "Add User" else "Edit User",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (isCreateMode) "New account will receive OTP"
                            else "Email cannot be changed",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isFetching) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AdminPurple)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── Info banner ───────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AdminPurpleSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = AdminPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isCreateMode)
                            "A verification code will be sent to the email. User must verify before logging in."
                        else
                            "Editing user details. Email cannot be changed.",
                        fontSize = 12.sp,
                        color = AdminPurpleDark,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Name ──────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text("Full Name") },
                placeholder = { Text("John Doe") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isSaving && !isDeleting,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Email ─────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                placeholder = { Text("john@example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TextTertiary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = isCreateMode && !isSaving && !isDeleting,
                readOnly = !isCreateMode,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = if (!isCreateMode) SurfaceElevated else SurfaceWhite,
                    unfocusedContainerColor = if (!isCreateMode) SurfaceElevated else SurfaceWhite,
                    disabledContainerColor = SurfaceElevated,
                    disabledBorderColor = BorderLight,
                    disabledTextColor = TextPrimary,
                    disabledLabelColor = TextSecondary,
                    disabledLeadingIconColor = TextTertiary,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Phone ─────────────────────────
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; errorMessage = null },
                label = { Text("Phone") },
                placeholder = { Text("+91 98765 43210") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = TextTertiary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                enabled = !isSaving && !isDeleting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AdminPurple,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    cursorColor = AdminPurple
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Password (create only) ────────
            if (isCreateMode) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    placeholder = { Text("Min 6 characters") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextTertiary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    enabled = !isSaving && !isDeleting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminPurple,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        cursorColor = AdminPurple
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Role dropdown ─────────────────
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = {
                    if (!isSaving && !isDeleting) roleExpanded = !roleExpanded
                }
            ) {
                OutlinedTextField(
                    value = selectedRole,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = TextTertiary)
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSaving && !isDeleting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdminPurple,
                        unfocusedBorderColor = BorderLight,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )
                ExposedDropdownMenu(
                    expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false }
                ) {
                    editableRoles.forEach { role ->
                        val isAllowed = !(role == "Admin" && myRole != "SuperAdmin")
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(role)
                                    if (!isAllowed) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "(SuperAdmin only)",
                                            fontSize = 10.sp,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (isAllowed) {
                                    selectedRole = role
                                    roleExpanded = false
                                }
                            },
                            enabled = isAllowed
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Community dropdown ────────────
            val showCommunityDropdown = communities.size > 1 || myRole == "SuperAdmin"

            if (showCommunityDropdown && communities.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = communityExpanded,
                    onExpandedChange = {
                        if (!isSaving && !isDeleting && myRole == "SuperAdmin")
                            communityExpanded = !communityExpanded
                    }
                ) {
                    val selectedCommunityName = communities
                        .find { it.id == selectedCommunityId }?.name ?: "Select community"

                    OutlinedTextField(
                        value = selectedCommunityName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Community") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Apartment,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = communityExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSaving && !isDeleting && myRole == "SuperAdmin",
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdminPurple,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = if (myRole != "SuperAdmin") SurfaceElevated else SurfaceWhite,
                            unfocusedContainerColor = if (myRole != "SuperAdmin") SurfaceElevated else SurfaceWhite,
                            disabledContainerColor = SurfaceElevated,
                            disabledBorderColor = BorderLight,
                            disabledTextColor = TextPrimary,
                            disabledLabelColor = TextSecondary,
                            disabledLeadingIconColor = TextTertiary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = communityExpanded,
                        onDismissRequest = { communityExpanded = false }
                    ) {
                        communities.forEach { community ->
                            DropdownMenuItem(
                                text = { Text(community.name) },
                                onClick = {
                                    selectedCommunityId = community.id
                                    communityExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Active toggle (edit only) ─────
            if (!isCreateMode) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (isActive) PrimaryGreen else TextTertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Account Active",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                if (isActive) "User can log in"
                                else "User is deactivated (cannot log in)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            enabled = !isSaving && !isDeleting,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AdminPurple,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = BorderLight
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Error banner ──────────────────
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRedSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage!!,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ─── Save button ───────────────────
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSaving && !isDeleting && hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AdminPurple,
                    disabledContainerColor = AdminPurple.copy(alpha = 0.4f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isCreateMode) "CREATE USER" else "SAVE CHANGES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // ─── Deactivate button (edit only) ─
            if (!isCreateMode) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSaving && !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = ErrorRed,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "DEACTIVATE USER",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ─────────────────────────────────────────
    // Deactivate confirm
    // ─────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = SurfaceWhite,
            title = {
                Text("Deactivate user?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "\"$name\" will be deactivated. They will no longer be able to log in. " +
                            "You can reactivate by editing and toggling Account Active back on.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        performDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Deactivate", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}