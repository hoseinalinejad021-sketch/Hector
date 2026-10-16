package com.hector.android

import android.Manifest
import android.content.pm.PackageManager
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
    private var pendingChallenge: ByteArray? = null

    companion object { private const val AUDIO_PERMISSION_REQUEST = 42 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        emergencyStopActive = securityPrefs.getBoolean("emergency_stop_engaged", true)
        showLockedScreen()
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
            text = "مرحله ۲: ورود صوتی مالک هنوز پیاده‌سازی نشده"
            isEnabled = false
            contentDescription = "Owner voice authentication is not implemented; protected access remains locked"
        }
        microphoneButton = Button(this).apply {
            text = "آزمایش میکروفون (فعلاً غیرفعال)"
            isEnabled = false
            contentDescription = "Microphone access is disabled until a reviewed voice-authentication flow exists"
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
        // No implemented voice verifier means audio capture must not be available.
        voiceButton.isEnabled = false
        microphoneButton.isEnabled = false
    }

    private fun lockedMessage(): String =
        "دروازه امنیتی فعال است\n\n۱) احراز هویت زیست‌سنجی قوی دستگاه\n۲) تأیید صدای اختصاصی مالک (هنوز پیاده‌سازی نشده)\n\nشبکه و عملیات حساس تا تکمیل و آزمایش هر دو مرحله قفل می‌مانند.\nHIC-STOP-01: توقف ایمن فعال"

    private fun activateEmergencyStop() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        emergencyStopActive = true
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
                    emergencyStopActive = true
                    status.text = "مرحله ۱ و آزمون کلید امن تأیید شد.\n\nتأیید صدای مالک هنوز پیاده‌سازی نشده است؛ شبکه، میکروفون و عملیات حساس قفل می‌مانند."
                    unlockButton.text = "مرحله ۱ تأیید شد — دسترسی حساس همچنان قفل است"
                    unlockButton.isEnabled = false
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

    override fun onStop() {
        super.onStop()
        pendingChallenge?.fill(0)
        pendingChallenge = null
        if (::unlockButton.isInitialized) {
            emergencyStopActive = true
            unlockButton.isEnabled = true
            unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
            applyLockedControls()
            if (::status.isInitialized) status.text = lockedMessage()
        }
    }
}
