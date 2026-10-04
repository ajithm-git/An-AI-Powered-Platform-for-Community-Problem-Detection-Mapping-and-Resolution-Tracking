package com.example.myiapp.data.repository

import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

class NotificationRepository {

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

    // ─────────────────────────────────────────────
    // Get user's notifications (paginated)
    // ─────────────────────────────────────────────
    suspend fun getNotifications(
        token: String,
        limit: Int = 30,
        skip: Int = 0,
        unreadOnly: Boolean = false
    ): Result<NotificationsResponse> {
        return try {
            val response = api.getNotifications(limit, skip, unreadOnly)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to load notifications")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Lightweight unread count (for badge)
    // ─────────────────────────────────────────────
    suspend fun getUnreadCount(token: String): Result<Int> {
        return try {
            val response = api.getNotificationsUnreadCount()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.unreadCount)
            } else {
                // Silent fallback — don't spam user with badge errors
                Result.success(0)
            }
        } catch (e: Exception) {
            // Also silent — badge count failing shouldn't affect UX
            Result.success(0)
        }
    }

    // ─────────────────────────────────────────────
    // Mark a single notification as read
    // ─────────────────────────────────────────────
    suspend fun markAsRead(
        token: String,
        notificationId: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.markNotificationRead(notificationId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to mark as read")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Mark all notifications as read
    // ─────────────────────────────────────────────
    suspend fun markAllAsRead(token: String): Result<SimpleResponse> {
        return try {
            val response = api.markAllNotificationsRead()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to mark all as read")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Delete a single notification
    // ─────────────────────────────────────────────
    suspend fun deleteNotification(
        token: String,
        notificationId: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.deleteNotification(notificationId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to delete")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Clear all notifications
    // ─────────────────────────────────────────────
    suspend fun clearAll(token: String): Result<SimpleResponse> {
        return try {
            val response = api.clearAllNotifications()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to clear notifications")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }
}