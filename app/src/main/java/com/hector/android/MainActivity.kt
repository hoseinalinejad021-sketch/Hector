package com.hector.android

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Biometric-first security gate. Voice verification remains fail-closed until a real speaker-verification engine is integrated and tested. */
class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var unlockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            text = "دروازه امنیتی فعال است\n\n۱) احراز هویت زیست‌سنجی دستگاه\n۲) تأیید صدای مالک\n\nشبکه هیکتور تا تکمیل هر دو مرحله قفل می‌ماند.\nHIC-STOP-01: توقف ایمن فعال"
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        unlockButton = Button(this).apply {
            text = "مرحله ۱: تأیید اثر انگشت"
            setOnClickListener { authenticateBiometric() }
        }
        val stopButton = Button(this).apply {
            text = "HIC-STOP-01 — توقف اضطراری"
            setOnClickListener {
                unlockButton.isEnabled = true
                unlockButton.text = "مرحله ۱: تأیید اثر انگشت"
                status.text = "توقف اضطراری فعال شد.\nشبکه و عملیات حساس قفل هستند."
            }
        }
        root.addView(title)
        root.addView(status)
        root.addView(unlockButton)
        root.addView(stopButton)
        setContentView(root)
    }

    private fun authenticateBiometric() {
        val availability = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            status.text = "زیست‌سنجی قوی آماده نیست. قفل هیکتور باز نمی‌شود. در تنظیمات گوشی اثر انگشت را ثبت کن."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                status.text = "مرحله ۱ موفق شد.\n\nمرحله ۲: تأیید صدای مالک لازم است.\nتأییدگر واقعی صدای اختصاصی و تشخیص بازپخش هنوز در این نسخه وجود ندارد؛ بنابراین شبکه باز نمی‌شود و توقف ایمن باقی می‌ماند."
                unlockButton.text = "مرحله ۲: صدای مالک — هنوز قفل است"
                unlockButton.isEnabled = false
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                status.text = "تأیید زیست‌سنجی ناموفق بود. شبکه هیکتور قفل می‌ماند."
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                status.text = "احراز هویت لغو شد یا خطا داشت. شبکه هیکتور قفل می‌ماند."
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن هیکتور")
            .setSubtitle("مرحله اول: احراز هویت زیست‌سنجی قوی دستگاه")
            .setDescription("پس از این مرحله، تأیید صدای مالک نیز الزامی است.")
            .setNegativeButtonText("لغو")
            .build()
        prompt.authenticate(info)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the foreground always returns Hector to the locked state.
        unlockButton?.isEnabled = true
    }
}
