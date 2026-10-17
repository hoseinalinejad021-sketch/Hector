package com.hector.android

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.util.concurrent.Executors

/**
 * Fail-closed first-stage gate.
 * Biometric success authorizes a Keystore operation only.
 * Owner-specific voice verification is not implemented; protected features stay locked.
 */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var unlockButton: Button
    private lateinit var voiceButton: Button
    private lateinit var microphoneButton: Button
    private val securityPrefs by lazy {
        getSharedPreferences("hector_security_latch", MODE_PRIVATE)
    }
    private var emergencyStopActive = true
    private var biometricStagePassed = false
    private var pendingChallenge: ByteArray? = null
    private val modelExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var speakerEngine: SpeakerModelEngine? = null
    @Volatile private var spokenChallengeEngine: SpokenChallengeEngine? = null
    private val replayChallenge = ReplayChallenge()

    companion object { private const val AUDIO_PERMISSION_REQUEST = 42 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        emergencyStopActive = securityPrefs.getBoolean("emergency_stop_engaged", true)
        showLockedScreen()
        initializeSpeakerModelEngine()
    }

    private fun initializeSpeakerModelEngine() {
        status.text = lockedMessage() + "\n\nموتور تشخیص گوینده: در حال بارگذاری محلی…"
        modelExecutor.execute {
            val loadedSpeaker = try { SpeakerModelEngine(applicationContext) } catch (_: Exception) { null }
            val loadedAsr = try { SpokenChallengeEngine(applicationContext) } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed) {
                    loadedSpeaker?.close()
                    loadedAsr?.close()
                    return@runOnUiThread
                }
                if (loadedSpeaker != null && loadedAsr != null) {
                    speakerEngine = loadedSpeaker
                    spokenChallengeEngine = loadedAsr
                    status.text = lockedMessage() + "\n\nمدل تشخیص گوینده و تشخیص گفتار آفلاین آماده‌اند؛ هر دو از یک ضبط استفاده می‌کنند. عملیات حساس همچنان قفل است."
                } else {
                    loadedSpeaker?.close()
                    loadedAsr?.close()
                    speakerEngine = null
                    spokenChallengeEngine = null
                    status.text = lockedMessage() + "\n\nیکی از مدل‌های محلی بارگذاری نشد؛ ضدبازپخش و تطبیق صدا قابل اجرا نیست و قفل می‌ماند."
                }
                applyLockedControls()
            }
        }
    }

    private fun showLockedScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "HECTOR"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = lockedMessage()
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        unlockButton = Button(this).apply {
            text = "مرحله ۱: تأیید زیست‌سنجی"
            setOnClickListener { authenticateBiometric() }
        }
        voiceButton = Button(this).apply {
            text = "مرحله ۲: ثبت / بررسی صدای مالک"
            isEnabled = false
            setOnClickListener { startVoiceIdentityFlow() }
            contentDescription = "Enroll or compare the local owner voice profile; protected actions remain locked pending liveness validation"
        }
        microphoneButton = Button(this).apply {
            text = "میکروفون فقط برای نمونه صدای هیکتور"
            isEnabled = false
            contentDescription = "Microphone is used only for a user-triggered local voice sample"
        }
        val stopButton = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener { activateEmergencyStop() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(unlockButton)
        root.addView(voiceButton)
        root.addView(microphoneButton)
        root.addView(stopButton)
        setContentView(root)
        applyLockedControls()
    }

    private fun applyLockedControls() {
        // Voice comparison is a prototype; never unlock protected actions without validated liveness.
        voiceButton.isEnabled = biometricStagePassed && speakerEngine != null && spokenChallengeEngine != null
        microphoneButton.isEnabled = false
    }

    private fun lockedMessage(): String =
        "دروازه امنیتی فعال است\n\n۱) احراز هویت زیست‌سنجی قوی دستگاه\n۲) ثبت/مقایسه محلی صدای مالک (آزمایشی)\n۳) تشخیص زنده‌بودن و ضدبازپخش هنوز کامل نیست\n\nشبکه و عملیات حساس تا اعتبارسنجی امنیتی قفل می‌مانند.\nHIC-STOP-01: توقف ایمن فعال"

    private fun activateEmergencyStop() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        emergencyStopActive = true
        biometricStagePassed = false
        securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
        unlockButton.isEnabled = true
        unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
        applyLockedControls()
        status.text = "توقف اضطراری فعال شد. شبکه، میکروفون و عملیات حساس قفل هستند."
    }

    private fun createAuthBoundCipher(): Cipher {
        val alias = "hector_owner_gate_key_v1"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val specBuilder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specBuilder.setInvalidatedByBiometricEnrollment(true)
            }
            generator.init(specBuilder.build())
            generator.generateKey()
        }
        val key = keyStore.getKey(alias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    private fun authenticateBiometric() {
        val availability = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            activateEmergencyStop()
            status.text = "زیست‌سنجی قوی آماده نیست. قفل هیکتور باز نمی‌شود."
            return
        }

        val cipher = try {
            createAuthBoundCipher()
        } catch (_: Exception) {
            activateEmergencyStop()
            status.text = "کلید امن دستگاه آماده نشد. هیکتور در حالت قفل باقی می‌ماند."
            return
        }

        pendingChallenge?.fill(0)
        pendingChallenge = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val challenge = pendingChallenge
                    val proof = try {
                        val authorizedCipher = result.cryptoObject?.cipher
                            ?: throw IllegalStateException("Missing authorized cipher")
                        authorizedCipher.doFinal(challenge ?: throw IllegalStateException("Missing challenge"))
                    } catch (_: Exception) {
                        null
                    } finally {
                        pendingChallenge?.fill(0)
                        pendingChallenge = null
                    }

                    if (proof == null || proof.isEmpty()) {
                        activateEmergencyStop()
                        status.text = "تأیید رمزنگاری‌شده انجام نشد. قفل هیکتور باز نمی‌شود."
                        return
                    }
                    proof.fill(0)
                    // Biometrics alone do not release the latch or authorize protected features.
                    biometricStagePassed = true
                    securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
                    status.text = "مرحله ۱ تأیید شد. اکنون ثبت/مقایسه صدای مالک را اجرا کن. حتی در صورت تطبیق صدا، عملیات حساس تا تکمیل تشخیص زنده‌بودن قفل می‌ماند."
                    unlockButton.text = "مرحله ۱ تأیید شد"
                    unlockButton.isEnabled = false
                    voiceButton.text = if (VoiceIdentityFlow(this@MainActivity).hasProfile()) "مرحله ۲: بررسی صدای مالک" else "مرحله ۲: ثبت سه نمونه صدای مالک"
                    applyLockedControls()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    activateEmergencyStop()
                    status.text = "تأیید زیست‌سنجی ناموفق بود. هیکتور قفل می‌ماند."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activateEmergencyStop()
                    status.text = "احراز هویت لغو شد یا خطا داشت. هیکتور قفل می‌ماند."
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن هیکتور")
            .setSubtitle("مرحله اول: احراز هویت زیست‌سنجی قوی دستگاه")
            .setDescription("تأیید صدای اختصاصی مالک نیز الزامی است.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("لغو")
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun startVoiceIdentityFlow() {
        if (!biometricStagePassed) {
            status.text = "ابتدا مرحله زیست‌سنجی را تأیید کن. توقف اضطراری همچنان عملیات حساس و شبکه را مسدود نگه می‌دارد."
            return
        }
        // Local owner-voice enrollment/comparison is allowed during the stop latch.
        // The latch remains engaged and no sensitive/network action is authorized.
        if (speakerEngine == null || spokenChallengeEngine == null) {
            status.text = "مدل تشخیص گوینده یا گفتار آماده نیست؛ قفل فعال می‌ماند."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        runVoiceIdentityOperation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runVoiceIdentityOperation()
            } else {
                status.text = "مجوز میکروفون رد شد؛ هیکتور قفل می‌ماند."
                activateEmergencyStop()
            }
        }
    }

    private fun runVoiceIdentityOperation() {
        voiceButton.isEnabled = false
        status.text = "چالش گفتاری تصادفی در حال آماده‌سازی است؛ عبارت روی صفحه را دقیق بخوان."
        modelExecutor.execute {
            val flow = VoiceIdentityFlow(applicationContext)
            val speaker = speakerEngine
            val asr = spokenChallengeEngine
            val enrolledEmbeddings = ArrayList<FloatArray>()
            val result = try {
                if (speaker == null || asr == null) {
                    "مدل محلی آماده نیست؛ هیچ دسترسی حساسی باز نشد."
                } else if (!flow.hasProfile()) {
                    repeat(3) { index ->
                        val challenge = replayChallenge.issue(SystemClock.elapsedRealtime())
                        runOnUiThread {
                            status.text = "ثبت مالک — نمونه ${index + 1} از ۳\nعبارت را دقیق و طبیعی بخوان:\n\n${challenge.phrase}\n\nاین عبارت فقط ۱۵ ثانیه اعتبار دارد."
                        }
                        Thread.sleep(700)
                        val pcm = flow.captureChallengeAudio()
                        val captureCompletedAt = SystemClock.elapsedRealtime()
                        try {
                            // ASR and speaker embedding receive the exact same captured PCM.
                            val transcript = asr.transcribeSameCapture(pcm)
                            val phraseOk = replayChallenge.consumeTranscript(
                                transcript, captureCompletedAt
                            )
                            if (!phraseOk) {
                                throw IllegalStateException("عبارت شنیده‌شده با چالش تصادفی مطابقت نداشت؛ دوباره تلاش کن.")
                            }
                            enrolledEmbeddings.add(speaker.computeEmbedding(pcm))
                        } finally {
                            pcm.fill(0f)
                        }
                        Thread.sleep(300)
                    }
                    flow.enroll(enrolledEmbeddings)
                    "سه نمونه پس از تطبیق عبارت تصادفی و الگوی گوینده، رمزگذاری‌شده روی گوشی ذخیره شد. این آزمون هنوز اثبات زیستی زنده‌بودن نیست؛ عملیات حساس قفل است."
                } else {
                    val challenge = replayChallenge.issue(SystemClock.elapsedRealtime())
                    runOnUiThread {
                        status.text = "بررسی مالک\nعبارت تصادفی را دقیق و طبیعی بخوان:\n\n${challenge.phrase}\n\nفقط ۱۵ ثانیه فرصت داری."
                    }
                    Thread.sleep(700)
                    val pcm = flow.captureChallengeAudio()
                    val captureCompletedAt = SystemClock.elapsedRealtime()
                    try {
                        // Both checks are calculated from the same PCM buffer.
                        val transcript = asr.transcribeSameCapture(pcm)
                        val phraseOk = replayChallenge.consumeTranscript(
                            transcript, captureCompletedAt
                        )
                        if (!phraseOk) {
                            "عبارت چالش اشتباه یا منقضی شد؛ صدای مالک تأیید نشد و قفل فعال است."
                        } else {
                            val embedding = speaker.computeEmbedding(pcm)
                            try {
                                val match = flow.verify(embedding)
                                if (match) {
                                    "عبارت تصادفی و الگوی صدای ذخیره‌شده هر دو تطبیق داشتند. این فقط یک مانع ضدبازپخش است، نه اثبات قطعی زنده‌بودن؛ عملیات حساس همچنان قفل است."
                                } else {
                                    "عبارت درست بود اما الگوی صدای مالک تطبیق نداشت؛ دسترسی رد شد."
                                }
                            } finally {
                                embedding.fill(0f)
                            }
                        }
                    } finally {
                        pcm.fill(0f)
                    }
                }
            } catch (e: SecurityException) {
                replayChallenge.clear()
                "دسترسی میکروفون رد شد؛ هیکتور قفل می‌ماند."
            } catch (e: Exception) {
                replayChallenge.clear()
                e.message ?: "خطای ضبط یا تشخیص؛ هیچ دسترسی حساسی باز نشد."
            } finally {
                enrolledEmbeddings.forEach { it.fill(0f) }
            }
            runOnUiThread {
                if (!isDestroyed) {
                    status.text = result
                    voiceButton.text = if (flow.hasProfile()) "مرحله ۲: بررسی صدای مالک" else "مرحله ۲: ثبت سه نمونه صدای مالک"
                    voiceButton.isEnabled = biometricStagePassed && speakerEngine != null && spokenChallengeEngine != null
                }
            }
        }
    }

    override fun onDestroy() {
        modelExecutor.shutdownNow()
        replayChallenge.clear()
        speakerEngine?.close()
        speakerEngine = null
        spokenChallengeEngine?.close()
        spokenChallengeEngine = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        pendingChallenge?.fill(0)
        pendingChallenge = null
        if (::unlockButton.isInitialized) {
            emergencyStopActive = true
            biometricStagePassed = false
            unlockButton.isEnabled = true
            unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
            applyLockedControls()
            if (::status.isInitialized) status.text = lockedMessage()
        }
    }
}
