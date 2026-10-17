package com.hector.android

import android.content.Context
import android.graphics.Color
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var loginButton: Button
    private var unlocked = false
    private var pendingChallenge: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        showLogin()
    }

    private fun createCipher(): Cipher {
        val alias = "hector_fingerprint_login_key"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setInvalidatedByBiometricEnrollment(true)
                    }
                }
                .build()
            generator.init(spec)
            generator.generateKey()
        }
        val key = store.getKey(alias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    @Suppress("DEPRECATION")
    private fun startFingerprintLogin() {
        val manager = getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            manager == null || !manager.isHardwareDetected || !manager.hasEnrolledFingerprints()
        ) {
            status.text = "برای ورود، اثر انگشت را در تنظیمات گوشی ثبت کن."
            return
        }

        val cipher = try {
            createCipher()
        } catch (_: Exception) {
            status.text = "ورود آماده نشد. دوباره تلاش کن."
            return
        }

        pendingChallenge?.fill(0)
        pendingChallenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        loginButton.isEnabled = false
        status.text = "حسگر اثر انگشت را لمس کن."

        manager.authenticate(cipher.let { FingerprintManager.CryptoObject(it) }, null, 0,
            object : FingerprintManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
                    val proof = try {
                        val authorized = result.cryptoObject?.cipher ?: throw IllegalStateException()
                        authorized.doFinal(pendingChallenge ?: throw IllegalStateException())
                    } catch (_: Exception) {
                        null
                    } finally {
                        pendingChallenge?.fill(0)
                        pendingChallenge = null
                    }
                    if (proof == null || proof.isEmpty()) {
                        proof?.fill(0)
                        status.text = "اثر انگشت تأیید نشد. دوباره تلاش کن."
                        loginButton.isEnabled = true
                        return
                    }
                    proof.fill(0)
                    unlocked = true
                    showHome()
                }

                override fun onAuthenticationFailed() {
                    status.text = "اثر انگشت تطبیق نکرد. دوباره امتحان کن."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    pendingChallenge?.fill(0)
                    pendingChallenge = null
                    status.text = "ورود لغو شد. برای تلاش دوباره دکمه را بزن."
                    loginButton.isEnabled = true
                }
            }, null
        )
    }

    private fun showLogin() {
        val root = baseLayout()
        val title = label("HECTOR", 30f)
        status = label("برای ورود، اثر انگشتت را بزن.", 17f)
        loginButton = Button(this).apply {
            text = "ورود با اثر انگشت"
            setOnClickListener { startFingerprintLogin() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(loginButton)
        setContentView(root)
    }

    private fun showHome() {
        val root = baseLayout()
        root.addView(label("HECTOR", 30f))
        root.addView(label("وارد شدی.", 20f))
        root.addView(Button(this).apply {
            text = "قفل کردن"
            setOnClickListener {
                unlocked = false
                pendingChallenge?.fill(0)
                pendingChallenge = null
                showLogin()
            }
        })
        setContentView(root)
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
        setBackgroundColor(Color.rgb(15, 23, 42))
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(0, 20, 0, 20)
    }

    override fun onStop() {
        super.onStop()
        pendingChallenge?.fill(0)
        pendingChallenge = null
        if (unlocked && !isChangingConfigurations) {
            unlocked = false
            if (!isFinishing) showLogin()
        }
    }

    override fun onDestroy() {
        pendingChallenge?.fill(0)
        pendingChallenge = null
        super.onDestroy()
    }
}
