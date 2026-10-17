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
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "META-INF/*.version",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/**",
                "linux-x64/**",
                "osx-x64/**",
                "osx-arm64/**",
                "win-x64/**",
                "win-x86/**"
            )
        }
    }
}
