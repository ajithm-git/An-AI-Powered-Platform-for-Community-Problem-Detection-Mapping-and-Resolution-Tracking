package com.example.myiapp.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myiapp.data.model.UserData
import com.example.myiapp.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
// DataStore extension — single instance per process
// ─────────────────────────────────────────────
private val Context.dataStore by preferencesDataStore(name = "community_ai_session")

// ─────────────────────────────────────────────
// SessionManager
// Stores JWT + user info + community membership
// locally so login persists across app restarts.
//
// ⚠️ IMPORTANT: This class also syncs the JWT to
// RetrofitClient on save/clear so authenticated
// calls work automatically.
// ─────────────────────────────────────────────
class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─────────────────────────────────────────
    // Preference Keys
    // ─────────────────────────────────────────
    private object Keys {
        val TOKEN = stringPreferencesKey("jwt_token")
        val USER_ID = stringPreferencesKey("user_id")
        val NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("user_email")
        val PHONE = stringPreferencesKey("user_phone")
        val ROLE = stringPreferencesKey("user_role")
        val AVATAR_URL = stringPreferencesKey("user_avatar_url")
        val COMMUNITY_ID = stringPreferencesKey("community_id")
        val COMMUNITY_NAME = stringPreferencesKey("community_name")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val IS_VERIFIED = booleanPreferencesKey("is_verified")
    }

    // ─────────────────────────────────────────
    // Save the session after successful login
    // Also pushes the token to RetrofitClient
    // ─────────────────────────────────────────
    suspend fun saveSession(
        token: String,
        userId: String,
        name: String,
        email: String,
        phone: String,
        role: String,
        avatarUrl: String? = null,
        communityId: String? = null,
        communityName: String? = null,
        isVerified: Boolean = true
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.NAME] = name
            prefs[Keys.EMAIL] = email
            prefs[Keys.PHONE] = phone
            prefs[Keys.ROLE] = role
            prefs[Keys.AVATAR_URL] = avatarUrl ?: ""
            prefs[Keys.COMMUNITY_ID] = communityId ?: ""
            prefs[Keys.COMMUNITY_NAME] = communityName ?: ""
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.IS_VERIFIED] = isVerified
        }

        // ✅ CRITICAL — push JWT to the network layer
        RetrofitClient.setAuthToken(token)
        Log.d(TAG, "✅ Session saved for user $email (role=$role)")
    }

    // ─────────────────────────────────────────
    // Convenience — save directly from UserData
    // ─────────────────────────────────────────
    suspend fun saveSession(token: String, user: UserData) {
        saveSession(
            token = token,
            userId = user.id,
            name = user.name,
            email = user.email,
            phone = user.phone,
            role = user.role,
            avatarUrl = user.avatarUrl,
            communityId = user.communityId,
            communityName = user.communityName,
            isVerified = user.isVerified
        )
    }

    // ─────────────────────────────────────────
    // Update only user info (used after profile edit)
    // Keeps token and community unchanged.
    // ─────────────────────────────────────────
    suspend fun updateUserInfo(
        name: String,
        phone: String,
        avatarUrl: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.PHONE] = phone
            if (avatarUrl != null) prefs[Keys.AVATAR_URL] = avatarUrl
        }
    }

    // ─────────────────────────────────────────
    // Update just the avatar (quick refresh)
    // ─────────────────────────────────────────
    suspend fun updateAvatarUrl(avatarUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AVATAR_URL] = avatarUrl
        }
    }

    // ─────────────────────────────────────────
    // Update community (e.g. SuperAdmin moves user)
    // ─────────────────────────────────────────
    suspend fun updateCommunity(
        communityId: String?,
        communityName: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMMUNITY_ID] = communityId ?: ""
            prefs[Keys.COMMUNITY_NAME] = communityName ?: ""
        }
    }

    // ─────────────────────────────────────────
    // Update role (after promotion/demotion)
    // ─────────────────────────────────────────
    suspend fun updateRole(newRole: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROLE] = newRole
        }
    }

    // ─────────────────────────────────────────
    // Clear the session (logout)
    // Also clears the token from RetrofitClient
    // ─────────────────────────────────────────
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
        RetrofitClient.setAuthToken(null)
        Log.d(TAG, "🗑️ Session cleared")
    }

    // ═════════════════════════════════════════
    // RESTORE — call this in Application.onCreate()
    // ═════════════════════════════════════════
    // Reads the stored JWT and pushes it to RetrofitClient
    // so the app can make authenticated calls immediately
    // after process restart.
    fun restoreSessionAsync() {
        ioScope.launch {
            try {
                val token = context.dataStore.data.first()[Keys.TOKEN]
                if (!token.isNullOrBlank()) {
                    RetrofitClient.setAuthToken(token)
                    Log.d(TAG, "♻️ Session restored")
                } else {
                    Log.d(TAG, "ℹ️ No session to restore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore session: ${e.message}")
            }
        }
    }

    /**
     * Suspend version — use when you need the token ready
     * before continuing (e.g. Splash screen decision).
     */
    suspend fun restoreSessionSync() {
        try {
            val token = context.dataStore.data.first()[Keys.TOKEN]
            if (!token.isNullOrBlank()) {
                RetrofitClient.setAuthToken(token)
                Log.d(TAG, "♻️ Session restored (sync)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restore session: ${e.message}")
        }
    }

    // ─────────────────────────────────────────
    // Synchronous getters
    // ─────────────────────────────────────────
    suspend fun getToken(): String? =
        context.dataStore.data.first()[Keys.TOKEN]

    suspend fun getUserId(): String? =
        context.dataStore.data.first()[Keys.USER_ID]

    suspend fun getName(): String? =
        context.dataStore.data.first()[Keys.NAME]

    suspend fun getEmail(): String? =
        context.dataStore.data.first()[Keys.EMAIL]

    suspend fun getPhone(): String? =
        context.dataStore.data.first()[Keys.PHONE]

    suspend fun getRole(): String? =
        context.dataStore.data.first()[Keys.ROLE]

    suspend fun getAvatarUrl(): String? =
        context.dataStore.data.first()[Keys.AVATAR_URL]?.takeIf { it.isNotBlank() }

    suspend fun getCommunityId(): String? =
        context.dataStore.data.first()[Keys.COMMUNITY_ID]?.takeIf { it.isNotBlank() }

    suspend fun getCommunityName(): String? =
        context.dataStore.data.first()[Keys.COMMUNITY_NAME]?.takeIf { it.isNotBlank() }

    suspend fun isLoggedIn(): Boolean =
        context.dataStore.data.first()[Keys.IS_LOGGED_IN] ?: false

    suspend fun isVerified(): Boolean =
        context.dataStore.data.first()[Keys.IS_VERIFIED] ?: false

    // ─────────────────────────────────────────
    // Full session snapshot
    // ─────────────────────────────────────────
    suspend fun getFullSession(): UserData? {
        val prefs = context.dataStore.data.first()
        val id = prefs[Keys.USER_ID] ?: return null
        return UserData(
            id = id,
            name = prefs[Keys.NAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            phone = prefs[Keys.PHONE] ?: "",
            role = prefs[Keys.ROLE] ?: "Resident",
            avatarUrl = prefs[Keys.AVATAR_URL]?.takeIf { it.isNotBlank() },
            isVerified = prefs[Keys.IS_VERIFIED] ?: false,
            communityId = prefs[Keys.COMMUNITY_ID]?.takeIf { it.isNotBlank() },
            communityName = prefs[Keys.COMMUNITY_NAME] ?: ""
        )
    }

    // ─────────────────────────────────────────
    // Role helpers (fast checks)
    // ─────────────────────────────────────────
    suspend fun isResident(): Boolean = getRole() == "Resident"
    suspend fun isStaff(): Boolean = getRole() == "Staff"
    suspend fun isAdmin(): Boolean = getRole() == "Admin"
    suspend fun isSuperAdmin(): Boolean = getRole() == "SuperAdmin"
    suspend fun isMaintainer(): Boolean = getRole()?.let { it == "Staff" || it == "Admin" } ?: false

    // ─────────────────────────────────────────
    // Flows (reactive) — for Compose
    // ─────────────────────────────────────────
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val nameFlow: Flow<String?> = context.dataStore.data.map { it[Keys.NAME] }
    val emailFlow: Flow<String?> = context.dataStore.data.map { it[Keys.EMAIL] }
    val phoneFlow: Flow<String?> = context.dataStore.data.map { it[Keys.PHONE] }
    val roleFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ROLE] }
    val avatarUrlFlow: Flow<String?> = context.dataStore.data.map {
        it[Keys.AVATAR_URL]?.takeIf { v -> v.isNotBlank() }
    }
    val communityIdFlow: Flow<String?> = context.dataStore.data.map {
        it[Keys.COMMUNITY_ID]?.takeIf { v -> v.isNotBlank() }
    }
    val communityNameFlow: Flow<String?> = context.dataStore.data.map {
        it[Keys.COMMUNITY_NAME]?.takeIf { v -> v.isNotBlank() }
    }
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.IS_LOGGED_IN] ?: false
    }
    val isVerifiedFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.IS_VERIFIED] ?: false
    }

    /**
     * Flow of the full user snapshot.
     * Use this in Compose to observe user changes in one place.
     */
    val currentUserFlow: Flow<UserData?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID] ?: return@map null
        UserData(
            id = id,
            name = prefs[Keys.NAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            phone = prefs[Keys.PHONE] ?: "",
            role = prefs[Keys.ROLE] ?: "Resident",
            avatarUrl = prefs[Keys.AVATAR_URL]?.takeIf { it.isNotBlank() },
            isVerified = prefs[Keys.IS_VERIFIED] ?: false,
            communityId = prefs[Keys.COMMUNITY_ID]?.takeIf { it.isNotBlank() },
            communityName = prefs[Keys.COMMUNITY_NAME] ?: ""
        )
    }
}