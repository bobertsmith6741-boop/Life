plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mockpilot.xposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mockpilot.xposed"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Xposed API is provided by the LSPosed framework at runtime — compileOnly, never bundled.
    compileOnly(libs.xposed.api)
    implementation(libs.androidx.core.ktx)
}
