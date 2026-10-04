package com.example.myiapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.ui.screens.admin.AdminHomeScreen
import com.example.myiapp.ui.screens.maintainer.MaintainerHomeScreen
import com.example.myiapp.ui.screens.resident.ResidentHomeScreen

/**
 * ═══════════════════════════════════════════════════════════════════
 *  RoleHomeRouter — routes to the correct home screen by role
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Called from AppNavigation after login or session restore.
 *
 *  Role → destination:
 *    • SuperAdmin / Admin  → AdminHomeScreen
 *    • Staff               → MaintainerHomeScreen
 *    • Resident / default  → ResidentHomeScreen
 *
 *  Each Home screen is responsible for:
 *    • Rendering its own tabs (Home / AI / Camera / Alerts / Profile)
 *    • Hosting the shared CommunityBottomNav (with role-specific accent color)
 *    • Navigating to sub-screens via the passed navController
 */
@Composable
fun RoleHomeRouter(
    sessionManager: SessionManager,
    navController: NavController,
    userRole: String
) {
    when (userRole) {
        "Staff" -> {
            MaintainerHomeScreen(
                sessionManager = sessionManager,
                navController = navController as NavHostController
            )
        }

        "Admin", "SuperAdmin" -> {
            AdminHomeScreen(
                sessionManager = sessionManager,
                navController = navController as NavHostController
            )
        }

        // Default: Resident (covers "Resident" and any unknown role)
        else -> {
            ResidentHomeScreen(
                sessionManager = sessionManager,
                navController = navController as NavHostController
            )
        }
    }
}