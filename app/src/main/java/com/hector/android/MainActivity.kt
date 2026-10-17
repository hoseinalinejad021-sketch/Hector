package com.hector.android

import android.graphics.Color
import android.os.Bundle
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
 * Fail-closed login scaffold. Stage 1 uses device biometric authentication.
 * Stage 2 is reserved for camera-based owner-face matching; it deliberately
 * cannot pass until a real local face-embedding matcher is integrated and tested.
 */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var biometricButton: Button
    private lateinit var faceButton: Button
    private val securityPrefs by lazy {
        getSharedPreferences("hector_security_latch", MODE_PRIVATE)
    }
    private var pendingChallenge: ByteArray? = null
    private var biometricPassed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
        showLockedScreen()
    }

    private fun showLockedScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        root.addView(TextView(this).apply {
            text = "HECTOR"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            text = "ورود ساده و امن هیکتور\n۱) اثر انگشت از طریق زیست‌سنجی اندروید\n۲) تطبیق چهرهٔ مالک (هنوز آماده نیست)\n\nتا وقتی مرحلهٔ چهره واقعاً پیاده‌سازی و آزمایش نشود، ورود کامل و عملیات حساس قفل می‌مانند.\nHIC-STOP-01 فعال است."
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        biometricButton = Button(this).apply {
            text = "۱. تأیید اثر انگشت"
            setOnClickListener { authenticateBiometric() }
        }
        faceButton = Button(this).apply {
            text = "۲. تطبیق چهره (فعلاً قفل)"
            isEnabled = false
            setOnClickListener { status.text = "موتور تطبیق هویت چهره هنوز نصب نشده؛ دسترسی رد شد." }
        }
        val review = Button(this).apply {
            text = "وضعیت امنیت"
            setOnClickListener { showSecurityReviewScreen() }
        }
        val stop = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener { activateEmergencyStop() }
        }
        root.addView(status)
        root.addView(biometricButton)
        root.addView(faceButton)
        root.addView(review)
        root.addView(stop)
        setContentView(root)
    }

    private fun createAuthBoundCipher(): Cipher {
        val alias = "hector_owner_gate_key_v1"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        setInvalidatedByBiometricEnrollment(true)
                    }
                }.build()
            generator.init(spec)
            generator.generateKey()
        }
        val key = keyStore.getKey(alias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    private fun authenticateBiometric() {
        val available = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (available != BiometricManager.BIOMETRIC_SUCCESS) {
            activateEmergencyStop()
            status.text = "زیست‌سنجی قوی در دسترس نیست؛ قفل فعال است."
            return
        }
        val cipher = try { createAuthBoundCipher() } catch (_: Exception) {
            activateEmergencyStop()
            status.text = "کلید امن آماده نشد؛ قفل فعال است."
            return
        }
        pendingChallenge?.fill(0)
        pendingChallenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val proof = try {
                        val authorized = result.cryptoObject?.cipher ?: error("No authorized cipher")
                        authorized.doFinal(pendingChallenge ?: error("No challenge"))
                    } catch (_: Exception) { null }
                    finally {
                        pendingChallenge?.fill(0)
                        pendingChallenge = null
                    }
                    if (proof == null || proof.isEmpty()) {
                        activateEmergencyStop()
                        status.text = "اثبات امن انجام نشد؛ قفل فعال است."
                        return
                    }
                    proof.fill(0)
                    biometricPassed = true
                    securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
                    biometricButton.isEnabled = false
                    faceButton.isEnabled = false
                    status.text = "مرحله اول تأیید شد. مرحله دوم به تطبیق واقعی چهره نیاز دارد؛ این موتور هنوز پیاده‌سازی نشده، پس دسترسی و عملیات حساس قفل می‌مانند."
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    status.text = "زیست‌سنجی تطبیق نکرد؛ قفل فعال است."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activateEmergencyStop()
                    status.text = "احراز هویت لغو شد یا خطا داشت؛ قفل فعال است."
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("هیکتور — مرحله اول")
            .setSubtitle("زیست‌سنجی قوی دستگاه")
            .setDescription("نوع زیست‌سنجی را اندروید انتخاب می‌کند؛ این مرحله به‌تنهایی ورود مالک را تأیید نمی‌کند.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("لغو")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun activateEmergencyStop() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        biometricPassed = false
        securityPrefs.edit().putBoolean("emergency_stop_engaged", true).commit()
        if (::biometricButton.isInitialized) {
            biometricButton.isEnabled = true
            faceButton.isEnabled = false
        }
        if (::status.isInitialized) {
            status.text = "توقف اضطراری HIC-STOP-01 فعال شد. ورود مالک و عملیات حساس قفل هستند."
        }
    }

    private fun showSecurityReviewScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        root.addView(TextView(this).apply {
            text = "بازبینی امنیتی هیکتور\n\n• مرحله اول: زیست‌سنجی قوی دستگاه\n• مرحله دوم: تطبیق چهره واقعی هنوز پیاده‌سازی نشده\n• ورود مالک: قفل\n• عملیات حساس: قفل\n• میکروفون و تشخیص صدا: حذف از مسیر ورود\n• توقف اضطراری HIC-STOP-01: فعال\n\nتشخیص چهره توسط دوربین به‌تنهایی کافی نیست؛ برای ورود باید الگوی چهره محلی و موتور تطبیق هویت واقعی افزوده و آزمایش شود."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "بازگشت به قفل هیکتور"
            setOnClickListener { showLockedScreen() }
        })
        root.addView(Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener { activateEmergencyStop(); showLockedScreen() }
        })
        setContentView(root)
    }

    override fun onStop() {
        super.onStop()
        pendingChallenge?.fill(0)
        pendingChallenge = null
        if (::biometricButton.isInitialized) activateEmergencyStop()
    }
}
