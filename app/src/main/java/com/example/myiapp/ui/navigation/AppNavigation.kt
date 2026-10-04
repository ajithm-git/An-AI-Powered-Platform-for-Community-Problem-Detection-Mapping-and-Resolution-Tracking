package com.example.myiapp.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.ui.screens.admin.AdminCreateProblemScreen
import com.example.myiapp.ui.screens.admin.AdminHomeScreen
import com.example.myiapp.ui.screens.admin.AdminMaintainerDetailScreen
import com.example.myiapp.ui.screens.admin.AdminProblemsScreen
import com.example.myiapp.ui.screens.admin.AdminUserEditScreen
import com.example.myiapp.ui.screens.auth.ForgotPasswordScreen
import com.example.myiapp.ui.screens.auth.LoginScreen
import com.example.myiapp.ui.screens.auth.RegisterScreen
import com.example.myiapp.ui.screens.auth.SplashScreen
import com.example.myiapp.ui.screens.common.ChatbotScreen
import com.example.myiapp.ui.screens.common.NotificationsScreen
import com.example.myiapp.ui.screens.common.ProblemDetailScreen
import com.example.myiapp.ui.screens.common.UnifiedProfileScreen
import com.example.myiapp.ui.screens.maintainer.MaintainerHomeScreen
import com.example.myiapp.ui.screens.resident.ReportProblemScreen
import com.example.myiapp.ui.screens.resident.ResidentHomeScreen
import com.example.myiapp.ui.theme.*

/**
 * ═══════════════════════════════════════════════════════════════════
 *  Route definitions — single source of truth
 * ═══════════════════════════════════════════════════════════════════
 */
object Routes {
    // ─── Auth flow ───────────────────────────
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"

    // ─── Role homes ──────────────────────────
    const val RESIDENT_HOME = "resident_home"
    const val MAINTAINER_HOME = "maintainer_home"
    const val ADMIN_HOME = "admin_home"

    // ─── Common (all roles) ──────────────────
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val CHATBOT = "chatbot"
    const val REPORT = "report"

    // ─── Problem detail ──────────────────────
    const val PROBLEM_DETAIL_PATTERN = "problem/{problemId}"
    fun problemDetail(problemId: String) = "problem/${Uri.encode(problemId)}"

    // ─── Admin: user management ──────────────
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_USERS_ADD = "admin/users/add"
    const val USER_EDIT_PATTERN = "admin/users/{userId}/edit"
    fun adminUserEdit(userId: String) = "admin/users/${Uri.encode(userId)}/edit"

    // ─── Admin: maintainers ──────────────────
    const val ADMIN_MAINTAINERS = "admin/maintainers"
    const val MAINTAINER_DETAIL_PATTERN = "admin/maintainers/{maintainerId}"
    fun adminMaintainerDetail(maintainerId: String) =
        "admin/maintainers/${Uri.encode(maintainerId)}"

    // ─── Admin: problems ─────────────────────
    const val ADMIN_PROBLEMS = "admin/problems"
    const val ADMIN_PROBLEMS_ADD = "admin/problems/add"
    const val PROBLEM_EDIT_PATTERN = "admin/problems/{problemId}/edit"
    fun adminProblemEdit(problemId: String) =
        "admin/problems/${Uri.encode(problemId)}/edit"

    const val VERIFICATION_PATTERN = "admin/problems/{problemId}/verification"
    fun adminProblemVerification(problemId: String) =
        "admin/problems/${Uri.encode(problemId)}/verification"

    // ─── Admin: community + config ───────────
    const val ADMIN_COMMUNITY = "admin/community"
    const val ADMIN_CONFIG = "admin/config"
}

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AppNavigation — root NavHost
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Flow:
 *    Splash → (session check) → Login OR RoleHome
 *
 *  Role home is a single route per role. Each role screen owns its
 *  own tab state and hosts the shared CommunityBottomNav.
 *
 *  Deep links: communityai://problem/{id}
 */
