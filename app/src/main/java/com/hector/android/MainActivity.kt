package com.hector.android

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fail-closed first-stage gate.
 * Android performs biometric matching; this app never receives biometric templates.
 * Owner-specific voice verification is not implemented, so protected features stay locked.
 */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var unlockButton: Button
    private var biometricPassed = false
    private var emergencyStopActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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

        val stopButton = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener { activateEmergencyStop() }
        }

        root.addView(title)
        root.addView(status)
        root.addView(unlockButton)
        root.addView(stopButton)
        setContentView(root)
    }

    private fun lockedMessage(): String =
        "دروازه امنیتی فعال است\n\n۱) احراز هویت زیست‌سنجی قوی دستگاه\n۲) تأیید صدای اختصاصی مالک\n\nشبکه و عملیات حساس تا تکمیل هر دو مرحله قفل می‌مانند.\nHIC-STOP-01: توقف ایمن فعال"

    private fun activateEmergencyStop() {
        biometricPassed = false
        emergencyStopActive = true
        unlockButton.isEnabled = true
        unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
        status.text = "توقف اضطراری فعال شد. شبکه و عملیات حساس قفل هستند."
    }

    private fun authenticateBiometric() {
        if (emergencyStopActive) {
            // A deliberate tap starts a fresh authentication attempt; stop remains effective on failure.
            emergencyStopActive = false
        }

        val availability = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPassed = false
            emergencyStopActive = true
            status.text = "زیست‌سنجی قوی آماده نیست. قفل هیکتور باز نمی‌شود. در تنظیمات گوشی زیست‌سنجی را ثبت کن."
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricPassed = true
                    emergencyStopActive = true // Voice gate is not yet implemented; fail closed.
                    status.text = "مرحله ۱ تأیید شد.\n\nمرحله ۲: تأیید صدای اختصاصی مالک لازم است.\nتأییدگر واقعی صدا و تشخیص بازپخش هنوز پیاده‌سازی و آزمایش نشده است؛ شبکه باز نمی‌شود."
                    unlockButton.text = "مرحله ۲: تأیید صدای مالک — قفل است"
                    unlockButton.isEnabled = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricPassed = false
                    emergencyStopActive = true
                    status.text = "تأیید زیست‌سنجی ناموفق بود. شبکه هیکتور قفل می‌ماند."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricPassed = false
                    emergencyStopActive = true
                    status.text = "احراز هویت لغو شد یا خطا داشت. شبکه هیکتور قفل می‌ماند."
                    unlockButton.isEnabled = true
                    unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن هیکتور")
            .setSubtitle("مرحله اول: احراز هویت زیست‌سنجی قوی دستگاه")
            .setDescription("پس از این مرحله، تأیید صدای اختصاصی مالک نیز الزامی است.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("لغو")
            .build()

        prompt.authenticate(info)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the foreground always clears the temporary first-stage result.
        if (::unlockButton.isInitialized) {
            biometricPassed = false
            emergencyStopActive = true
            unlockButton.isEnabled = true
            unlockButton.text = "مرحله ۱: تأیید زیست‌سنجی"
            if (::status.isInitialized) status.text = lockedMessage()
        }
    }
}
