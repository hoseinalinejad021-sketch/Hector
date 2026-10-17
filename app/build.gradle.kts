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
        versionCode = 9
        versionName = "1.3.5-persian-voice"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The sherpa-onnx AAR bundles desktop runtimes too. Keep only Android
    // resources in the APK; Android native libraries remain under lib/<abi>.
    packaging {
        resources {
            excludes += setOf(
                "sherpa-onnx/native/osx-aarch64/**",
                "sherpa-onnx/native/osx-x64/**",
                "sherpa-onnx/native/win-arm64/**",
                "sherpa-onnx/native/win-x64/**"
            )
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.biometric:biometric:1.1.0")
    // Sherpa-ONNX includes the JVM API, JNI bridge, and native Android runtime.
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.8") {
        // The Android AAR already contains the Kotlin/JVM API classes.
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }
}
