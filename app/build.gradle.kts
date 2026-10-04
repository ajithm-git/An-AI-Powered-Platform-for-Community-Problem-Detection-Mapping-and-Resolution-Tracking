plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.myiapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myiapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "3.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Keep base URL pointing to local backend
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // ─────────────────────────────────────────────
    // Core Android & Lifecycle
    // ─────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(libs.androidx.appcompat)

    // ─────────────────────────────────────────────
    // Jetpack Compose (via BOM — no explicit versions)
    // ─────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.material)

    // ─────────────────────────────────────────────
    // Navigation Compose
    // ─────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // ─────────────────────────────────────────────
    // DataStore (for SessionManager — saves JWT locally)
    // ─────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ─────────────────────────────────────────────
    // Networking (Retrofit + Gson + OkHttp)
    // ─────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ─────────────────────────────────────────────
    // Kotlin Coroutines
    // ─────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ─────────────────────────────────────────────
    // Image Loading (Coil — avatars, thumbnails)
    // ─────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ─────────────────────────────────────────────
    // Testing
    // ─────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}