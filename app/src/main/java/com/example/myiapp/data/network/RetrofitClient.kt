package com.example.myiapp.data.network

import android.os.Build
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════════
 *  RetrofitClient — Singleton network layer for MyAIApp
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Base URL is chosen automatically:
 *
 *    • EMULATOR           → http://10.0.2.2:5000/
 *         10.0.2.2 is the host machine's localhost from inside
 *         the Android emulator.
 *
 *    • PHYSICAL DEVICE    → http://localhost:5000/
 *         Requires running: `adb reverse tcp:5000 tcp:5000`
 *         once per USB (re)connection.
 *
 *         ALTERNATIVE (LAN IP): if you prefer Wi-Fi instead of adb:
 *         replace `localhost` below with your PC's LAN IP,
 *         e.g. "http://192.168.1.42:5000/" — and make sure your
 *         phone is on the same Wi-Fi + Windows Firewall allows port 5000.
 *
 *  The BASE_URL ends with "/" because Retrofit requires it.
 *  Paths in ApiService.kt already include the "api/" prefix.
 * ═══════════════════════════════════════════════════════════════════
 */
object RetrofitClient {

    private const val TAG = "RetrofitClient"

    // ═════════════════════════════════════════════
    // BASE URL — auto-detected
    // ═════════════════════════════════════════════
    private val BASE_URL: String = if (isEmulator()) {
        Log.d(TAG, "📡 Device is EMULATOR → using http://10.0.2.2:5000/")
        "http://10.0.2.2:5000/"
    } else {
        Log.d(TAG, "📡 Device is PHYSICAL → using http://localhost:5000/ (adb reverse)")
        "http://localhost:5000/"

        // ─────────────────────────────────────────
        // ALTERNATIVE: LAN IP (if you're not using adb reverse)
        // ─────────────────────────────────────────
        // Uncomment the line below and replace with your PC's actual IP:
        //
        // "http://192.168.1.42:5000/"
    }

    // ═════════════════════════════════════════════
    // EMULATOR DETECTION
    // ═════════════════════════════════════════════
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.FINGERPRINT.contains("vbox")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MODEL.contains("Android SDK built for arm")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT == "google_sdk"
                || Build.PRODUCT == "sdk"
                || Build.PRODUCT == "sdk_google"
                || Build.PRODUCT == "sdk_x86"
                || Build.PRODUCT == "vbox86p"
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    // ═════════════════════════════════════════════
    // SESSION — JWT token holder
    // ═════════════════════════════════════════════
    // SessionManager sets this on login and clears it on logout.
    @Volatile
    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
        Log.d(TAG, if (token.isNullOrBlank()) "🔓 Token cleared" else "🔐 Token set (${token.take(12)}…)")
    }

    fun getAuthToken(): String? = authToken

    fun hasToken(): Boolean = !authToken.isNullOrBlank()

    // ═════════════════════════════════════════════
    // AUTH INTERCEPTOR
    // ═════════════════════════════════════════════
    // Automatically adds: Authorization: Bearer <jwt>
    // Skips injection when the request has header "No-Auth: true"
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        val skipAuth = originalRequest.header("No-Auth") == "true"

        val newRequest = if (!skipAuth && !authToken.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $authToken")
                .build()
        } else {
            originalRequest
        }

        // Remove the internal "No-Auth" header before sending
        val finalRequest = newRequest.newBuilder()
            .removeHeader("No-Auth")
            .build()

        chain.proceed(finalRequest)
    }

    // ═════════════════════════════════════════════
    // LOGGING INTERCEPTOR
    // ═════════════════════════════════════════════
    // Filter Logcat by "OkHttp" to see requests/responses.
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("OkHttp", message)
    }.apply {
        level = if (isDebugBuild()) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val buildConfigClass = Class.forName("com.example.myiapp.BuildConfig")
            val debugField = buildConfigClass.getField("DEBUG")
            debugField.getBoolean(null)
        } catch (_: Throwable) {
            true
        }
    }

    // ═════════════════════════════════════════════
    // OkHttpClient (shared across Retrofit)
    // ═════════════════════════════════════════════
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)   // TCP handshake
            .readTimeout(90, TimeUnit.SECONDS)      // YOLO + Gemini can be slow
            .writeTimeout(90, TimeUnit.SECONDS)     // Upload base64 images
            .retryOnConnectionFailure(true)
            .build()
    }

    // ═════════════════════════════════════════════
    // Retrofit instance
    // ═════════════════════════════════════════════
    val apiService: ApiService by lazy {
        Log.d(TAG, "🏗️ Building Retrofit with BASE_URL = $BASE_URL")
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ═════════════════════════════════════════════
    // Alternative instance WITHOUT auth
    // ═════════════════════════════════════════════
    val apiServiceNoAuth: ApiService by lazy {
        val noAuthClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(noAuthClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ═════════════════════════════════════════════
    // Optional helper — build against a custom URL
    // ═════════════════════════════════════════════
    fun buildWithBaseUrl(baseUrl: String): ApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // ═════════════════════════════════════════════
    // Diagnostic helper — log current base URL
    // ═════════════════════════════════════════════
    fun getBaseUrl(): String = BASE_URL
}