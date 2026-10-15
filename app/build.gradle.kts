plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hector.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hector.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
