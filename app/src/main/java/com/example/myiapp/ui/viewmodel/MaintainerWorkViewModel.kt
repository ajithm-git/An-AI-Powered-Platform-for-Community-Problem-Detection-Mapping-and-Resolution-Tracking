package com.example.myiapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════
// UI STATE
// ═════════════════════════════════════════════

data class MaintainerWorkUiState(
    val notFinished: List<Problem> = emptyList(),
    val finished: List<Problem> = emptyList(),
    val isLoading: Boolean = false,      // Full-screen loader (initial load)
    val isRefreshing: Boolean = false,   // Silent refresh (small spinner in app bar)
    val errorMessage: String? = null,
    val lastUpdatedAt: Long = 0L
)

// ═════════════════════════════════════════════
// VIEW MODEL
// ═════════════════════════════════════════════

class MaintainerWorkViewModel : ViewModel() {

    private val repository = ProblemRepository()

    private val _uiState = MutableStateFlow(MaintainerWorkUiState())
    val uiState: StateFlow<MaintainerWorkUiState> = _uiState.asStateFlow()

    // ─────────────────────────────────────────────
    // Initial load — shows full-screen loader
    // ─────────────────────────────────────────────
    fun loadMyWork(token: String) {
        // Skip if a load is already running
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = repository.getMyWork()
            result
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            notFinished = response.notFinished,
                            finished = response.finished,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Failed to load work"
                        )
                    }
                }
        }
    }

    // ─────────────────────────────────────────────
    // Refresh — keeps existing list visible, shows small spinner
    // ─────────────────────────────────────────────
    fun refresh(token: String) {
        if (_uiState.value.isRefreshing || _uiState.value.isLoading) return

        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = repository.getMyWork()
            result
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            notFinished = response.notFinished,
                            finished = response.finished,
                            isRefreshing = false,
                            isLoading = false,
                            errorMessage = null,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to refresh"
                        )
                    }
                }
        }
    }

    // ─────────────────────────────────────────────
    // Optimistic local update — call after checklist step toggle
    // so the card's progress bar updates without a full refresh.
    // ─────────────────────────────────────────────
    fun applyUpdatedProblem(updated: Problem) {
        _uiState.update { state ->
            // Replace in notFinished
            val newNotFinished = state.notFinished.map {
                if (it.id == updated.id) updated else it
            }
            // Replace in finished
            val newFinished = state.finished.map {
                if (it.id == updated.id) updated else it
            }
            state.copy(
                notFinished = newNotFinished,
                finished = newFinished
            )
        }
    }

    // ─────────────────────────────────────────────
    // Clear error message (e.g., after showing a snackbar)
    // ─────────────────────────────────────────────
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}