plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

android {
    namespace = "io.github.lacostej.playtime.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

val ktor = "3.6.1"

kotlin {
    // JVM target for fast unit tests and the read-only dev check; iOS targets come with the iOS app.
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-android:$ktor")
        }
    }
}

// Read-only check against a real account: ./gradlew :shared:readOnlyCheck (session token path in PLAYTIME_TOKEN_FILE).
tasks.register<JavaExec>("readOnlyCheck") {
    group = "verification"
    description = "Reads today's state from Nintendo with the shared client. Never writes."
    val jvm = kotlin.jvm()
    dependsOn(jvm.compilations["main"].compileTaskProvider)
    classpath = files(jvm.compilations["main"].output.allOutputs, jvm.compilations["main"].runtimeDependencyFiles)
    mainClass.set("io.github.lacostej.playtime.dev.ReadOnlyCheckKt")
}
