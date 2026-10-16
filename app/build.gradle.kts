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
        versionCode = 6
        versionName = "1.3.2-offline-voice-prototype"
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
    implementation("androidx.biometric:biometric:1.1.0")
    // Sherpa-ONNX includes the JVM API, JNI bridge, and native Android runtime.
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.8") {
        // The Android AAR already contains the Kotlin/JVM API classes.
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }
}
