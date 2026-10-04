package com.example.myiapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OTPDialog(
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: (() -> Unit)? = null,
    isVerifying: Boolean = false,
    verificationError: String? = null
) {
    var otpValue by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(0) }
    // Tracks the last OTP we already submitted, so we never verify twice
    var lastSubmittedOtp by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ─────────────────────────────────────────
    // Auto-focus + show keyboard on open
    // ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // ─────────────────────────────────────────
    // Resend cooldown timer (60 seconds)
    // ─────────────────────────────────────────
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown -= 1
        }
    }

    // ─────────────────────────────────────────
    // Auto-verify when 6th digit typed — but ONLY ONCE per unique OTP
    // ─────────────────────────────────────────
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6 &&
            !isVerifying &&
            lastSubmittedOtp != otpValue
        ) {
            lastSubmittedOtp = otpValue
            keyboardController?.hide()
            onVerify(otpValue)
        }
    }

    // ─────────────────────────────────────────
    // If backend returns an error, reset lastSubmittedOtp so user can retry
    // ─────────────────────────────────────────
    LaunchedEffect(verificationError) {
        if (verificationError != null) {
            lastSubmittedOtp = null
        }
    }

    // Manual verify via button
    fun submitOtp() {
        if (otpValue.length == 6 && !isVerifying && lastSubmittedOtp != otpValue) {
            lastSubmittedOtp = otpValue
            keyboardController?.hide()
            onVerify(otpValue)
        }
    }

    Dialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isVerifying,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ─────────────────────────────────────────
                // Icon Badge
                // ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(PrimaryGreen, PrimaryGreenDark)),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Verify Your Email",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We've sent a 6-digit code to your email. Enter it below to continue.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ─────────────────────────────────────────
                // OTP Input Boxes
                // ─────────────────────────────────────────
                BasicTextField(
                    value = otpValue,
                    onValueChange = { new ->
                        if (new.length <= 6 && new.all { it.isDigit() }) {
                            otpValue = new
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(6) { index ->
                                val char = otpValue.getOrNull(index)?.toString() ?: ""
                                val isFocused = otpValue.length == index && !isVerifying
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (isFocused) PrimaryGreenSoft else SurfaceElevated,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (isFocused) PrimaryGreen else BorderLight,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        style = TextStyle(textAlign = TextAlign.Center)
                                    )
                                }
                            }
                        }
                    }
                )

                // ─────────────────────────────────────────
                // Verification Error
                // ─────────────────────────────────────────
                if (verificationError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ErrorRedSoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = verificationError,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─────────────────────────────────────────
                // Verify Button
                // ─────────────────────────────────────────
                Button(
                    onClick = { submitOtp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    enabled = otpValue.length == 6 && !isVerifying
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = when {
                                    isVerifying -> Brush.linearGradient(
                                        listOf(PrimaryGreen.copy(alpha = 0.6f), PrimaryGreenDark.copy(alpha = 0.6f))
                                    )
                                    otpValue.length == 6 -> Brush.linearGradient(
                                        listOf(PrimaryGreen, PrimaryGreenDark)
                                    )
                                    else -> Brush.linearGradient(
                                        listOf(TextTertiary, TextTertiary)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "VERIFY",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ─────────────────────────────────────────
                // Resend + Cancel Row
                // ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isVerifying
                    ) {
                        Text(
                            "Cancel",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    if (onResend != null) {
                        TextButton(
                            onClick = {
                                if (resendCooldown == 0 && !isVerifying) {
                                    lastSubmittedOtp = null // allow re-verify with new OTP
                                    onResend()
                                    resendCooldown = 60
                                }
                            },
                            enabled = resendCooldown == 0 && !isVerifying
                        ) {
                            Text(
                                text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend OTP",
                                color = if (resendCooldown > 0 || isVerifying) TextTertiary else PrimaryGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}