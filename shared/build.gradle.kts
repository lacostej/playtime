plugins {
    kotlin("multiplatform")
}

kotlin {
    // JVM target for fast unit tests; androidTarget() and the iOS targets are added with the apps.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
