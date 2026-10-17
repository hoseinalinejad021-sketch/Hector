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
        versionCode = 15
        versionName = "1.4.1-special-hardening"
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

// Fetch a pinned, checksum-verified speaker-embedding model into generated assets.
// The ONNX file is ~83 MB; it is not committed as a binary Git blob, but is bundled
// into the APK during builds. A failed download/checksum must fail the build.
val ownerVoiceModel = layout.buildDirectory.file("generated/ownerModels/assets/owner_voice/ecapa_tdnn.onnx")
val downloadOwnerVoiceModel by tasks.registering {
    outputs.file(ownerVoiceModel)
    doLast {
        val target = ownerVoiceModel.get().asFile
        if (target.exists() && target.length() == 83404680L) {
            val existingDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            if (existingDigest == "9acb086d5d9efca07266930ce03772f13cad5fd5a415bcf014ee4d82bed26311") return@doLast
            target.delete()
        }
        target.parentFile.mkdirs()
        val temporary = java.io.File(target.parentFile, target.name + ".download")
        val url = java.net.URL("https://huggingface.co/astrolabos/ecapa-tdnn/resolve/main/ecapa_tdnn.onnx")
        try {
            val connection = url.openConnection()
            connection.connectTimeout = 20000
            connection.readTimeout = 120000
            connection.getInputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            require(temporary.length() == 83404680L) { "Owner voice model has unexpected size: " + temporary.length() }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(temporary.readBytes()).joinToString("") { "%02x".format(it) }
            require(digest == "9acb086d5d9efca07266930ce03772f13cad5fd5a415bcf014ee4d82bed26311") {
                "Owner voice model SHA-256 mismatch; refusing to package unverified model"
            }
            check(temporary.renameTo(target)) { "Could not place verified owner voice model" }
        } finally {
            temporary.delete()
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/ownerModels/assets"))
tasks.named("preBuild").configure { dependsOn(downloadOwnerVoiceModel) }

dependencies {
    // Direct dependency for Activity Result APIs used by the Persian speech recognizer.
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    // ONNX Runtime is needed to execute the bundled speaker-embedding model.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    testImplementation("junit:junit:4.13.2")
}
