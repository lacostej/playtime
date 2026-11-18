plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "org.example.playtime.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.example.playtime" // placeholder until the app has a name
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        // Writes to the console stay off unless built with -Pplaytime.writes=true.
        buildConfigField("boolean", "WRITES_ENABLED", (findProperty("playtime.writes") == "true").toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("io.ktor:ktor-client-android:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
