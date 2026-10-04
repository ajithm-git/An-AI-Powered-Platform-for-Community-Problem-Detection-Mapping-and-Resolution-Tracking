package com.example.myiapp.data.repository

import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

class ChatbotRepository {

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
    // SEND MESSAGE TO GEMINI
    // ═════════════════════════════════════════════
    /**
     * @param token JWT bearer token
     * @param message user's message
     * @param history prior turns — [{ role: "user"|"model", text: "..." }]
     */
    suspend fun sendMessage(
        token: String,
        message: String,
        history: List<ChatbotMessage> = emptyList()
    ): Result<ChatbotResponse> {
        return try {
            val request = ChatbotRequest(
                message = message,
                history = history
            )
            val response = api.sendChatMessage(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to get AI reply")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ═════════════════════════════════════════════
    // CHECK AI AVAILABILITY
    // ═════════════════════════════════════════════
    suspend fun getStatus(token: String): Result<ChatStatusResponse> {
        return try {
            val response = api.getChatStatus()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to check AI status")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }
}