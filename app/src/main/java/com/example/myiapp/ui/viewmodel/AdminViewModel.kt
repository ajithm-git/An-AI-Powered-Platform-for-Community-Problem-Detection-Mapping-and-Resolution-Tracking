package com.example.myiapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiapp.data.model.*
import com.example.myiapp.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AdminViewModel — state + actions for all Admin screens
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Holds UI state as StateFlow (Compose-friendly)
 *  • Delegates all API calls to AdminRepository
 *  • Manages loading + error + success message state
 */
class AdminViewModel(
    private val repository: AdminRepository = AdminRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "AdminViewModel"
    }

    // ═════════════════════════════════════════════
    // DASHBOARD STATE
    // ═════════════════════════════════════════════
    private val _dashboardState = MutableStateFlow(AdminDashboardUiState())
    val dashboardState: StateFlow<AdminDashboardUiState> = _dashboardState.asStateFlow()

    // ═════════════════════════════════════════════
    // USERS STATE
    // ═════════════════════════════════════════════
    private val _usersState = MutableStateFlow(AdminUsersUiState())
    val usersState: StateFlow<AdminUsersUiState> = _usersState.asStateFlow()

    // ═════════════════════════════════════════════
    // MAINTAINERS STATE
    // ═════════════════════════════════════════════
    private val _maintainersState = MutableStateFlow(AdminMaintainersUiState())
    val maintainersState: StateFlow<AdminMaintainersUiState> = _maintainersState.asStateFlow()

    // ═════════════════════════════════════════════
    // MAINTAINER DETAIL STATE
    // ═════════════════════════════════════════════
    private val _maintainerDetailState = MutableStateFlow(AdminMaintainerDetailUiState())
    val maintainerDetailState: StateFlow<AdminMaintainerDetailUiState> = _maintainerDetailState.asStateFlow()

    // ═════════════════════════════════════════════
    // PROBLEM VERIFICATION DETAIL STATE
    // ═════════════════════════════════════════════
    private val _verificationState = MutableStateFlow(AdminVerificationUiState())
    val verificationState: StateFlow<AdminVerificationUiState> = _verificationState.asStateFlow()

    // ═════════════════════════════════════════════
    // CONFIG STATE (SuperAdmin only)
    // ═════════════════════════════════════════════
    private val _configState = MutableStateFlow(AdminConfigUiState())
    val configState: StateFlow<AdminConfigUiState> = _configState.asStateFlow()

    // ═════════════════════════════════════════════
    // ACTION RESULT (one-shot message + success flag)
    // ═════════════════════════════════════════════
    private val _actionResult = MutableStateFlow<ActionMessage?>(null)
    val actionResult: StateFlow<ActionMessage?> = _actionResult.asStateFlow()

    fun clearActionResult() {
        _actionResult.value = null
    }

    // ═════════════════════════════════════════════
    // DASHBOARD
    // ═════════════════════════════════════════════

    fun loadDashboard(communityId: String? = null) {
        viewModelScope.launch {
            _dashboardState.update { it.copy(isLoading = true, error = null) }

            repository.getDashboard(communityId).fold(
                onSuccess = { response ->
                    if (response.success && response.stats != null) {
                        _dashboardState.update {
                            it.copy(
                                isLoading = false,
                                stats = response.stats,
                                error = null
                            )
                        }
                    } else {
                        _dashboardState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Failed to load dashboard"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Dashboard load failed", e)
                    _dashboardState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    // ═════════════════════════════════════════════
    // USERS
    // ═════════════════════════════════════════════

    fun loadUsers(
        role: String? = null,
        search: String? = null,
        active: Boolean? = null,
        communityId: String? = null,
        reset: Boolean = true
    ) {
        viewModelScope.launch {
            _usersState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    filterRole = role ?: it.filterRole,
                    filterSearch = search ?: it.filterSearch,
                    filterActive = active ?: it.filterActive
                )
            }

            repository.getUsers(
                role = role,
                search = search,
                active = active,
                communityId = communityId,
                limit = 100,
                skip = 0
            ).fold(
                onSuccess = { response ->
                    _usersState.update {
                        it.copy(
                            isLoading = false,
                            users = response.users,
                            total = response.total,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Users load failed", e)
                    _usersState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    fun filterUsersByRole(role: String?) {
        loadUsers(role = role, search = _usersState.value.filterSearch)
    }

    fun searchUsers(query: String) {
        loadUsers(role = _usersState.value.filterRole, search = query)
    }

    fun createUser(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        communityId: String? = null
    ) {
        viewModelScope.launch {
            _usersState.update { it.copy(isSubmitting = true) }

            repository.createUser(
                name = name,
                email = email,
                phone = phone,
                password = password,
                role = role,
                communityId = communityId
            ).fold(
                onSuccess = { response ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = response.message ?: "User created successfully"
                        )
                        // Refresh list
                        loadUsers(
                            role = _usersState.value.filterRole,
                            search = _usersState.value.filterSearch
                        )
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to create user"
                        )
                    }
                },
                onFailure = { e ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun updateUser(
        userId: String,
        name: String? = null,
        phone: String? = null,
        role: String? = null,
        communityId: String? = null,
        isActive: Boolean? = null
    ) {
        viewModelScope.launch {
            _usersState.update { it.copy(isSubmitting = true) }

            repository.updateUser(
                userId = userId,
                name = name,
                phone = phone,
                role = role,
                communityId = communityId,
                isActive = isActive
            ).fold(
                onSuccess = { response ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = response.message ?: "User updated"
                        )
                        loadUsers(
                            role = _usersState.value.filterRole,
                            search = _usersState.value.filterSearch
                        )
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to update user"
                        )
                    }
                },
                onFailure = { e ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _usersState.update { it.copy(isSubmitting = true) }

            repository.deleteUser(userId).fold(
                onSuccess = { response ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = response.message ?: "User deactivated"
                        )
                        loadUsers(
                            role = _usersState.value.filterRole,
                            search = _usersState.value.filterSearch
                        )
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to delete user"
                        )
                    }
                },
                onFailure = { e ->
                    _usersState.update { it.copy(isSubmitting = false) }
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun promoteUser(userId: String, newRole: String) {
        viewModelScope.launch {
            repository.promoteUser(userId, newRole).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = "Role updated to $newRole"
                        )
                        loadUsers(
                            role = _usersState.value.filterRole,
                            search = _usersState.value.filterSearch
                        )
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to change role"
                        )
                    }
                },
                onFailure = { e ->
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    // ═════════════════════════════════════════════
    // MAINTAINERS
    // ═════════════════════════════════════════════

    fun loadMaintainers(communityId: String? = null) {
        viewModelScope.launch {
            _maintainersState.update { it.copy(isLoading = true, error = null) }

            repository.getMaintainers(communityId).fold(
                onSuccess = { response ->
                    _maintainersState.update {
                        it.copy(
                            isLoading = false,
                            maintainers = response.maintainers,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _maintainersState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    fun loadMaintainerDetail(maintainerId: String) {
        viewModelScope.launch {
            _maintainerDetailState.update { it.copy(isLoading = true, error = null) }

            repository.getMaintainerDetail(maintainerId).fold(
                onSuccess = { response ->
                    _maintainerDetailState.update {
                        it.copy(
                            isLoading = false,
                            maintainer = response.maintainer,
                            stats = response.stats,
                            activeProblems = response.activeProblems,
                            resolvedProblems = response.resolvedProblems,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _maintainerDetailState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    // ═════════════════════════════════════════════
    // PROBLEM ACTIONS
    // ═════════════════════════════════════════════

    fun reassignProblem(
        problemId: String,
        maintainerId: String,
        reason: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.reassignProblem(problemId, maintainerId, reason).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = response.message ?: "Problem reassigned"
                        )
                        onSuccess?.invoke()
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to reassign"
                        )
                    }
                },
                onFailure = { e ->
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun overrideVerification(
        problemId: String,
        verdict: String,
        note: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.overrideVerification(problemId, verdict, note).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = "Verification overridden to $verdict"
                        )
                        onSuccess?.invoke()
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to override"
                        )
                    }
                },
                onFailure = { e ->
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun loadProblemVerification(problemId: String) {
        viewModelScope.launch {
            _verificationState.update { it.copy(isLoading = true, error = null) }

            repository.getProblemVerification(problemId).fold(
                onSuccess = { response ->
                    _verificationState.update {
                        it.copy(
                            isLoading = false,
                            verification = response.verification,
                            timing = response.timing,
                            notification = response.notification,
                            checklist = response.checklist,
                            images = response.images,
                            yolo = response.yolo,
                            problem = response.problem,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _verificationState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    // ═════════════════════════════════════════════
    // CONFIG (SuperAdmin)
    // ═════════════════════════════════════════════

    fun loadConfig() {
        viewModelScope.launch {
            _configState.update { it.copy(isLoading = true, error = null) }

            repository.getAllConfig().fold(
                onSuccess = { response ->
                    _configState.update {
                        it.copy(
                            isLoading = false,
                            configs = response.configs,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _configState.update {
                        it.copy(isLoading = false, error = e.message ?: "Network error")
                    }
                }
            )
        }
    }

    fun updateConfig(
        key: String,
        value: Map<String, Any>,
        description: String? = null
    ) {
        viewModelScope.launch {
            _configState.update { it.copy(isSubmitting = true) }

            repository.updateConfig(key, value, description).fold(
                onSuccess = { response ->
                    _configState.update { it.copy(isSubmitting = false) }
                    if (response.success) {
                        _actionResult.value = ActionMessage(
                            success = true,
                            message = "$key updated successfully"
                        )
                        loadConfig()
                    } else {
                        _actionResult.value = ActionMessage(
                            success = false,
                            message = response.message ?: "Failed to update config"
                        )
                    }
                },
                onFailure = { e ->
                    _configState.update { it.copy(isSubmitting = false) }
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun testSmtp(email: String) {
        viewModelScope.launch {
            _configState.update { it.copy(isTestingSmtp = true) }

            repository.testSmtpConfig(email).fold(
                onSuccess = { response ->
                    _configState.update { it.copy(isTestingSmtp = false) }
                    _actionResult.value = ActionMessage(
                        success = response.success,
                        message = response.message ?: "SMTP test complete"
                    )
                },
                onFailure = { e ->
                    _configState.update { it.copy(isTestingSmtp = false) }
                    _actionResult.value = ActionMessage(
                        success = false,
                        message = e.message ?: "SMTP test failed"
                    )
                }
            )
        }
    }
}

// ═════════════════════════════════════════════
// UI STATE CLASSES
// ═════════════════════════════════════════════

data class AdminDashboardUiState(
    val isLoading: Boolean = false,
    val stats: AdminStats? = null,
    val error: String? = null
)

data class AdminUsersUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val filterRole: String? = null,
    val filterSearch: String? = null,
    val filterActive: Boolean? = null
)

data class AdminMaintainersUiState(
    val isLoading: Boolean = false,
    val maintainers: List<AdminMaintainer> = emptyList(),
    val error: String? = null
)

data class AdminMaintainerDetailUiState(
    val isLoading: Boolean = false,
    val maintainer: AdminUser? = null,
    val stats: MaintainerDetailStats? = null,
    val activeProblems: List<Problem> = emptyList(),
    val resolvedProblems: List<Problem> = emptyList(),
    val error: String? = null
)

data class AdminVerificationUiState(
    val isLoading: Boolean = false,
    val problem: Problem? = null,
    val verification: ResolutionVerification? = null,
    val timing: AdminTimingDetail? = null,
    val notification: AdminNotificationDetail? = null,
    val checklist: AdminChecklistDetail? = null,
    val images: AdminImageDetail? = null,
    val yolo: AdminYoloDetail? = null,
    val error: String? = null
)

data class AdminConfigUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isTestingSmtp: Boolean = false,
    val configs: List<ConfigEntry> = emptyList(),
    val error: String? = null
)

data class ActionMessage(
    val success: Boolean,
    val message: String
)