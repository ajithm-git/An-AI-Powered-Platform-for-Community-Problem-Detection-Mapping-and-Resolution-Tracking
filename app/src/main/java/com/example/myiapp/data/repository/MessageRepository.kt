package com.example.myiapp.data.repository

import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

class MessageRepository {

    private val api = RetrofitClient.apiService

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────
    private fun parseError(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                else -> fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }

    // ═════════════════════════════════════════════
    // CHAT ROOMS
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // Get all chat rooms
    // ─────────────────────────────────────────────
    suspend fun getRooms(token: String): Result<RoomsResponse> {
        return try {
            val response = api.getChatRooms()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to load rooms")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ═════════════════════════════════════════════
    // MESSAGES IN A ROOM
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // Get messages in a room (paginated)
    // Pass beforeId (a message ID) to fetch older messages
    // ─────────────────────────────────────────────
    suspend fun getRoomMessages(
        token: String,
        room: String,
        limit: Int = 50,
        beforeId: String? = null
    ): Result<MessagesResponse> {
        return try {
            val response = api.getRoomMessages(room, limit, beforeId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to load messages")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Send a message (text, image, or both)
    // ─────────────────────────────────────────────
    suspend fun sendMessage(
        token: String,
        room: String,
        text: String? = null,
        imageBase64: String? = null,
        relatedProblemId: String? = null
    ): Result<SingleMessageResponse> {
        return try {
            val request = SendMessageRequest(
                text = text,
                imageBase64 = imageBase64,
                relatedProblemId = relatedProblemId
            )
            val response = api.sendMessage(room, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to send message")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Mark all messages in a room as read
    // ─────────────────────────────────────────────
    suspend fun markRoomRead(
        token: String,
        room: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.markRoomRead(room)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to mark room read")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Delete own message
    // ─────────────────────────────────────────────
    suspend fun deleteMessage(
        token: String,
        messageId: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.deleteMessage(messageId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to delete message")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ═════════════════════════════════════════════
    // UNREAD COUNT
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // Total unread messages across all rooms
    // Fails silently with 0 (badge is non-critical)
    // ─────────────────────────────────────────────
    suspend fun getUnreadCount(token: String): Result<Int> {
        return try {
            val response = api.getMessagesUnreadCount()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.unreadCount)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Result.success(0)
        }
    }
}