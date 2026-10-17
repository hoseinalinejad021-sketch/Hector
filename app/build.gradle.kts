plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hector.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hector.android"
        minSdk = 23
        targetSdk = 36
        versionCode = 11
        versionName = "1.5.0-android16-face-gate"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("stableDebug") }
    }
}

dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
