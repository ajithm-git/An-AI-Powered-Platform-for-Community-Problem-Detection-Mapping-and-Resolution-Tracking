package com.example.myiapp.ui.screens.common

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.AppNotification
import com.example.myiapp.data.network.RetrofitClient
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════════
 *  NotificationsScreen — Premium redesign
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Gradient hero header with unread badge
 *  • Elevated notification cards with unread accent bar
 *  • Category-colored rounded icon tiles
 *  • Staggered entry animation
 *  • Empty / error / loading states
 *  • Mark-all-as-read + delete with material menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit = {},
    onNavigateToProblem: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { RetrofitClient.apiService }

    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMarkingAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadNotifications(silent: Boolean = false) {
        if (!silent) isLoading = true
        try {
            val response = api.getNotifications(limit = 100, skip = 0, unreadOnly = false)
            if (response.isSuccessful && response.body() != null) {
                notifications = response.body()!!.notifications
                error = null
            } else {
                error = parseError(response.errorBody()?.string(), "Failed to load")
            }
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { loadNotifications() }

    // ─────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────
    fun markAsRead(notification: AppNotification) {
        if (notification.isRead) return
        scope.launch {
            try {
                api.markNotificationRead(notification.id)
                notifications = notifications.map {
                    if (it.id == notification.id) it.copy(isRead = true) else it
                }
            } catch (_: Exception) { }
        }
    }

    fun markAllRead() {
        isMarkingAll = true
        scope.launch {
            try {
                api.markAllNotificationsRead()
                notifications = notifications.map { it.copy(isRead = true) }
                Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            isMarkingAll = false
        }
    }

    fun deleteNotification(notification: AppNotification) {
        scope.launch {
            try {
                api.deleteNotification(notification.id)
                notifications = notifications.filterNot { it.id == notification.id }
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleTap(notification: AppNotification) {
        markAsRead(notification)
        notification.relatedProblemId?.let { problemId ->
            onNavigateToProblem(problemId)
        }
    }

    val unreadCount = notifications.count { !it.isRead }

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
        ) {
            // ─────────────────────────────────
            // Gradient header
            // ─────────────────────────────────
            NotificationsHeader(
                unreadCount = unreadCount,
                isMarkingAll = isMarkingAll,
                onBack = onBack,
                onRefresh = { scope.launch { loadNotifications(silent = true) } },
                onMarkAllRead = { markAllRead() }
            )

            // ─────────────────────────────────
            // Body
            // ─────────────────────────────────
            when {
                isLoading -> LoadingState()
                error != null && notifications.isEmpty() -> ErrorState(
                    message = error!!,
                    onRetry = { scope.launch { loadNotifications() } }
                )
                notifications.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = notifications,
                        key = { it.id }
                    ) { notification ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible = true }

                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(300)
                                    )
                        ) {
                            NotificationCard(
                                notification = notification,
                                onTap = { handleTap(notification) },
                                onDelete = { deleteNotification(notification) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// HEADER
// ═════════════════════════════════════════════

@Composable
private fun NotificationsHeader(
    unreadCount: Int,
    isMarkingAll: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(PrimaryGreen, PrimaryGreenDark)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(4.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Notifications",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (unreadCount > 0) Color(0xFFFBBF24)
                                else Color.White.copy(alpha = 0.85f)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (unreadCount > 0) "$unreadCount unread"
                        else "You're all caught up",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ─── Refresh button ──────────────
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White
                )
            }

            // ─── Mark all as read ────────────
            if (unreadCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(enabled = !isMarkingAll) {
                            onMarkAllRead()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isMarkingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Mark all as read",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════
// NOTIFICATION CARD
// ═════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(
    notification: AppNotification,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val (icon, tint) = iconForCategory(notification.category)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = if (notification.isRead) 2.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = if (notification.isRead)
                    Color.Black.copy(alpha = 0.06f)
                else
                    tint.copy(alpha = 0.25f),
                spotColor = if (notification.isRead)
                    Color.Black.copy(alpha = 0.06f)
                else
                    tint.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ─── Left accent bar (unread only) ─
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(tint, tint.copy(alpha = 0.4f))
                            )
                        )
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(16.dp))
            }

            // ─── Category icon tile ──────────
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tint.copy(alpha = 0.15f),
                                tint.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = tint.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // ─── Content ─────────────────────
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        notification.title,
                        fontSize = 14.sp,
                        fontWeight = if (notification.isRead)
                            FontWeight.SemiBold
                        else
                            FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        lineHeight = 18.sp
                    )

                    // ─── Overflow menu ───────────
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = Color.White
                        ) {
                            if (!notification.isRead) {
                                DropdownMenuItem(
                                    text = { Text("Mark as read") },
                                    onClick = {
                                        showMenu = false
                                        onTap()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Done,
                                            null,
                                            tint = PrimaryGreen
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = ErrorRed) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        null,
                                        tint = ErrorRed
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    notification.body,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Time chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BackgroundLight
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 3.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                notification.timeAgo.ifBlank {
                                    notification.createdAt ?: ""
                                },
                                fontSize = 10.sp,
                                color = TextTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (notification.actorName.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "· ${notification.actorName}",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// STATES
// ═════════════════════════════════════════════

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = PrimaryGreen,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ErrorRedSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = ErrorRed
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen,
                contentColor = Color.White
            )
        ) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryGreenSoft, PrimaryGreenLight)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = PrimaryGreenDark
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "All caught up!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "You have no notifications yet.\nNew updates will appear here.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ═════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════

private fun iconForCategory(category: String): Pair<ImageVector, Color> = when (category) {
    "PROBLEM_ASSIGNED" -> Icons.Default.AssignmentInd to AccentBlue
    "PROBLEM_UPDATED" -> Icons.Default.Update to WarningAmber
    "PROBLEM_RESOLVED" -> Icons.Default.CheckCircle to PrimaryGreen
    "PROBLEM_COMMENTED" -> Icons.AutoMirrored.Filled.Comment to AccentBlue
    "PROBLEM_LIKED" -> Icons.Default.Favorite to ErrorRed
    "PROBLEM_AFTER_PHOTO" -> Icons.Default.PhotoCamera to AccentTeal
    "NEW_PROBLEM_NEARBY" -> Icons.Default.NewReleases to WarningAmber
    "CHAT_MESSAGE" -> Icons.AutoMirrored.Filled.Chat to AccentTeal
    "COMMUNITY_CREATED" -> Icons.Default.Apartment to AdminPurple
    "COMMUNITY_UPDATED" -> Icons.Default.Edit to AdminPurple
    "COMMUNITY_DEACTIVATED" -> Icons.Default.Block to ErrorRed
    "WELCOME" -> Icons.Default.Celebration to PrimaryGreen
    "ACCOUNT" -> Icons.Default.Person to AccentBlue
    "SYSTEM" -> Icons.Default.Info to TextSecondary
    else -> Icons.Default.Notifications to PrimaryGreen
}

private fun parseError(errorBody: String?, fallback: String): String {
    if (errorBody.isNullOrBlank()) return fallback
    return try {
        val json = JSONObject(errorBody)
        when {
            json.has("message") -> json.getString("message")
            json.has("error") -> json.getString("error")
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}