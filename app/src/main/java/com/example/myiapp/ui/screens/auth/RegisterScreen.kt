package com.example.myiapp.ui.screens.auth

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myiapp.ui.theme.*
import com.example.myiapp.ui.viewmodel.AuthViewModel
import com.example.myiapp.ui.viewmodel.OtpPurpose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val state by viewModel.registerState.collectAsState()
    val otpState by viewModel.otpState.collectAsState()
    val message by viewModel.message.collectAsState()

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var communityDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    // Load communities when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadCommunities()
    }

    // Show one-shot messages
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // Show success toast when OTP verification succeeds
    LaunchedEffect(otpState.isVerified) {
        if (otpState.isVerified && otpState.purpose == OtpPurpose.REGISTER) {
            Toast.makeText(context, "Email verified! Please login.", Toast.LENGTH_LONG).show()
            viewModel.consumeOtpVerified()
            onRegisterSuccess()
        }
    }

    fun validateInputs(): String? {
        if (state.name.isBlank() || state.email.isBlank() ||
            state.phone.isBlank() || state.password.isBlank()
        ) return "Please fill in all fields"
        if (!Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches())
            return "Please enter a valid email address"
        if (!state.phone.trim().matches(Regex("^[0-9+\\-\\s()]{7,20}$")))
            return "Please enter a valid phone number"
        if (state.password.length < 6)
            return "Password must be at least 6 characters"
        if (state.communityId.isBlank())
            return "Please select your community"
        return null
    }

    fun performRegister() {
        focusManager.clearFocus()
        if (validateInputs() == null) {
            viewModel.register()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(PrimaryGreen, PrimaryGreenDark)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Apartment,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Join Community AI",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Create an account to start contributing",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(30.dp))

            // ─────────────────────────────────────────
            // Community dropdown
            // ─────────────────────────────────────────
            when {
                state.isLoadingCommunities -> {
                    CircularProgressIndicator(
                        color = PrimaryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                state.communities.isNotEmpty() -> {
                    ExposedDropdownMenuBox(
                        expanded = communityDropdownExpanded,
                        onExpandedChange = {
                            if (!state.isLoading) {
                                communityDropdownExpanded = !communityDropdownExpanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = state.communityName.ifBlank { "Select your community" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Community") },
                            leadingIcon = {
                                Icon(Icons.Default.Apartment, null, tint = TextTertiary)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = communityDropdownExpanded
                                )
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isLoading,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = BorderLight
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = communityDropdownExpanded,
                            onDismissRequest = { communityDropdownExpanded = false }
                        ) {
                            state.communities.forEach { community ->
                                DropdownMenuItem(
                                    text = { Text(community.name) },
                                    onClick = {
                                        viewModel.updateRegisterField(
                                            communityId = community.id,
                                            communityName = community.name
                                        )
                                        communityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // No communities available — show error
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRedSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No communities available. Please try again later.",
                            color = ErrorRed,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─────────────────────────────────────────
            // Role selector
            // ─────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Resident", "Staff", "Admin").forEach { role ->
                        val isSelected = state.role == role
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) PrimaryGreen else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !state.isLoading) {
                                    viewModel.updateRegisterField(role = role)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = role,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─────────────────────────────────────────
            // Form fields
            // ─────────────────────────────────────────
            RegisterTextField(
                value = state.name,
                onValueChange = { viewModel.updateRegisterField(name = it) },
                label = "Full Name",
                leadingIcon = { Icon(Icons.Default.Person, null, tint = TextTertiary) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterTextField(
                value = state.email,
                onValueChange = { viewModel.updateRegisterField(email = it) },
                label = "Email",
                leadingIcon = { Icon(Icons.Default.Email, null, tint = TextTertiary) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterTextField(
                value = state.phone,
                onValueChange = { viewModel.updateRegisterField(phone = it) },
                label = "Phone",
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = TextTertiary) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterTextField(
                value = state.password,
                onValueChange = { viewModel.updateRegisterField(password = it) },
                label = "Password",
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextTertiary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }
                },
                enabled = !state.isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            // Error message
            state.error?.let { errorMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRedSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        errorMsg,
                        color = ErrorRed,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────
            // Create Account button
            // ─────────────────────────────────────────
            Button(
                onClick = { performRegister() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isLoading &&
                        state.communities.isNotEmpty() &&
                        !state.isLoadingCommunities,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "CREATE ACCOUNT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            TextButton(
                onClick = onBackToLogin,
                enabled = !state.isLoading
            ) {
                Text(
                    "Already have an account? Sign In",
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // ═════════════════════════════════════════════
    // OTP Dialog (shown after successful registration)
    // ═════════════════════════════════════════════
    if (otpState.isVisible && otpState.purpose == OtpPurpose.REGISTER) {
        OTPDialog(
            email = otpState.email,
            otp = otpState.otp,
            onOtpChange = { viewModel.updateOtp(it) },
            onVerify = { viewModel.verifyOtp() },
            onResend = { viewModel.resendOtp() },
            onDismiss = { viewModel.dismissOtpPopup() },
            isVerifying = otpState.isLoading,
            errorMessage = otpState.error
        )
    }
}

// ═════════════════════════════════════════════
// INLINE COMPONENTS
// ═════════════════════════════════════════════
// These are fallbacks until we extract them to
// `ui/components/AppComponents.kt` and `ui/components/OTPDialog.kt`.

@Composable
private fun RegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = BorderLight,
            focusedLabelColor = PrimaryGreen,
            unfocusedLabelColor = TextSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OTPDialog(
    email: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
    isVerifying: Boolean = false,
    errorMessage: String? = null
) {
    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(PrimaryGreenSoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Verify Your Email",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "We sent a 6-digit code to",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    email,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = otp,
                    onValueChange = { input ->
                        // Only allow digits, max 6
                        val digits = input.filter { it.isDigit() }.take(6)
                        onOtpChange(digits)
                    },
                    label = { Text("OTP Code") },
                    singleLine = true,
                    enabled = !isVerifying,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onResend,
                    enabled = !isVerifying
                ) {
                    Text(
                        "Didn't receive? Resend code",
                        color = PrimaryGreen,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onVerify,
                enabled = otp.length == 6 && !isVerifying,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("VERIFY", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isVerifying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}