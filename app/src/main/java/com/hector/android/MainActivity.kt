package com.hector.android

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
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
 * A local voice-matching prototype is present; liveness and replay resistance are not implemented,
 * so protected features stay locked.
 */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var unlockButton: Button
    private lateinit var voiceButton: Button
    private lateinit var advancedButton: Button
    @Volatile private var ownerVoiceMatched = false
    // Must remain false until a separately validated anti-spoof/liveness engine is integrated.
    @Volatile private var livenessPassed = false
    private val securityPrefs by lazy {
        getSharedPreferences("hector_security_latch", MODE_PRIVATE)
    }
    private var emergencyStopActive = true
    @Volatile private var biometricStagePassed = false
    private var pendingChallenge: ByteArray? = null
    @Volatile private var biometricStage = 1
    private val modelExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var speakerEngine: SpeakerModelEngine? = null

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
            val loadResult = try {
                SpeakerModelEngine(applicationContext) to null
            } catch (e: Exception) {
                null to (e.message?.take(180) ?: e.javaClass.simpleName)
            }
            val loaded = loadResult.first
            val loadError = loadResult.second
            runOnUiThread {
                if (isDestroyed) {
                    loaded?.close()
                    return@runOnUiThread
                }
                speakerEngine = loaded
                status.text = if (loaded != null) {
                    lockedMessage() + "\n\nموتور تشخیص گوینده آماده است؛ پس از اثر انگشت، صدای مالک بررسی می‌شود."
                } else {
                    lockedMessage() + "\n\nموتور تشخیص گوینده بارگذاری نشد. علت فنی: ${loadError ?: "نامشخص"}"
                }
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
            text = "مرحله ۱: زیست‌سنجی دستگاه"
            setOnClickListener { authenticateBiometric() }
        }
        voiceButton = Button(this).apply {
            text = "مرحله ۲: ثبت / بررسی صدای مالک"
            isEnabled = false
            setOnClickListener { startVoiceIdentityFlow() }
            contentDescription = "Enroll or compare the local owner voice profile; protected actions remain locked pending liveness validation"
        }
        advancedButton = Button(this).apply {
            text = "بازبینی امنیتی"
            setOnClickListener { showSecurityReviewScreen() }
        }
        val stopButton = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener { activateEmergencyStop() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(unlockButton)
        root.addView(advancedButton)
        root.addView(stopButton)
        setContentView(root)
        applyLockedControls()
    }

    private fun applyLockedControls() {
        // Voice comparison is a prototype; never unlock protected actions without validated liveness.
        voiceButton.isEnabled = biometricStagePassed && speakerEngine != null
        advancedButton.isEnabled = true
    }

    private fun lockedMessage(): String =
        "ورود هیکتور\n\n۱) زیست‌سنجی قوی دستگاه\n۲) تأیید زیست‌سنجی دوباره؛ اگر دستگاه چهرهٔ قوی ارائه کند ممکن است از چهره استفاده شود.\n\nاندروید انتخاب روش زیست‌سنجی را کنترل می‌کند؛ برنامه نمی‌تواند تضمین کند مرحلهٔ دوم فقط با چهره انجام شود. عملیات حساس قفل می‌ماند.\nHIC-STOP-01: توقف اضطراری در دسترس است."

    private fun activateEmergencyStop() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        biometricStage = 1
        emergencyStopActive = true
        biometricStagePassed = false
        ownerVoiceMatched = false
        livenessPassed = false
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
                    if (biometricStage == 1) {
                        biometricStage = 2
                        status.text = "مرحلهٔ اول تأیید شد. مرحلهٔ دوم زیست‌سنجی را انجام بده. اگر گوشی چهرهٔ قوی را ارائه کند، ممکن است بتوانی از آن استفاده کنی؛ اندروید روش را انتخاب می‌کند."
                        unlockButton.text = "مرحله ۲: زیست‌سنجی دستگاه"
                        authenticateBiometric()
                        return
                    }
                    biometricStage = 1
                    biometricStagePassed = true
                    ownerVoiceMatched = false
                    livenessPassed = false
                    emergencyStopActive = true
                    securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
                    unlockButton.text = "دو مرحلهٔ زیست‌سنجی تأیید شد"
                    unlockButton.isEnabled = false
                    status.text = "دو بار زیست‌سنجی دستگاه تأیید شد. این به معنی تأیید مستقل چهره نیست؛ عملیات حساس همچنان قفل است."
                    showOwnerHomeScreen()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // A failed biometric scan is non-terminal: Android keeps the prompt open for retry.
                    // Do not clear pendingChallenge here, or a later successful scan cannot complete
                    // the Keystore-bound proof. Terminal cancellation/errors are handled below.
                    status.text = "اثر انگشت/زیست‌سنجی تطبیق نکرد؛ دوباره تلاش کن. پنجره احراز هویت باز می‌ماند و قفل فعال است."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activateEmergencyStop()
                    status.text = "احراز هویت لغو شد یا خطا داشت. هیکتور قفل می‌ماند."
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن هیکتور — مرحله " + biometricStage + " از ۲")
            .setSubtitle(if (biometricStage == 1) "مرحله اول: زیست‌سنجی قوی دستگاه" else "مرحله دوم: زیست‌سنجی قوی دستگاه")
            .setDescription("اندروید نوع زیست‌سنجی را انتخاب می‌کند؛ چهره‌محور بودن این مرحله تضمین‌شده نیست.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("لغو")
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun startVoiceIdentityFlow() {
        if (!biometricStagePassed) {
            status.text = "ابتدا اثر انگشت را تأیید کن."
            return
        }
        if (speakerEngine == null) {
            status.text = "مدل تشخیص گوینده آماده نیست؛ قفل فعال می‌ماند."
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
                if (biometricStagePassed && speakerEngine != null) {
                    runVoiceIdentityOperation()
                } else {
                    activateEmergencyStop()
                    status.text = "مجوز برگشت، اما نشست احراز هویت معتبر نیست. دوباره از مرحله زیست‌سنجی شروع کن."
                }
            } else {
                status.text = "مجوز میکروفون رد شد؛ هیکتور قفل می‌ماند."
                activateEmergencyStop()
            }
        }
    }

    private fun runVoiceIdentityOperation() {
        // Permission callbacks and lifecycle changes can invalidate the biometric session.
        if (!biometricStagePassed || speakerEngine == null) {
            activateEmergencyStop()
            status.text = "نشست احراز هویت معتبر نیست؛ ضبط شروع نشد و قفل فعال است."
            return
        }
        // A voice match is not liveness proof; reset both states for every new attempt.
        ownerVoiceMatched = false
        livenessPassed = false
        voiceButton.isEnabled = false
        status.text = "ضبط محلی در حال آماده‌سازی است. محیط آرام انتخاب کن و هنگام هر ضبط، طبیعی صحبت کن."
        modelExecutor.execute {
            val flow = VoiceIdentityFlow(applicationContext)
            val engine = speakerEngine
            val result = try {
                if (engine == null) "موتور صدا آماده نیست."
                else if (!flow.hasProfile()) {
                    val embeddings = ArrayList<FloatArray>()
                    try {
                        repeat(3) { index ->
                            check(biometricStagePassed && !isDestroyed) {
                                "Authentication session expired; enrollment cancelled"
                            }
                            runOnUiThread {
                                status.text = "ثبت صدای مالک — نمونه ${index + 1} از ۳\nجمله «هیکتور، صدای من را ثبت کن» را طبیعی و پیوسته تکرار کن."
                            }
                            Thread.sleep(800)
                            val pcm = flow.captureThreeSeconds { biometricStagePassed && !isDestroyed }
                            runOnUiThread {
                                status.text = "نمونه ${index + 1} ضبط شد؛ موتور در حال تحلیل محلی صداست…"
                            }
                            try {
                                check(biometricStagePassed && !isDestroyed) {
                                    "Authentication session expired; enrollment cancelled"
                                }
                                embeddings.add(engine.computeEmbedding(pcm))
                            } finally {
                                pcm.fill(0f)
                            }
                            Thread.sleep(700)
                        }
                        check(biometricStagePassed && !isDestroyed) {
                            "Authentication session expired; enrollment cancelled"
                        }
                        flow.enroll(embeddings)
                        "سه نمونه صدا به‌صورت رمزگذاری‌شده روی همین گوشی ذخیره شد. حالا دوباره مرحله بررسی صدای مالک را اجرا کن تا وارد محیط پایه شوی."
                    } finally {
                        embeddings.forEach { it.fill(0f) }
                    }
                } else {
                    runOnUiThread { status.text = "برای بررسی صدای مالک، جمله «هیکتور، صدای من را ثبت کن» را طبیعی و پیوسته تکرار کن." }
                    val pcm = flow.captureThreeSeconds { biometricStagePassed && !isDestroyed }
                    val embedding = try {
                        engine.computeEmbedding(pcm)
                    } finally {
                        pcm.fill(0f)
                    }
                    val match = try {
                        flow.verify(embedding)
                    } finally {
                        embedding.fill(0f)
                    }
                    if (match) {
                        ownerVoiceMatched = true
                        livenessPassed = false
                        "OWNER_VOICE_MATCH_OK"
                    }
                    else "صدای واردشده با پروفایل مالک تطبیق نداشت؛ دسترسی رد شد و قفل فعال است."
                }
            } catch (e: SecurityException) {
                "دسترسی میکروفون رد شد؛ قفل فعال است."
            } catch (e: Exception) {
                "خطای ضبط/تشخیص: ${e.message?.take(180) ?: e.javaClass.simpleName}\nقفل امنیتی حفظ شد."
            }
            runOnUiThread {
                if (!isDestroyed) {
                    if (!biometricStagePassed) {
                        ownerVoiceMatched = false
                        livenessPassed = false
                        applyLockedControls()
                        status.text = lockedMessage()
                        return@runOnUiThread
                    }
                    applyLockedControls()
                    val profileReady = try { flow.hasProfile() } catch (_: Exception) { false }
                    voiceButton.text = if (profileReady) "مرحله ۲: بررسی صدای مالک" else "مرحله ۲: ثبت سه نمونه صدای مالک"
                    voiceButton.isEnabled = biometricStagePassed && speakerEngine != null
                    // Two-step login complete: fingerprint plus local owner-voice match.
                    if (ownerVoiceMatched && result == "OWNER_VOICE_MATCH_OK") {
                        emergencyStopActive = false
                        securityPrefs.edit().putBoolean("emergency_stop_engaged", false).commit()
                        showOwnerHomeScreen()
                    } else {
                        status.text = result
                    }
                }
            }
        }
    }


    /**
     * Basic app entry after biometric + local voice-profile match + spoken phrase check.
     * This is not authorization for network, commands, security settings, or other sensitive actions.
     */
    private fun showOwnerHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "HECTOR — بررسی زیست‌سنجی انجام شد"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val details = TextView(this).apply {
            text = "دو مرحله زیست‌سنجی دستگاه تأیید شدند. اندروید تضمین نمی‌کند مرحله دوم حتماً چهره باشد؛ بنابراین این نسخه تأیید مستقل چهره محسوب نمی‌شود.\n\nعملیات حساس و قابلیت‌های شبکه همچنان قفل هستند تا احراز هویت چهره‌محور واقعی و آزمون امنیتی انجام شود."
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val review = Button(this).apply {
            text = "بازبینی وضعیت"
            setOnClickListener { showSecurityReviewScreen() }
        }
        val stop = Button(this).apply {
            text = "HIC-STOP-01 — قفل کردن هیکتور"
            setOnClickListener {
                activateEmergencyStop()
                showLockedScreen()
            }
        }
        root.addView(title)
        root.addView(details)
        root.addView(review)
        root.addView(stop)
        setContentView(root)
    }

    /** Honest local diagnostics: this screen reports readiness but never unlocks protected actions. */
    private fun showSecurityReviewScreen() {
        val profileExists = try { VoiceIdentityFlow(applicationContext).hasProfile() } catch (_: Exception) { false }
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val modelReady = speakerEngine != null
        val stopEngaged = securityPrefs.getBoolean("emergency_stop_engaged", true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "HECTOR — بازبینی امنیتی"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val details = TextView(this).apply {
            text = buildString {
                append("نتیجه بررسی محلی این نسخه\n\n")
                append("• موتور تشخیص صدا: ").append(if (modelReady) "بارگذاری شده" else "آماده نیست").append("\n")
                append("• پروفایل صدای رمزگذاری‌شده: ").append(if (profileExists) "موجود" else "هنوز ثبت نشده").append("\n")
                append("• اجازه میکروفون: ").append(if (micPermission) "داده شده" else "داده نشده").append("\n")
                append("• توقف اضطراری: ").append(if (stopEngaged) "فعال/قفل" else "وضعیت غیرفعال در ذخیره‌سازی").append("\n")
                append("• چهرهٔ مستقل: هنوز پیاده‌سازی نشده؛ Android BiometricPrompt نوع چهره/اثر انگشت را برای برنامه تضمین نمی‌کند.\n\n")
                append("هنوز پیاده‌سازی/تأیید نشده: تشخیص زنده‌بودن صدا، ضدبازپخش قابل اتکا، کالیبراسیون نرخ خطا و آزمون واقعی روی گوشی.\n\n")
                append("این صفحه وضعیت فنی را گزارش می‌کند. دو بار احراز هویت قوی دستگاه، چهرهٔ مستقل را ثابت نمی‌کند؛ عملیات حساس قفل می‌ماند.")
            }
            textSize = 15f
            setTextColor(Color.rgb(226, 232, 240))
            setPadding(0, 20, 0, 20)
        }
        val refresh = Button(this).apply {
            text = "بازبینی دوباره"
            setOnClickListener { showSecurityReviewScreen() }
        }
        val back = Button(this).apply {
            text = "بازگشت به قفل هیکتور"
            setOnClickListener { showLockedScreen() }
        }
        val stop = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener {
                activateEmergencyStop()
                showLockedScreen()
            }
        }
        root.addView(title)
        root.addView(details)
        root.addView(refresh)
        root.addView(back)
        root.addView(stop)
        setContentView(root)
    }

    override fun onDestroy() {
        modelExecutor.shutdownNow()
        speakerEngine?.close()
        speakerEngine = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        pendingChallenge?.fill(0)
        pendingChallenge = null
        if (::unlockButton.isInitialized) {
            emergencyStopActive = true
            biometricStagePassed = false
            ownerVoiceMatched = false
            livenessPassed = false
            unlockButton.isEnabled = true
            unlockButton.text = "مرحله ۱: زیست‌سنجی دستگاه"
            applyLockedControls()
            if (::status.isInitialized) status.text = lockedMessage()
        }
    }
}
