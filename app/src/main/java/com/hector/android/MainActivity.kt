package com.hector.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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
    private val faceRequestCode = 4127
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
            text = "ورود هیکتور با دو مرحله طراحی شده است:\n۱) زیست‌سنجی قوی گوشی\n۲) آزمایش مستقل چهره با دوربین\n\nمرحله چهره می‌تواند وجود صورت را بررسی کند؛ این بررسی هنوز هویت مالک را اثبات نمی‌کند. بنابراین عملیات حساس تا افزودن مدل واقعی شناسایی هویت قفل می‌مانند.\nورود صوتی حذف شده است. HIC-STOP-01 فعال است."
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        biometricButton = Button(this).apply {
            text = "۱. تأیید زیست‌سنجی قوی (معمولاً اثر انگشت)"
            setOnClickListener { authenticateBiometric() }
        }
        faceButton = Button(this).apply {
            text = "۲. آزمایش دوربین و تشخیص چهره"
            setOnClickListener { startFaceCheck() }
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

    private fun startFaceCheck() {
        activateEmergencyStop()
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) == null) {
            status.text = "دوربین در دسترس نیست؛ قفل فعال است."
            return
        }
        status.text = "دوربین را باز کن و از چهره روبه‌رو عکس بگیر. این آزمایش فقط صورت را پیدا می‌کند و هویت مالک را تأیید نمی‌کند."
        @Suppress("DEPRECATION")
        startActivityForResult(intent, faceRequestCode)
    }

    @Deprecated("Camera result callback used for compatibility with the current project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != faceRequestCode) return
        if (resultCode != Activity.RESULT_OK) {
            activateEmergencyStop()
            status.text = "آزمایش دوربین لغو شد؛ قفل فعال است."
            return
        }
        @Suppress("DEPRECATION")
        val bitmap = data?.extras?.get("data") as? Bitmap
        if (bitmap == null) {
            activateEmergencyStop()
            status.text = "تصویر دوربین دریافت نشد؛ قفل فعال است."
            return
        }
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(options)
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                detector.close()
                bitmap.recycle()
                activateEmergencyStop()
                status.text = if (faces.size == 1) {
                    "آزمایش تشخیص صورت موفق شد: یک چهره پیدا شد. این مدل افراد را شناسایی نمی‌کند؛ هویت مالک تأیید نشده و عملیات حساس قفل است."
                } else {
                    "آزمایش ناموفق: ${faces.size} چهره پیدا شد؛ برای آزمایش یک چهره روبه‌رو لازم است. قفل فعال است."
                }
            }
            .addOnFailureListener {
                detector.close()
                bitmap.recycle()
                activateEmergencyStop()
                status.text = "پردازش تصویر شکست خورد؛ قفل فعال است."
            }
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
                    status.text = "زیست‌سنجی قوی گوشی تأیید شد. برای ساده‌سازی، از احراز هویت امن اندروید استفاده می‌شود؛ مرحله چهره جداگانه اجرا نمی‌شود. توقف اضطراری فعال است و عملیات حساس همچنان قفل‌اند."
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
            text = "بازبینی امنیتی هیکتور\n\n• روش‌های هدف ورود: اثر انگشت و تشخیص چهره\n• ورود صوتی: حذف‌شده از مسیر احراز هویت\n• مرحله اول: زیست‌سنجی قوی اندروید\n• مرحله دوم: تشخیص وجود صورت با ML Kit\n• تشخیص وجود صورت هویت شخص را اثبات نمی‌کند؛ تطبیق هویت مالک هنوز پیاده‌سازی نشده\n• توقف اضطراری HIC-STOP-01: فعال\n• عملیات حساس: قفل\n\nبرای جلوگیری از ورود اشتباه، تا وقتی مرحلهٔ مستقل چهره واقعی متصل و آزمایش نشود، دسترسی کامل باز نمی‌شود."
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
