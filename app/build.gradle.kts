plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hector.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hector.android"
        minSdk = 23
        targetSdk = 35
        versionCode = 11
        versionName = "1.3.7-speech-chat-cleanup"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Explicitly use the backed-up CI debug key; do not let AGP create a new signer.
    signingConfigs {
        create("stableDebug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }
}

dependencies {
    // Direct dependency for Activity Result APIs used by the Persian speech recognizer.
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
