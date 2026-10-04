package com.example.myiapp.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.Problem
import com.example.myiapp.data.repository.ProblemRepository
import com.example.myiapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AdminProblemsScreen — Manage all problems in the community
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Features:
 *  • Status filter chips (All / Open / Assigned / In Progress / Resolved)
 *  • Priority indicator bar (Urgent=red, High=amber, Medium=blue, Low=gray)
 *  • Live search by title / location
 *  • Manual refresh
 *  • Empty + error states
 *  • FAB to create a problem on behalf of a resident
 *
 *  Uses theme-level priorityColor() / statusColor() from AppDesignSystem.kt
 *  for consistency with other screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProblemsScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onProblemClick: (String) -> Unit,
    onAddProblemClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val problemRepository = remember { ProblemRepository() }

    var problems by remember { mutableStateOf<List<Problem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // ─────────────────────────────────────────
    // Load problems
    // ─────────────────────────────────────────
    suspend fun loadProblems(silent: Boolean = false) {
        if (!silent) isLoading = true
        error = null

        problemRepository.getProblems(limit = 100).fold(
            onSuccess = { response ->
                problems = response.problems
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load problems"
                if (!silent) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(Unit) { loadProblems() }

    // Filter logic (client-side for snappy UX)
    val filteredProblems = remember(problems, searchQuery, statusFilter) {
        problems.filter { p ->
            val matchesStatus = statusFilter == null || p.status == statusFilter
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                    p.title.lowercase().contains(q) ||
                    p.location.lowercase().contains(q) ||
                    p.category.lowercase().contains(q) ||
                    p.assignedToName.lowercase().contains(q)
            matchesStatus && matchesQuery
        }
    }

    val statusOptions = listOf(
        null to "All",
        "Open" to "Open",
        "Assigned" to "Assigned",
        "In Progress" to "In Progress",
        "Resolved" to "Resolved",
        "Rejected" to "Rejected"
    )

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            Surface(
                color = AdminPurple,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                "Issue Management",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "${filteredProblems.size} of ${problems.size} report(s)",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                isRefreshing = true
                                scope.launch { loadProblems(silent = true) }
                            },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("Search by title, location…", color = Color.White.copy(alpha = 0.6f))
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        )
                    )

                    // Status chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(statusOptions) { (value, label) ->
                            val selected = statusFilter == value
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { statusFilter = value }
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                    color = if (selected) AdminPurple else Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProblemClick,
                containerColor = AdminPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create problem")
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AdminPurple)
                }
            }

            error != null && problems.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error!!, color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { scope.launch { loadProblems() } },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
                    ) {
                        Text("Retry")
                    }
                }
            }

            filteredProblems.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = BorderLight
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank() || statusFilter != null)
                            "No matches found"
                        else
                            "No problems yet",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isNotBlank() || statusFilter != null)
                            "Try a different search or filter"
                        else
                            "Tap + to create the first report",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredProblems, key = { it.id }) { problem ->
                        AdminProblemManageCard(problem) { onProblemClick(problem.id) }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════
// PROBLEM ROW
// ═════════════════════════════════════════════

@Composable
private fun AdminProblemManageCard(p: Problem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority indicator
            Box(
                Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(priorityColor(p.priority), RoundedCornerShape(2.dp))
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.location,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(" • ", fontSize = 12.sp, color = TextTertiary)
                    Text(p.category, fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status chip — uses theme statusColor()
                    Box(
                        Modifier
                            .background(
                                statusColor(p.status).copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            p.status.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = statusColor(p.status)
                        )
                    }

                    // Priority chip — uses theme priorityColor()
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(
                                priorityColor(p.priority).copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            p.priority.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = priorityColor(p.priority)
                        )
                    }

                    // Assignee
                    if (p.assignedToName.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "→ ${p.assignedToName}",
                            fontSize = 10.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

// ═════════════════════════════════════════════
// NOTE: priorityColor() and statusColor() are now
// provided by AppDesignSystem.kt (theme layer).
// They resolve via the `com.example.myiapp.ui.theme.*`
// import above.
// ═════════════════════════════════════════════