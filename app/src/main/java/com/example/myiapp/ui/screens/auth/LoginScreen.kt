package com.example.myiapp.ui.screens.auth

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun LoginScreen(
    onLoginSuccess: (name: String, role: String, communityName: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val state by viewModel.loginState.collectAsState()
    val message by viewModel.message.collectAsState()

    // Local UI-only state (not persisted)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var tapCount by rememberSaveable { mutableStateOf(0) }
    var showSuperAdminTab by rememberSaveable { mutableStateOf(false) }

    val roles = remember(showSuperAdminTab) {
        if (showSuperAdminTab) listOf("Resident", "Staff", "Admin", "SuperAdmin")
        else listOf("Resident", "Staff", "Admin")
    }

    // Navigate on successful login
    LaunchedEffect(state.loginSucceeded) {
        if (state.loginSucceeded) {
            val user = state.loggedInUser
            if (user != null) {
                Toast.makeText(context, "Welcome back, ${user.name}!", Toast.LENGTH_SHORT).show()
                viewModel.consumeLoginSuccess()
                onLoginSuccess(user.name, user.role, user.communityName)
            }
        }
    }

    // Show one-shot messages (e.g. "Email verified. You can now login.")
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    fun validateInputs(): String? {
        val cleanEmail = state.email.trim()
        if (cleanEmail.isBlank() || state.password.isBlank()) return "Please enter both email and password"
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) return "Please enter a valid email address"
        return null
    }

    fun performLogin() {
        focusManager.clearFocus()
        if (validateInputs() == null) {
            viewModel.login()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrimaryGreenSoft, BackgroundLight)))
    ) {
        // Decorative background elements
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .background(PrimaryGreen.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .background(AccentTeal.copy(alpha = 0.1f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo (tap 5× to unlock SuperAdmin role in selector)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                    }
                    .background(PrimaryGradient)
                    .clickable {
                        tapCount += 1
                        if (tapCount >= 5 && !showSuperAdminTab) {
                            showSuperAdminTab = true
                            Toast.makeText(context, "SuperAdmin mode unlocked", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Apartment,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Community AI",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                "Your AI-powered community portal",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(48.dp))

            // Role selector
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.7f),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    roles.forEach { role ->
                        val isSelected = state.role == role
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) PrimaryGreen else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = !state.isLoading) {
                                    viewModel.updateLoginRole(role)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                role,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                fontSize = if (showSuperAdminTab) 10.sp else 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Email field
            AppTextField(
                value = state.email,
                onValueChange = { viewModel.updateLoginEmail(it) },
                label = "Email",
                leadingIcon = { Icon(Icons.Default.Email, null, tint = TextTertiary) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            AppTextField(
                value = state.password,
                onValueChange = { viewModel.updateLoginPassword(it) },
                label = "Password",
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextTertiary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextTertiary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { performLogin() })
            )

            // Forgot password link
            TextButton(
                onClick = onNavigateToForgot,
                modifier = Modifier.align(Alignment.End),
                enabled = !state.isLoading
            ) {
                Text(
                    "Forgot Password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryGreen
                )
            }

            // Error message
            state.error?.let { errorMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRedSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        errorMsg,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign In button
            AppButton(
                text = "Sign In",
                onClick = { performLogin() },
                isLoading = state.isLoading
            )

            Spacer(modifier = Modifier.weight(1f))

            // Register link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                TextButton(onClick = onNavigateToRegister, enabled = !state.isLoading) {
                    Text(
                        "Register",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// FALLBACK COMPONENTS
// ═════════════════════════════════════════════
// These are inline fallbacks in case AppTextField/AppButton
// haven't been extracted to a shared components file yet.
// Once you build `ui/components/AppComponents.kt`, remove these.

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
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
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = BorderLight,
            focusedLabelColor = PrimaryGreen,
            unfocusedLabelColor = TextSecondary,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryGreen,
            contentColor = Color.White,
            disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f),
            disabledContentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        )
    }
}