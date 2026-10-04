package com.example.myiapp.ui.screens.common

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.ChatbotMessage
import com.example.myiapp.data.model.ChatbotRequest
import com.example.myiapp.data.network.RetrofitClient
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ChatbotScreen — Gemini AI assistant
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Chat bubbles with proper corner styling
 *  • Typing indicator
 *  • Conversation history preserved (last 10 messages)
 *  • Suggested prompts for the empty state
 *  • Auto-scroll to newest message
 *  • Graceful error handling with retry hint
 */
data class ChatUiMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val api = remember { RetrofitClient.apiService }

    // Message list — starts with a welcome
    val messages = remember {
        mutableStateListOf(
            ChatUiMessage(
                id = "welcome",
                text = "Hi! I'm your AI Community Assistant. Ask me about reporting issues, checking problem status, or how the app works.",
                isUser = false
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isAiTyping by remember { mutableStateOf(false) }

    // Suggested prompts for empty state
    val suggestions = listOf(
        "How do I report a problem?",
        "What happens after I report?",
        "How does AI verification work?",
        "Can I edit my problem?"
    )

    // ─────────────────────────────────────────
    // Auto-scroll when messages change
    // ─────────────────────────────────────────
    LaunchedEffect(messages.size, isAiTyping) {
        if (messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(messages.size)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ─────────────────────────────────────────
    // Send message
    // ─────────────────────────────────────────
    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || isAiTyping) return

        // Add user message
        messages.add(
            ChatUiMessage(
                id = System.currentTimeMillis().toString(),
                text = clean,
                isUser = true
            )
        )
        inputText = ""
        focusManager.clearFocus()
        isAiTyping = true

        scope.launch {
            // Build history from last 10 non-error messages (skip the welcome)
            val history = messages
                .filter { it.id != "welcome" && !it.isError }
                .takeLast(10)
                .map { msg ->
                    ChatbotMessage(
                        role = if (msg.isUser) "user" else "model",
                        text = msg.text
                    )
                }
                .dropLast(1) // Don't include the message we're about to send again

            try {
                // Small UX delay so typing indicator is visible
                delay(300)

                val response = api.sendChatMessage(
                    ChatbotRequest(
                        message = clean,
                        history = history
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success && !body.reply.isNullOrBlank()) {
                        messages.add(
                            ChatUiMessage(
                                id = System.currentTimeMillis().toString(),
                                text = body.reply!!,
                                isUser = false
                            )
                        )
                    } else {
                        messages.add(
                            ChatUiMessage(
                                id = System.currentTimeMillis().toString(),
                                text = body.message ?: "I couldn't generate a reply. Please try again.",
                                isUser = false,
                                isError = true
                            )
                        )
                    }
                } else {
                    val errMsg = parseError(response.errorBody()?.string(), "Server error")
                    messages.add(
                        ChatUiMessage(
                            id = System.currentTimeMillis().toString(),
                            text = errMsg,
                            isUser = false,
                            isError = true
                        )
                    )
                }
            } catch (e: Exception) {
                messages.add(
                    ChatUiMessage(
                        id = System.currentTimeMillis().toString(),
                        text = "Network error: ${e.message ?: "Please check your connection."}",
                        isUser = false,
                        isError = true
                    )
                )
            } finally {
                isAiTyping = false
            }
        }
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(36.dp),
                            shape = CircleShape,
                            color = PrimaryGreenSoft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Powered by Gemini",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundLight
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // ─────────────────────────────────
            // Messages list
            // ─────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
                if (isAiTyping) {
                    item(key = "typing_indicator") {
                        TypingIndicator()
                    }
                }
            }

            // ─────────────────────────────────
            // Suggestions (only when just welcome)
            // ─────────────────────────────────
            if (messages.size == 1 && !isAiTyping) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { prompt ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.clickable { sendMessage(prompt) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    prompt,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────
            // Input bar
            // ─────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceWhite,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type a message…") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !isAiTyping,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(enabled = inputText.isNotBlank() && !isAiTyping) {
                                sendMessage(inputText)
                            },
                        color = if (inputText.isNotBlank() && !isAiTyping) PrimaryGreen
                        else PrimaryGreen.copy(alpha = 0.4f),
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// CHAT BUBBLE
// ═════════════════════════════════════════════

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    val bubbleColor = when {
        message.isUser -> PrimaryGreen
        message.isError -> ErrorRedSoft
        else -> Color.White
    }
    val textColor = when {
        message.isUser -> Color.White
        message.isError -> ErrorRed
        else -> TextPrimary
    }
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            // AI avatar (left)
            if (!message.isUser) {
                Surface(
                    Modifier.size(32.dp),
                    shape = CircleShape,
                    color = PrimaryGreenSoft
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SmartToy,
                            null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Surface(
                color = bubbleColor,
                shape = shape,
                shadowElevation = if (message.isUser) 2.dp else 1.dp,
                border = if (message.isUser) null else BorderStroke(1.dp, BorderLight),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// TYPING INDICATOR
// ═════════════════════════════════════════════

@Composable
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            Modifier.size(32.dp),
            shape = CircleShape,
            color = PrimaryGreenSoft
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.SmartToy,
                    null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = PrimaryGreen
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI is thinking…",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

// ═════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════

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