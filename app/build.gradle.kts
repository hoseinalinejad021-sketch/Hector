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
        versionCode = 23
        versionName = "1.4.9-gpt-live"
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
    // WebSocket transport for OpenAI GPT-Live realtime audio.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Offline Persian text-to-speech engine (Sherpa-ONNX/eSpeak).
    implementation("com.github.am3n:PersianTTS:1.3.0")
    testImplementation("junit:junit:4.13.2")
}
