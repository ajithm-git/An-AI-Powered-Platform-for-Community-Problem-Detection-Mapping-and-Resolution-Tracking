package com.example.myiapp.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.ChatMessageDto
import com.example.myiapp.data.model.ChatRoom
import com.example.myiapp.data.repository.MessageRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

private enum class ChatView { ROOMS_LIST, ROOM_MESSAGES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(sessionManager: SessionManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messageRepository = MessageRepository()
    var currentView by remember { mutableStateOf(ChatView.ROOMS_LIST) }
    var currentRoom by remember { mutableStateOf<ChatRoom?>(null) }
    var rooms by remember { mutableStateOf<List<ChatRoom>>(emptyList()) }
    var messages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var isLoadingRooms by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    fun loadRooms() {
        scope.launch {
            isLoadingRooms = true
            val token = sessionManager.getToken()
            if (!token.isNullOrBlank()) {
                val res = messageRepository.getRooms(token)
                if (res.isSuccess) rooms = res.getOrNull()?.rooms ?: emptyList()
            }
            isLoadingRooms = false
        }
    }

    LaunchedEffect(Unit) { loadRooms() }

    if (currentView == ChatView.ROOMS_LIST) {
        RoomsListScreen(rooms, isLoadingRooms, onBack, onRoomClick = {
            currentRoom = it
            currentView = ChatView.ROOM_MESSAGES
            scope.launch {
                val token = sessionManager.getToken() ?: return@launch
                val res = messageRepository.getRoomMessages(token, it.id)
                if (res.isSuccess) messages = res.getOrNull()?.messages ?: emptyList()
            }
        })
    } else {
        RoomMessagesScreen(currentRoom, messages, listState, onBack = { currentView = ChatView.ROOMS_LIST; loadRooms() }, onSend = { text ->
            scope.launch {
                val token = sessionManager.getToken() ?: return@launch
                val res = messageRepository.sendMessage(token, currentRoom!!.id, text)
                if (res.isSuccess) messages = messages + res.getOrNull()?.data!!
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomsListScreen(rooms: List<ChatRoom>, isLoading: Boolean, onBack: () -> Unit, onRoomClick: (ChatRoom) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Community Chat") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { p ->
        if (isLoading) Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().padding(p)) { items(rooms) { r -> ListItem(headlineContent = { Text(r.name) }, modifier = Modifier.clickable { onRoomClick(r) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomMessagesScreen(room: ChatRoom?, messages: List<ChatMessageDto>, listState: LazyListState, onBack: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(room?.name ?: "Chat") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            LazyColumn(Modifier.weight(1f), state = listState) { items(messages) { m -> Text(m.text, Modifier.padding(8.dp)) } }
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                TextField(text, { text = it }, Modifier.weight(1f))
                IconButton(onClick = { onSend(text); text = "" }) { Icon(Icons.AutoMirrored.Filled.Send, null) }
            }
        }
    }
}
