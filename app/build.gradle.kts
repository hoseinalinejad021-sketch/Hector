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
        versionCode = 8
        versionName = "1.3.4-install-fix"
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
    implementation("androidx.biometric:biometric:1.1.0")
    // Sherpa-ONNX includes the JVM API, JNI bridge, and native Android runtime.
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.8") {
        // The Android AAR already contains the Kotlin/JVM API classes.
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }
}
