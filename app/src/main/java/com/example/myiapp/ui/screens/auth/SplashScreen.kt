package com.example.myiapp.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myiapp.ui.theme.*
import com.example.myiapp.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

/**
 * ═══════════════════════════════════════════════════════════════════
 *  SplashScreen — app entry point
 * ═══════════════════════════════════════════════════════════════════
 *
 *  1. Plays a subtle entry animation
 *  2. Checks the local session (JWT in DataStore)
 *  3. Verifies backend health
 *  4. Routes to Login or role-specific home
 *
 *  Role-based navigation:
 *    • SuperAdmin / Admin → admin_home
 *    • Staff             → maintainer_home
 *    • Resident          → resident_home
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToResidentHome: () -> Unit,
    onNavigateToMaintainerHome: () -> Unit,
    onNavigateToAdminHome: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val sessionState by viewModel.sessionState.collectAsState()

    // ─────────────────────────────────────────
    // Entry animation
    // ─────────────────────────────────────────
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing
            )
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900)
        )
    }

    // ─────────────────────────────────────────
    // Restore session + check backend
    // ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        // Give the animation a moment
        delay(300)

        // Restore the JWT from DataStore and push it into RetrofitClient
        viewModel.checkSession()

        // Optional — verify backend reachability
        // If backend is down, still route to Login (user will see error)
        viewModel.checkBackendHealth { isUp ->
            // Logged in console from ViewModel; no UI action here
        }
    }

    // ─────────────────────────────────────────
    // Route based on session state
    // ─────────────────────────────────────────
    LaunchedEffect(sessionState.isChecking, sessionState.isLoggedIn, sessionState.role) {
        if (sessionState.isChecking) return@LaunchedEffect

        // Small delay so the user sees the logo before routing
        delay(400)

        if (sessionState.isLoggedIn && !sessionState.role.isNullOrBlank()) {
            when (sessionState.role) {
                "SuperAdmin", "Admin" -> onNavigateToAdminHome()
                "Staff" -> onNavigateToMaintainerHome()
                "Resident" -> onNavigateToResidentHome()
                else -> onNavigateToLogin()
            }
        } else {
            onNavigateToLogin()
        }
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PrimaryGreenSoft,
                        BackgroundLight,
                        BackgroundLight
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background circles
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-150).dp, y = (-200).dp)
                .background(PrimaryGreen.copy(alpha = 0.08f), RoundedCornerShape(50))
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 150.dp)
                .background(AccentTeal.copy(alpha = 0.08f), RoundedCornerShape(50))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ─────────────────────────────────
            // Logo
            // ─────────────────────────────────
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .graphicsLayer {
                        shadowElevation = 24.dp.toPx()
                        shape = RoundedCornerShape(40.dp)
                        clip = true
                    }
                    .background(PrimaryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Apartment,
                    contentDescription = "Community AI Logo",
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─────────────────────────────────
            // App title
            // ─────────────────────────────────
            Text(
                "Community AI",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Your AI-powered community portal",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // ─────────────────────────────────
            // Loading indicator
            // ─────────────────────────────────
            CircularProgressIndicator(
                color = PrimaryGreen,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Connecting…",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.alpha(alpha.value)
            )
        }

        // ─────────────────────────────────
        // Version footer
        // ─────────────────────────────────
        Text(
            "v3.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}