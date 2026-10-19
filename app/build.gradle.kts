import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase is optional at build time until the owner supplies app/google-services.json.
// Once that project-specific file is present, Gradle configures the Firebase resources.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val releaseKeystoreBase64 = System.getenv("HECTOR_RELEASE_KEYSTORE_BASE64").orEmpty()
val releaseStorePassword = System.getenv("HECTOR_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("HECTOR_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("HECTOR_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningConfigured = releaseKeystoreBase64.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()
val releaseKeystoreFile = layout.buildDirectory.file("secure-signing/hector-release.jks").get().asFile

if (releaseSigningConfigured) {
    releaseKeystoreFile.parentFile.mkdirs()
    releaseKeystoreFile.writeBytes(Base64.getDecoder().decode(releaseKeystoreBase64))
}

android {
    namespace = "com.hector.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hector.android"
        minSdk = 23
        targetSdk = 36
        versionCode = 26
        versionName = "1.6.0-background-voice"
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
        if (releaseSigningConfigured) {
            create("hectorRelease") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("hectorRelease")
            }
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
