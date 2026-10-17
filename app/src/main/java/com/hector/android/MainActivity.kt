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
 * Owner-specific voice verification is not implemented; protected features stay locked.
 */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var unlockButton: Button
    private lateinit var voiceButton: Button
    private lateinit var microphoneButton: Button
    private lateinit var enterAppButton: Button
    private lateinit var advancedButton: Button
    @Volatile private var ownerVoiceMatched = false
    private val securityPrefs by lazy {
        getSharedPreferences("hector_security_latch", MODE_PRIVATE)
    }
    private var emergencyStopActive = true
    private var biometricStagePassed = false
    private var pendingChallenge: ByteArray? = null
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
                    lockedMessage() + "\n\nموتور مدل محلی بارگذاری شد؛ احراز هویت صوتی و دسترسی حساس هنوز قفل است."
                } else {
                    lockedMessage() + "\n\nموتور مدل بارگذاری نشد. علت فنی: ${loadError ?: "نامشخص"}\nقفل امنیتی فعال می‌ماند."
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
            text = "مرحله ۱: تأیید زیست‌سنجی"
            setOnClickListener { authenticateBiometric() }
        }
        voiceButton = Button(this).apply {
            text = "مرحله ۲: ثبت / بررسی صدای مالک"
            isEnabled = false
            setOnClickListener { startVoiceIdentityFlow() }
            contentDescription = "Enroll or compare the local owner voice profile; protected actions remain locked pending liveness validation"
        }
        enterAppButton = Button(this).apply {
            text = "ورود خودکار با صدای زنده مالک"
            isEnabled = false
            contentDescription = "The owner enters automatically after live voice matching; no manual entry button is required"
        }
        advancedButton = Button(this).apply {
            text = "مرحله ۳: قابلیت‌های پیشرفته"
            setOnClickListener {
                status.text = "قابلیت‌های پیشرفته هنوز فعال نشده‌اند: تشخیص زنده‌بودن و ضدبازپخش باید پیاده‌سازی و روی گوشی آزموده شوند. شبکه، اجرای فرمان و تغییر تنظیمات امنیتی قفل می‌مانند."
            }
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
        root.addView(enterAppButton)
        root.addView(advancedButton)
        root.addView(microphoneButton)
        root.addView(stopButton)
        setContentView(root)
        applyLockedControls()
    }

    private fun applyLockedControls() {
        // Voice comparison is a prototype; never unlock protected actions without validated liveness.
        voiceButton.isEnabled = biometricStagePassed && speakerEngine != null
        microphoneButton.isEnabled = false
        enterAppButton.isEnabled = false
        advancedButton.isEnabled = true
    }

    private fun lockedMessage(): String =
        "دروازه امنیتی فعال است\n\n۱) احراز هویت زیست‌سنجی قوی دستگاه\n۲) ثبت/مقایسه محلی صدای مالک (آزمایشی)\n۳) تشخیص زنده‌بودن و ضدبازپخش هنوز کامل نیست\n\nشبکه و عملیات حساس تا اعتبارسنجی امنیتی قفل می‌مانند.\nHIC-STOP-01: توقف ایمن فعال"

    private fun activateEmergencyStop() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        emergencyStopActive = true
        biometricStagePassed = false
        ownerVoiceMatched = false
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
                runVoiceIdentityOperation()
            } else {
                status.text = "مجوز میکروفون رد شد؛ هیکتور قفل می‌ماند."
                activateEmergencyStop()
            }
        }
    }

    private fun runVoiceIdentityOperation() {
        voiceButton.isEnabled = false
        status.text = "ضبط محلی در حال آماده‌سازی است. محیط آرام انتخاب کن و هنگام هر ضبط، طبیعی صحبت کن."
        modelExecutor.execute {
            val flow = VoiceIdentityFlow(applicationContext)
            val engine = speakerEngine
            val result = try {
                if (engine == null) "موتور صدا آماده نیست."
                else if (!flow.hasProfile()) {
                    val embeddings = ArrayList<FloatArray>()
                    repeat(3) { index ->
                        runOnUiThread {
                            status.text = "ثبت صدای مالک — نمونه ${index + 1} از ۳\nاکنون ضبط زنده به مدت ۳ ثانیه شروع می‌شود؛ طبیعی و پیوسته صحبت کن."
                        }
                        Thread.sleep(800)
                        val pcm = flow.captureThreeSeconds()
                        runOnUiThread {
                            status.text = "نمونه ${index + 1} ضبط شد؛ موتور در حال تحلیل محلی صداست…"
                        }
                        try {
                            embeddings.add(engine.computeEmbedding(pcm))
                        } finally {
                            pcm.fill(0f)
                        }
                        Thread.sleep(700)
                    }
                    flow.enroll(embeddings)
                    embeddings.forEach { it.fill(0f) }
                    "سه نمونه صدا به‌صورت رمزگذاری‌شده روی همین گوشی ذخیره شد. تطبیق صدا آزمایشی است؛ قفل عملیات حساس باز نشده است."
                } else {
                    runOnUiThread { status.text = "در حال ضبط نمونه زنده برای مقایسه محلی…" }
                    val pcm = flow.captureThreeSeconds()
                    val embedding = engine.computeEmbedding(pcm)
                    pcm.fill(0f)
                    val match = flow.verify(embedding)
                    embedding.fill(0f)
                    if (match) {
                        ownerVoiceMatched = true
                        "الگوی صدا با پروفایل ذخیره‌شده تطبیق دارد. ورود به محیط پایه ممکن است؛ عملیات حساس و شبکه همچنان قفل است چون تشخیص زنده‌بودن تأیید نشده."
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
                    applyLockedControls()
                    voiceButton.text = if (flow.hasProfile()) "مرحله ۲: بررسی صدای مالک" else "مرحله ۲: ثبت سه نمونه صدای مالک"
                    voiceButton.isEnabled = biometricStagePassed && speakerEngine != null
                    if (ownerVoiceMatched) {
                        showHomeScreen()
                    } else {
                        status.text = result
                    }
                }
            }
        }
    }


    /** Opens the basic home screen automatically after live owner-voice matching. */
    private fun showHomeScreen() {
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
        val message = TextView(this).apply {
            text = "ورود گفتاری مالک تأیید شد.\nبه محیط پایه هیکتور وارد شدی.\n\nعملیات پیشرفته، شبکه و اجرای فرمان تا تکمیل تشخیص زنده‌بودن و آزمون امنیتی قفل هستند."
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val advanced = Button(this).apply {
            text = "مرحله ۳: قابلیت‌های پیشرفته"
            setOnClickListener {
                message.text = "این قابلیت هنوز در دسترس نیست. تشخیص زنده‌بودن و ضدبازپخش هنوز تأیید نشده‌اند؛ بنابراین شبکه، اجرای فرمان و تغییر تنظیمات امنیتی قفل می‌مانند."
            }
        }
        val stop = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener {
                activateEmergencyStop()
                showLockedScreen()
            }
        }
        val relock = Button(this).apply {
            text = "قفل و خروج"
            setOnClickListener {
                activateEmergencyStop()
                showLockedScreen()
            }
        }
        root.addView(title)
        root.addView(message)
        root.addView(advanced)
        root.addView(stop)
        root.addView(relock)
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
            unlockButton.isEnabled = true
            unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
            applyLockedControls()
            if (::status.isInitialized) status.text = lockedMessage()
        }
    }
}
