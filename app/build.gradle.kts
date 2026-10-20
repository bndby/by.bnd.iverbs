plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "by.bnd.iverbs"
    compileSdk = 36

    defaultConfig {
        applicationId = "by.bnd.iverbs"
        minSdk = 33
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":turn"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
}
