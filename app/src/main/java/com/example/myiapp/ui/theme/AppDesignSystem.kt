package com.example.myiapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════
//  1. COLORS
// ═══════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────
// Primary Brand — Modern Emerald & Teal
// ─────────────────────────────────────────────
val PrimaryGreen = Color(0xFF10B981)
val PrimaryGreenDark = Color(0xFF059669)
val PrimaryGreenLight = Color(0xFFD1FAE5)
val PrimaryGreenSoft = Color(0xFFECFDF5)

val AccentTeal = Color(0xFF14B8A6)
val AccentBlue = Color(0xFF3B82F6)
val AccentBlueDark = Color(0xFF1D4ED8)

// ─────────────────────────────────────────────
// Neutrals & Backgrounds
// ─────────────────────────────────────────────
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceElevated = Color(0xFFF1F5F9)

// Glassmorphism Base
val GlassWhite = Color(0xCCFFFFFF)
val GlassWhiteDeep = Color(0xEEFFFFFF)
val GlassBorder = Color(0x33FFFFFF)

// ─────────────────────────────────────────────
// Text & Hierarchy
// ─────────────────────────────────────────────
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextTertiary = Color(0xFF94A3B8)
val TextOnPrimary = Color(0xFFFFFFFF)

// ─────────────────────────────────────────────
// Borders & Status
// ─────────────────────────────────────────────
val BorderLight = Color(0xFFE2E8F0)
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val ErrorRedSoft = Color(0xFFFEF2F2)
val WarningAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)

// ─────────────────────────────────────────────
// Role Specifics
// ─────────────────────────────────────────────
val AdminPurple = Color(0xFF7C3AED)
val AdminPurpleDark = Color(0xFF4C1D95)
val AdminPurpleSoft = Color(0xFFF5F3FF)

val StaffOrange = Color(0xFFF97316)
val StaffOrangeSoft = Color(0xFFFFF7ED)

val ResidentBlue = Color(0xFF3B82F6)
val ResidentBlueSoft = Color(0xFFEFF6FF)

// ─────────────────────────────────────────────
// Priority Colors
// ─────────────────────────────────────────────
val PriorityLow = Color(0xFF64748B)
val PriorityLowSoft = Color(0xFFF1F5F9)
val PriorityMedium = Color(0xFF3B82F6)
val PriorityMediumSoft = Color(0xFFEFF6FF)
val PriorityHigh = Color(0xFFF59E0B)
val PriorityHighSoft = Color(0xFFFFF7ED)
val PriorityUrgent = Color(0xFFEF4444)
val PriorityUrgentSoft = Color(0xFFFEF2F2)

// ─────────────────────────────────────────────
// Status Colors
// ─────────────────────────────────────────────
val StatusOpen = Color(0xFF3B82F6)
val StatusOpenSoft = Color(0xFFEFF6FF)
val StatusAssigned = Color(0xFF8B5CF6)
val StatusAssignedSoft = Color(0xFFF5F3FF)
val StatusInProgress = Color(0xFFF59E0B)
val StatusInProgressSoft = Color(0xFFFFF7ED)
val StatusResolved = Color(0xFF10B981)
val StatusResolvedSoft = Color(0xFFECFDF5)
val StatusRejected = Color(0xFFEF4444)
val StatusRejectedSoft = Color(0xFFFEF2F2)

// ─────────────────────────────────────────────
// High-End Gradients
// ─────────────────────────────────────────────
val PrimaryGradient = Brush.linearGradient(listOf(PrimaryGreen, AccentTeal))
val DarkGradient = Brush.linearGradient(listOf(PrimaryGreenDark, Color(0xFF065F46)))
val AdminGradient = Brush.linearGradient(listOf(AdminPurple, Color(0xFF6D28D9)))
val SurfaceGradient = Brush.verticalGradient(listOf(SurfaceWhite, SurfaceElevated))
val GlassGradient = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.3f))
)
val SuccessGradient = Brush.linearGradient(listOf(PrimaryGreen, SuccessGreen))
val WarningGradient = Brush.linearGradient(listOf(WarningAmber, Color(0xFFEA580C)))

// ─────────────────────────────────────────────
// Legacy Aliases (keep for compatibility)
// ─────────────────────────────────────────────
val DarkGreen = PrimaryGreenDark
val LightGreen = PrimaryGreenSoft
val BackgroundWhite = BackgroundLight
val TextDark = TextPrimary
val TextGray = TextSecondary

// ═══════════════════════════════════════════════════════════════════
//  2. TYPOGRAPHY
// ═══════════════════════════════════════════════════════════════════

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ═══════════════════════════════════════════════════════════════════
//  3. SHAPES
// ═══════════════════════════════════════════════════════════════════

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// ═══════════════════════════════════════════════════════════════════
//  4. SPACING / RADIUS / ELEVATION CONSTANTS
// ═══════════════════════════════════════════════════════════════════

