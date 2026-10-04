package com.example.myiapp

import android.app.Application
import android.util.Log
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.network.RetrofitClient

/**
 * ═══════════════════════════════════════════════════════════════════
 *  MyAIAppApplication — process-wide Application class
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Responsibilities:
 *  • Initialize SessionManager once for the whole app
 *  • Restore the JWT from DataStore into RetrofitClient on cold start
 *    so every subsequent API call is authenticated
 *  • Log basic startup info
 *
 *  ⚠️ Register this in AndroidManifest.xml:
 *     <application android:name=".MyAIAppApplication" ... >
 */
class MyAIAppApplication : Application() {

    companion object {
        private const val TAG = "MyAIAppApplication"

        /**
         * Singleton session manager — available from anywhere via
         * `MyAIAppApplication.sessionManager`.
         */
        lateinit var sessionManager: SessionManager
            private set
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Application starting…")

        // Create the SessionManager using Application context (no leaks)
        sessionManager = SessionManager(applicationContext)

        // Restore JWT from DataStore into RetrofitClient asynchronously
        // so the very first API call after cold start is authenticated.
        sessionManager.restoreSessionAsync()

        Log.d(TAG, "✅ SessionManager initialized")
    }
}