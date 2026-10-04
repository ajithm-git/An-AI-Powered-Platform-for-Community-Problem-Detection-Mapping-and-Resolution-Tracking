package com.example.myiapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.ui.navigation.AppNavigation
import com.example.myiapp.ui.theme.BackgroundLight
import com.example.myiapp.ui.theme.CommunityAITheme

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MainActivity — single-activity Compose host
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Responsibilities:
 *  • Set the Compose content with our theme
 *  • Host `AppNavigation` (NavHost)
 *  • Capture deep links (cold + warm start)
 *  • Provide the process-wide SessionManager to the UI tree
 *
 *  Session restoration happens in `MyAIAppApplication.onCreate()`
 *  so the JWT is already in RetrofitClient before this Activity
 *  builds the NavHost.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Session manager from the Application class (single source of truth)
    private val sessionManager: SessionManager
        get() = MyAIAppApplication.sessionManager

    // ─────────────────────────────────────────
    // Deep link state (observed by Compose)
    // ─────────────────────────────────────────
    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge (Android 15 / targetSdk 35)
        enableEdgeToEdge()

        // Capture deep link from launch intent (cold start)
        pendingDeepLink = extractDeepLink(intent)
        if (pendingDeepLink != null) {
            Log.d(TAG, "Deep link (cold start): $pendingDeepLink")
        }

        setContent {
            CommunityAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    AppNavigation(
                        sessionManager = sessionManager,
                        deepLinkUri = pendingDeepLink,
                        onDeepLinkConsumed = {
                            // Clear once the nav controller has handled it,
                            // so we don't re-navigate on recomposition
                            if (pendingDeepLink != null) {
                                Log.d(TAG, "Deep link consumed")
                                pendingDeepLink = null
                            }
                        }
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // Handle deep link when app is already running
    // ─────────────────────────────────────────
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // make getIntent() return latest

        val uri = extractDeepLink(intent)
        if (uri != null) {
            Log.d(TAG, "Deep link (warm start): $uri")
            pendingDeepLink = uri
        }
    }

    // ─────────────────────────────────────────
    // Extract a valid communityai:// deep link
    // ─────────────────────────────────────────
    private fun extractDeepLink(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return if (data.scheme == "communityai") data else null
    }
}