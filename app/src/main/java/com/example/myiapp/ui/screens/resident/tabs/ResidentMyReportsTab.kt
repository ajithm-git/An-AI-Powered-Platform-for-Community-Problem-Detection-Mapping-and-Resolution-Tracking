package com.example.myiapp.ui.screens.resident.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.screens.common.ProblemCardTile
import com.example.myiapp.ui.theme.PrimaryGreen
import com.example.myiapp.ui.theme.TextPrimary
import com.example.myiapp.ui.theme.TextSecondary

/**
 * Resident "My Reports" tab — shows only problems reported by the current user.
 * Tap a card → opens ProblemDetailScreen.
 */
@Composable
fun ResidentMyReportsTab(
    sessionManager: SessionManager,
    onProblemClick: (String) -> Unit
) {
    val repo = remember { ProblemRepository() }

    var problems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = sessionManager.getToken().orEmpty()
        if (token.isBlank()) {
            errorMessage = "Session expired"
            loading = false
            return@LaunchedEffect
        }

        val result = repo.getMyProblems()
        if (result.isSuccess) {
            problems = result.getOrNull()?.problems ?: emptyList()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Failed to load your reports"
        }
        loading = false
    }

    // ─── Loading ─────────────────────────────
    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryGreen)
        }
        return
    }

    // ─── Error ───────────────────────────────
    if (errorMessage != null && problems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                errorMessage!!,
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    // ─── My Reports list ─────────────────────
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "My Reports",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (problems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "You haven't reported anything yet.\n" +
                                "Tap the ➕ button below to report an issue.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            items(problems, key = { it.id }) { problem ->
                ProblemCardTile(
                    problem = problem,
                    onClick = { onProblemClick(problem.id) },
                    highlightMyReport = true
                )
            }
        }
    }
}