/**
 * Consistent spacing scale used across all screens.
 * Use these instead of magic numbers like 16.dp.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 48.dp

    // Screen-level padding
    val screenPadding: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val cardSpacing: Dp = 12.dp
}

/**
 * Corner radius scale.
 */
object Radius {
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val pill: Dp = 100.dp
}

/**
 * Standard elevation values.
 */
object Elevation {
    val none: Dp = 0.dp
    val low: Dp = 1.dp
    val medium: Dp = 3.dp
    val high: Dp = 6.dp
    val overlay: Dp = 12.dp
}

// ═══════════════════════════════════════════════════════════════════
//  5. COLOR SCHEMES
// ═══════════════════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = PrimaryGreenDark,
    secondary = AccentTeal,
    onSecondary = TextOnPrimary,
    secondaryContainer = PrimaryGreenSoft,
    onSecondaryContainer = PrimaryGreenDark,
    tertiary = AccentBlue,
    onTertiary = TextOnPrimary,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderLight.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = TextOnPrimary,
    errorContainer = ErrorRedSoft,
    onErrorContainer = ErrorRed
)

// Reserved for future dark mode support
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryGreenDark,
    onPrimaryContainer = TextOnPrimary,
    secondary = AccentTeal,
    onSecondary = TextPrimary,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    error = ErrorRed,
    onError = TextPrimary
)

// ═══════════════════════════════════════════════════════════════════
//  6. THEME COMPOSABLE
// ═══════════════════════════════════════════════════════════════════

/**
 * Community AI Theme — currently forces light mode for a consistent
 * high-end "Emerald" look. Dark mode reserved for future.
 */
@Composable
fun CommunityAITheme(
    darkTheme: Boolean = false, // ignored for now — light-only
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Safe status bar tinting — ignores Preview mode
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()

                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            } catch (_: Throwable) {
                // Ignore — happens in Preview mode or restricted contexts
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

// Aliases for compatibility with old code
val Typography = AppTypography
val Shapes = AppShapes

// ═══════════════════════════════════════════════════════════════════
//  7. HELPER FUNCTIONS — Dynamic Color Lookup
// ═══════════════════════════════════════════════════════════════════

/**
 * Priority → Color mapping. Use in problem cards.
 */
fun priorityColor(priority: String): Color = when (priority.lowercase()) {
    "urgent" -> PriorityUrgent
    "high" -> PriorityHigh
    "medium" -> PriorityMedium
    "low" -> PriorityLow
    else -> PriorityMedium
}

fun prioritySoftColor(priority: String): Color = when (priority.lowercase()) {
    "urgent" -> PriorityUrgentSoft
    "high" -> PriorityHighSoft
    "medium" -> PriorityMediumSoft
    "low" -> PriorityLowSoft
    else -> PriorityMediumSoft
}

/**
 * Status → Color mapping. Use in problem cards and detail screens.
 */
fun statusColor(status: String): Color = when (status) {
    "Open" -> StatusOpen
    "Assigned" -> StatusAssigned
    "In Progress" -> StatusInProgress
    "Resolved" -> StatusResolved
    "Rejected" -> StatusRejected
    else -> TextSecondary
}

fun statusSoftColor(status: String): Color = when (status) {
    "Open" -> StatusOpenSoft
    "Assigned" -> StatusAssignedSoft
    "In Progress" -> StatusInProgressSoft
    "Resolved" -> StatusResolvedSoft
    "Rejected" -> StatusRejectedSoft
    else -> SurfaceElevated
}

/**
 * YOLO verdict → Color mapping.
 */
fun verdictColor(verdict: String): Color = when (verdict.uppercase()) {
    "VERIFIED" -> SuccessGreen
    "PARTIAL" -> WarningAmber
    "NOT_VERIFIED" -> ErrorRed
    "UNVERIFIABLE" -> TextSecondary
    else -> TextSecondary
}

/**
 * Role → Accent color mapping.
 */
fun roleColor(role: String): Color = when (role) {
    "Admin", "SuperAdmin" -> AdminPurple
    "Staff" -> StaffOrange
    "Resident" -> ResidentBlue
    else -> PrimaryGreen
}

fun roleSoftColor(role: String): Color = when (role) {
    "Admin", "SuperAdmin" -> AdminPurpleSoft
    "Staff" -> StaffOrangeSoft
    "Resident" -> ResidentBlueSoft
    else -> PrimaryGreenSoft
}

fun roleLabel(role: String): String = when (role) {
    "SuperAdmin" -> "Super Admin"
    "Admin" -> "Admin"
    "Staff" -> "Maintainer"
    "Resident" -> "Resident"
    else -> role
}