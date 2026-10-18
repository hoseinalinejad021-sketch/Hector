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
        versionCode = 24
        versionName = "1.5.0-stop-only"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

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
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.am3n:PersianTTS:1.3.0")
    testImplementation("junit:junit:4.13.2")
}
