package com.example.myiapp.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.ui.theme.*

/**
 * ═══════════════════════════════════════════════════════════════════
 *  BottomNavBar — Shared premium bottom navigation
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Floating pill with:
 *  • Rounded 28dp corners, 16dp side margins, soft colored shadow
 *  • Center camera FAB elevated 22dp with gradient + inner highlight
 *  • Animated selection: icon scale + label fade + underline bar
 *  • Notification badge with count (caps at "99+")
 *  • Role-aware accent color
 *
 *  Usage:
 *      CommunityBottomNavBar(
 *          currentTab = NavTab.HOME,
 *          accentColor = PrimaryGreen,  // or AccentBlue / AdminPurple
 *          notificationCount = 3,
 *          onHomeClick = { ... },
 *          onChatbotClick = { ... },
 *          onCameraClick = { ... },
 *          onNotificationsClick = { ... },
 *          onProfileClick = { ... }
 *      )
 */
enum class NavTab {
    HOME, CHATBOT, CAMERA, NOTIFICATIONS, PROFILE
}

@Composable
fun CommunityBottomNavBar(
    currentTab: NavTab = NavTab.HOME,
    accentColor: Color = PrimaryGreen,
    notificationCount: Int = 0,
    onHomeClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onCameraClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ─────────────────────────────────
        // Floating pill background
        // ─────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = accentColor.copy(alpha = 0.25f)
                ),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavIcon(
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    label = "Home",
                    isSelected = currentTab == NavTab.HOME,
                    accentColor = accentColor,
                    onClick = onHomeClick
                )

                NavIcon(
                    selectedIcon = Icons.Filled.SmartToy,
                    unselectedIcon = Icons.Outlined.SmartToy,
                    label = "AI",
                    isSelected = currentTab == NavTab.CHATBOT,
                    accentColor = accentColor,
                    onClick = onChatbotClick
                )

                // Spacer for center FAB
                Spacer(modifier = Modifier.width(64.dp))

                NavIcon(
                    selectedIcon = Icons.Filled.Notifications,
                    unselectedIcon = Icons.Outlined.Notifications,
                    label = "Alerts",
                    isSelected = currentTab == NavTab.NOTIFICATIONS,
                    accentColor = accentColor,
                    badgeCount = notificationCount,
                    onClick = onNotificationsClick
                )

                NavIcon(
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    label = "Me",
                    isSelected = currentTab == NavTab.PROFILE,
                    accentColor = accentColor,
                    onClick = onProfileClick
                )
            }
        }

        // ─────────────────────────────────
        // Center Camera FAB
        // ─────────────────────────────────
        CenterCameraFab(
            accentColor = accentColor,
            onClick = onCameraClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//   NAV ICON
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NavIcon(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    // Animated values
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = tween(220),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(220),
        label = "iconAlpha"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 22.dp else 0.dp,
        animationSpec = tween(220),
        label = "indicatorWidth"
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon + badge
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected) accentColor else TextTertiary,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha)
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(ErrorRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Label
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accentColor else TextTertiary
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Selection underline
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) accentColor else Color.Transparent)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//   CENTER FAB
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CenterCameraFab(
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(64.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = accentColor.copy(alpha = 0.5f),
                spotColor = accentColor.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.75f)
                    )
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner highlight ring
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Report",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}