package com.example.myiapp.ui.screens.auth

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.myiapp.ui.viewmodel.ForgotStep
import com.example.myiapp.ui.viewmodel.OtpPurpose

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val state by viewModel.forgotState.collectAsState()
    val otpState by viewModel.otpState.collectAsState()
    val message by viewModel.message.collectAsState()

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Show one-shot messages
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // When OTP verification succeeds for FORGOT_PASSWORD purpose, advance step
    LaunchedEffect(otpState.isVerified) {
        if (otpState.isVerified && otpState.purpose == OtpPurpose.FORGOT_PASSWORD) {
            // ViewModel already set step to NEW_PASSWORD after OTP verification
            viewModel.consumeOtpVerified()
        }
    }

    // Auto-navigate back to login after successful password reset
    LaunchedEffect(state.email, state.step, state.error) {
        // When the ViewModel resets forgotState to default after success,
        // step goes back to EMAIL and email is empty → indicates success
        if (state.step == ForgotStep.EMAIL && state.email.isBlank() && state.error == null) {
            // Only navigate if we were previously in a later step
            // (Handled by the reset() callback in the ViewModel instead)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // ─────────────────────────────────────────
        // Top bar
        // ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    when (state.step) {
                        ForgotStep.EMAIL -> onBackToLogin()
                        ForgotStep.OTP -> {
                            viewModel.updateForgotEmail("") // reset
                            viewModel.updateForgotNewPassword("")
                        }
                        ForgotStep.NEW_PASSWORD -> {
                            viewModel.updateForgotNewPassword("")
                            viewModel.updateForgotConfirmPassword("")
                        }
                    }
                },
                enabled = !state.isLoading
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                "Reset Password",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ─────────────────────────────────────────
            // Step indicator
            // ─────────────────────────────────────────
            StepIndicator(currentStep = state.step)

            Spacer(modifier = Modifier.height(32.dp))

            // ─────────────────────────────────────────
            // Step 1: Email
            // ─────────────────────────────────────────
            when (state.step) {
                ForgotStep.EMAIL -> {
                    Text(
                        "Forgot Password?",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enter your email and we'll send you an OTP to reset your password.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    ForgotTextField(
                        value = state.email,
                        onValueChange = { viewModel.updateForgotEmail(it) },
                        label = "Email",
                        leadingIcon = {
                            Icon(Icons.Default.Email, null, tint = TextTertiary)
                        },
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.sendForgotPasswordOtp() }
                        )
                    )

                    state.error?.let { err ->
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorBanner(message = err)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    PrimaryButton(
                        text = "SEND OTP",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.sendForgotPasswordOtp()
                        },
                        isLoading = state.isLoading,
                        enabled = state.email.isNotBlank()
                    )
                }

                // ─────────────────────────────────────────
                // Step 2: OTP sent — waiting for user
                // ─────────────────────────────────────────
                ForgotStep.OTP -> {
                    Text(
                        "Enter OTP",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "We sent a 6-digit code to ${state.email}. Enter it below to continue.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    ForgotTextField(
                        value = otpState.otp,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(6)
                            viewModel.updateOtp(digits)
                        },
                        label = "OTP Code",
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = TextTertiary)
                        },
                        enabled = !otpState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.verifyOtp() }
                        )
                    )

                    otpState.error?.let { err ->
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorBanner(message = err)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { viewModel.resendOtp() },
                        enabled = !otpState.isLoading
                    ) {
                        Text(
                            "Didn't receive? Resend code",
                            color = PrimaryGreen,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PrimaryButton(
                        text = "VERIFY OTP",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.verifyOtp()
                        },
                        isLoading = otpState.isLoading,
                        enabled = otpState.otp.length == 6
                    )
                }

                // ─────────────────────────────────────────
                // Step 3: New password
                // ─────────────────────────────────────────
                ForgotStep.NEW_PASSWORD -> {
                    Text(
                        "New Password",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose a strong password for your account.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    ForgotTextField(
                        value = state.newPassword,
                        onValueChange = { viewModel.updateForgotNewPassword(it) },
                        label = "New Password",
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = TextTertiary)
                        },
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ForgotTextField(
                        value = state.confirmPassword,
                        onValueChange = { viewModel.updateForgotConfirmPassword(it) },
                        label = "Confirm Password",
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = TextTertiary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    if (confirmPasswordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextTertiary
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.resetPassword() }
                        )
                    )

                    state.error?.let { err ->
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorBanner(message = err)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    PrimaryButton(
                        text = "RESET PASSWORD",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.resetPassword()
                        },
                        isLoading = state.isLoading,
                        enabled = state.newPassword.length >= 6 &&
                                state.newPassword == state.confirmPassword
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onBackToLogin,
                enabled = !state.isLoading
            ) {
                Text(
                    "Back to Login",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// STEP INDICATOR
// ═════════════════════════════════════════════

@Composable
private fun StepIndicator(currentStep: ForgotStep) {
    val steps = listOf(ForgotStep.EMAIL, ForgotStep.OTP, ForgotStep.NEW_PASSWORD)
    val currentIndex = steps.indexOf(currentStep)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, _ ->
            val isCompleted = index < currentIndex
            val isActive = index == currentIndex

            Box(
                modifier = Modifier
                    .size(if (isActive) 14.dp else 10.dp)
                    .background(
                        color = when {
                            isCompleted -> PrimaryGreen
                            isActive -> PrimaryGreen
                            else -> BorderLight
                        },
                        shape = CircleShape
                    )
            )

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(
                            color = if (index < currentIndex) PrimaryGreen else BorderLight
                        )
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// INLINE COMPONENTS
// ═════════════════════════════════════════════

@Composable
private fun ForgotTextField(
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
            unfocusedLabelColor = TextSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrimaryButton(
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
            disabledContainerColor = PrimaryGreen.copy(alpha = 0.4f),
            disabledContentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ErrorRedSoft,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            color = ErrorRed,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}