@Composable
fun AppNavigation(
    sessionManager: SessionManager,
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // ─── Deep link handling ──────────────────
    // If a deep link arrives, navigate to the target route once
    // the NavHost is ready. Then notify the host to clear it.
    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        try {
            val host = uri.host
            val lastPath = uri.lastPathSegment

            when (host) {
                "problem" -> {
                    if (!lastPath.isNullOrBlank()) {
                        navController.navigate(Routes.problemDetail(lastPath))
                    }
                }
                "community" -> {
                    // Future: navigate to community detail route
                }
                "user" -> {
                    navController.navigate(Routes.PROFILE)
                }
            }
        } catch (_: Exception) {
            // Ignore malformed deep links
        } finally {
            onDeepLinkConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // ═════════════════════════════════════════════
        // AUTH GRAPH
        // ═════════════════════════════════════════════

        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToResidentHome = {
                    navController.navigate(Routes.RESIDENT_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToMaintainerHome = {
                    navController.navigate(Routes.MAINTAINER_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToAdminHome = {
                    navController.navigate(Routes.ADMIN_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { _, role, _ ->
                    val destination = when (role) {
                        "SuperAdmin", "Admin" -> Routes.ADMIN_HOME
                        "Staff" -> Routes.MAINTAINER_HOME
                        else -> Routes.RESIDENT_HOME
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToForgot = { navController.navigate(Routes.FORGOT) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // ═════════════════════════════════════════════
        // ROLE HOME SCREENS (each hosts its own tabs)
        // ═════════════════════════════════════════════

        composable(Routes.RESIDENT_HOME) {
            ResidentHomeScreen(
                sessionManager = sessionManager,
                navController = navController
            )
        }

        composable(Routes.MAINTAINER_HOME) {
            MaintainerHomeScreen(
                sessionManager = sessionManager,
                navController = navController
            )
        }

        composable(Routes.ADMIN_HOME) {
            AdminHomeScreen(
                sessionManager = sessionManager,
                navController = navController
            )
        }

        // ═════════════════════════════════════════════
        // COMMON SCREENS (all roles)
        // ═════════════════════════════════════════════

        composable(Routes.PROFILE) {
            UnifiedProfileScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onNavigateToProblem = { problemId ->
                    navController.navigate(Routes.problemDetail(problemId))
                }
            )
        }

        composable(Routes.CHATBOT) {
            ChatbotScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REPORT) {
            ReportProblemScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemCreated = { problemId ->
                    navController.navigate(Routes.problemDetail(problemId))
                }
            )
        }

        // ═════════════════════════════════════════════
        // PROBLEM DETAIL (shared, with deep link)
        // ═════════════════════════════════════════════

        composable(
            route = Routes.PROBLEM_DETAIL_PATTERN,
            arguments = listOf(
                navArgument("problemId") {
                    type = NavType.StringType
                    nullable = false
                }
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "communityai://problem/{problemId}"
                }
            )
        ) { backStackEntry ->
            val problemId = backStackEntry.arguments?.getString("problemId") ?: ""
            ProblemDetailScreen(
                sessionManager = sessionManager,
                problemId = problemId,
                onBack = { navController.popBackStack() }
            )
        }

        // ═════════════════════════════════════════════
        // ADMIN SUB-ROUTES
        // ═════════════════════════════════════════════

        // ─── Users ────────────────────────────
        composable(Routes.ADMIN_USERS) {
            AdminProblemsScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemClick = { id -> navController.navigate(Routes.problemDetail(id)) },
                onAddProblemClick = { navController.navigate(Routes.ADMIN_PROBLEMS_ADD) }
            )
        }

        composable(Routes.ADMIN_USERS_ADD) {
            AdminUserEditScreen(
                sessionManager = sessionManager,
                userId = null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.USER_EDIT_PATTERN,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            AdminUserEditScreen(
                sessionManager = sessionManager,
                userId = userId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        // ─── Maintainers ──────────────────────
        composable(Routes.ADMIN_MAINTAINERS) {
            AdminProblemsScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemClick = { id -> navController.navigate(Routes.problemDetail(id)) },
                onAddProblemClick = { navController.navigate(Routes.ADMIN_PROBLEMS_ADD) }
            )
        }

        composable(
            route = Routes.MAINTAINER_DETAIL_PATTERN,
            arguments = listOf(navArgument("maintainerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val maintainerId = backStackEntry.arguments?.getString("maintainerId") ?: ""
            AdminMaintainerDetailScreen(
                sessionManager = sessionManager,
                maintainerId = maintainerId,
                onBack = { navController.popBackStack() },
                onProblemClick = { id -> navController.navigate(Routes.problemDetail(id)) }
            )
        }

        // ─── Problems ─────────────────────────
        composable(Routes.ADMIN_PROBLEMS) {
            AdminProblemsScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemClick = { id -> navController.navigate(Routes.problemDetail(id)) },
                onAddProblemClick = { navController.navigate(Routes.ADMIN_PROBLEMS_ADD) }
            )
        }

        composable(Routes.ADMIN_PROBLEMS_ADD) {
            AdminCreateProblemScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemCreated = { id ->
                    navController.navigate(Routes.problemDetail(id)) {
                        popUpTo(Routes.ADMIN_PROBLEMS_ADD) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.PROBLEM_EDIT_PATTERN,
            arguments = listOf(navArgument("problemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val problemId = backStackEntry.arguments?.getString("problemId") ?: ""
            // TODO: replace with a dedicated AdminProblemEditScreen
            AdminCreateProblemScreen(
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() },
                onProblemCreated = { id ->
                    navController.navigate(Routes.problemDetail(id))
                }
            )
        }

        composable(
            route = Routes.VERIFICATION_PATTERN,
            arguments = listOf(navArgument("problemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val problemId = backStackEntry.arguments?.getString("problemId") ?: ""
            // Reuse the shared ProblemDetailScreen; verification section is inside it
            ProblemDetailScreen(
                sessionManager = sessionManager,
                problemId = problemId,
                onBack = { navController.popBackStack() }
            )
        }

        // ─── Community + Config ───────────────
        composable(Routes.ADMIN_COMMUNITY) {
            PlaceholderScreen(
                title = "Community Settings",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_CONFIG) {
            PlaceholderScreen(
                title = "Configuration",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PlaceholderScreen
 * ═══════════════════════════════════════════════════════════════════
 * Elegant placeholder for routes that still need a real screen.
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Coming soon…",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onBack) {
                Text(
                    "Go Back",
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}