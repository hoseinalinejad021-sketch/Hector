package com.hector.android

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import android.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.camera.view.PreviewView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val prefs by lazy { getSharedPreferences("hector_private", MODE_PRIVATE) }
    private val secureStore by lazy { SecureMemoryStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var unlocked = false
    // In-process fail-closed latch: a failed stop-state disk write must not permit re-login.
    @Volatile private var emergencyStopLatch = false
    private var speechLaunchPending = false
    private enum class AuthenticationStage { NONE, BIOMETRIC, FACE }
    private var authenticationStage = AuthenticationStage.NONE
    private var explicitStopResumeApproved = false
    private var faceChallengeController: FaceLivenessChallengeController? = null
    @Volatile private var sessionGeneration = 0L
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val networkLock = Any()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val speechInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        speechLaunchPending = false
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        if (unlocked && result.resultCode == RESULT_OK) {
            val spoken = results.firstOrNull().orEmpty()
            if (spoken.isNotBlank() && ::input.isInitialized) {
                input.setText(spoken)
                input.setSelection(input.text.length)
                sendMessage()
            } else {
                Toast.makeText(this, "گفتار واضح دریافت نشد؛ دوباره امتحان کن.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (authenticationStage == AuthenticationStage.FACE) {
                if (granted) showFaceChallenge()
                else failAuthentication("مجوز دوربین داده نشد؛ ورود لغو شد.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        emergencyStopLatch = prefs.getBoolean("emergency_stop_engaged", false)
        textToSpeech = TextToSpeech(applicationContext) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                val persian = textToSpeech?.setLanguage(Locale.forLanguageTag("fa-IR"))
                if (persian == TextToSpeech.LANG_MISSING_DATA || persian == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = false
                    Toast.makeText(this, "صدای فارسی نصب یا پشتیبانی نمی‌شود؛ دادهٔ گفتار فارسی را در تنظیمات گوشی نصب کن.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "موتور تبدیل متن به گفتار در گوشی در دسترس نیست.", Toast.LENGTH_LONG).show()
            }
        }
        showLogin()
    }

    private fun showLogin() {
        val page = page()
        page.addView(label("HECTOR", 32f))
        page.addView(label("دستیار شخصی تو", 20f))
        status = label(
            if (isEmergencyStopEngaged())
                "توقف اضطراری HIC-STOP-01 فعال است. برای ادامه، احراز هویت کن."
            else "برای ورود از احراز هویت ثبت‌شده روی گوشی استفاده کن.",
            16f
        )
        page.addView(status)
        val login = button("ورود با زیست‌سنجی گوشی") { authenticate() }
        page.addView(login)
        page.addView(label("هیکتور پس از ورود، گفت‌وگو و یادداشت‌های محلی را نگه می‌دارد.", 13f))
        setContentView(page)
    }

    private fun authenticate() {
        if (isEmergencyStopEngaged()) {
            AlertDialog.Builder(this)
                .setTitle("توقف اضطراری فعال است")
                .setMessage("توقف HIC-STOP-01 فقط با تأیید صریح تو و سپس تکمیل احراز هویت برداشته می‌شود.")
                .setNegativeButton("لغو", null)
                .setPositiveButton("تأیید و احراز هویت") { _, _ ->
                    explicitStopResumeApproved = true
                    startBiometricAuthentication()
                }
                .show()
            return
        }
        explicitStopResumeApproved = false
        startBiometricAuthentication()
    }

    private fun startBiometricAuthentication() {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            status.text = "زیست‌سنجی قوی گوشی آماده نیست. اثر انگشت یا حسگر زیست‌سنجی قویِ پشتیبانی‌شده را در تنظیمات بررسی کن."
            return
        }
        authenticationStage = AuthenticationStage.BIOMETRIC
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticationStage = AuthenticationStage.FACE
                    startFaceChallenge()
                }
                override fun onAuthenticationFailed() {
                    status.text = "تطبیق زیست‌سنجی انجام نشد؛ دوباره تلاش کن."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticationStage = AuthenticationStage.NONE
                    explicitStopResumeApproved = false
                    status.text = "ورود لغو شد. هیکتور قفل است."
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("مرحلهٔ اول: تأیید مالک")
            .setSubtitle("زیست‌سنجی قوی ثبت‌شده در اندروید؛ نوع حسگر را خود سیستم انتخاب می‌کند")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("لغو")
            .build()
        prompt.authenticate(info)
    }

    private fun startFaceChallenge() {
        authenticationStage = AuthenticationStage.FACE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            showFaceChallenge()
        }
    }

    private fun showFaceChallenge() {
        if (authenticationStage != AuthenticationStage.FACE || isFinishing || isDestroyed) return
        val page = page()
        page.addView(label("مرحلهٔ دوم: بررسی زندهٔ چهره", 22f))
        page.addView(label("این مرحله حضور چهره و حرکت تازه را بررسی می‌کند؛ تطبیق اختصاصی چهره با تصویر ذخیره‌شده هنوز فعال نیست.", 13f))
        val preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        page.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val faceStatus = label("آماده‌سازی دوربین جلو…", 15f)
        page.addView(faceStatus)
        page.addView(button("لغو ورود") { failAuthentication("ورود توسط کاربر لغو شد.") })
        setContentView(page)

        faceChallengeController?.close()
        faceChallengeController = FaceLivenessChallengeController(this, preview, faceStatus) { success, message ->
            faceChallengeController = null
            if (success && authenticationStage == AuthenticationStage.FACE) {
                completeAuthentication()
            } else if (!success) {
                failAuthentication(message)
            }
        }
        faceChallengeController?.start()
    }

    private fun completeAuthentication() {
        if (authenticationStage != AuthenticationStage.FACE) return
        val stopIsEngaged = isEmergencyStopEngaged()
        if (stopIsEngaged && !explicitStopResumeApproved) {
            failAuthentication("توقف اضطراری فعال است؛ بدون تأیید صریح، ورود ممنوع است.")
            return
        }
        authenticationStage = AuthenticationStage.NONE
        unlocked = true
        if (stopIsEngaged) {
            val stopCleared = prefs.edit().putBoolean("emergency_stop_engaged", false).commit()
            if (!stopCleared) {
                // A failed disk write must never silently clear the emergency-stop latch.
                emergencyStopLatch = true
                prefs.edit().putBoolean("emergency_stop_engaged", true).commit()
                failAuthentication("توقف اضطراری هنوز فعال است؛ ذخیرهٔ رفع توقف ناموفق بود. هیکتور قفل می‌ماند.")
                return
            }
        }
        emergencyStopLatch = false
        explicitStopResumeApproved = false
        showHome()
        Toast.makeText(this, "ورود پایه انجام شد؛ بررسی دوربین تطبیق هویت چهره نیست. عملیات حساس همچنان قفل می‌مانند.", Toast.LENGTH_LONG).show()
    }

    private fun failAuthentication(message: String) {
        explicitStopResumeApproved = false
        faceChallengeController?.close()
        faceChallengeController = null
        authenticationStage = AuthenticationStage.NONE
        speechLaunchPending = false
        unlocked = false
        showLogin()
        status.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showHome() {
        root = page()
        root.addView(label("HECTOR", 28f))
        root.addView(label("دستیار شخصی | گفت‌وگو و حافظه محلی", 14f))
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        toolbar.addView(button("تنظیم اتصال هوش مصنوعی") { showAiSettings() })
        toolbar.addView(button("قفل") { lockNow() })
        root.addView(toolbar)

        root.addView(label("یادداشت حافظه هیکتور (روی همین گوشی)", 16f))
        val memory = EditText(this).apply {
            setText(readSecureString("memory"))
            hint = "اطلاعاتی که می‌خواهی محلی بماند..."
            minLines = 2
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 41, 59))
            setPadding(12, 8, 12, 8)
        }
        root.addView(memory, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(button("ذخیره یادداشت") {
            writeSecureString("memory", memory.text.toString())
            Toast.makeText(this, "یادداشت روی گوشی ذخیره شد.", Toast.LENGTH_SHORT).show()
        })

        transcript = label("", 15f).apply {
            gravity = Gravity.START
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(30, 41, 59))
        }
        val scroll = ScrollView(this)
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderHistory()

        input = EditText(this).apply {
            hint = "پیامت را بنویس..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 41, 59))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        sendButton = button("ارسال پیام") { sendMessage() }
        root.addView(sendButton)
        root.addView(button("گوش مصنوعی: صحبت کن") { startSpeechInput() })
        root.addView(button("توقف اضطراری HIC-STOP-01") { lockNow() })
        setContentView(root)
    }

    private fun sendMessage() {
        if (!unlocked || !::input.isInitialized || !::sendButton.isInitialized) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        addMessage("user", text)
        input.setText("")
        renderHistory()
        val endpoint = prefs.getString("endpoint", "")?.trim().orEmpty()
        val apiKey = readSecureString("api_key").trim()
        if (endpoint.isEmpty() || apiKey.isEmpty()) {
            val notice = "پیامت ذخیره شد. برای پاسخ هوش مصنوعی، از «تنظیم اتصال هوش مصنوعی» یک نشانی HTTPS سازگار با OpenAI API و کلید API معتبر وارد کن. اتصال هنوز پیکربندی نشده است."
            addMessage("assistant", notice)
            renderHistory()
            speakAnswer(notice)
            return
        }
        sendButton.isEnabled = false
        val requestGeneration = sessionGeneration
        addMessage("assistant", "در حال دریافت پاسخ…")
        renderHistory()
        executor.execute {
            val answer = requestAi(endpoint, apiKey, requestGeneration)
            runOnUiThread {
                if (!unlocked || requestGeneration != sessionGeneration || isFinishing || isDestroyed) return@runOnUiThread
                removeLastLoadingIfNeeded()
                addMessage("assistant", answer)
                renderHistory()
                speakAnswer(answer)
                sendButton.isEnabled = true
            }
        }
    }

    private fun startSpeechInput() {
        if (!unlocked) {
            Toast.makeText(this, "ابتدا وارد هیکتور شو.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "با هیکتور صحبت کن")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLaunchPending = true
            speechInput.launch(intent)
        } catch (_: Exception) {
            speechLaunchPending = false
            Toast.makeText(this, "سرویس تشخیص گفتار روی گوشی در دسترس نیست.", Toast.LENGTH_LONG).show()
        }
    }

    private fun speakAnswer(answer: String) {
        if (!ttsReady || answer.isBlank()) return
        textToSpeech?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "hector-answer")
    }

    private fun requestAi(endpoint: String, apiKey: String, requestGeneration: Long): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = if (endpoint.endsWith("/")) endpoint.dropLast(1) else endpoint
            if (!url.startsWith("https://", ignoreCase = true)) {
                return "برای محافظت از کلید API، نشانی سرویس باید با HTTPS شروع شود."
            }
            if (!unlocked || requestGeneration != sessionGeneration) return "درخواست به‌دلیل قفل‌شدن هیکتور لغو شد."
            val parsedUrl = URL(url)
            if (parsedUrl.protocol != "https" || parsedUrl.host.isNullOrBlank() || parsedUrl.userInfo != null) {
                return "نشانی سرویس معتبر نیست؛ فقط HTTPS با میزبان مشخص و بدون اطلاعات ورود در نشانی پذیرفته می‌شود."
            }
            connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            synchronized(networkLock) {
                if (!unlocked || requestGeneration != sessionGeneration) {
                    connection.disconnect()
                    return "درخواست به‌دلیل قفل‌شدن هیکتور لغو شد."
                }
                activeConnection = connection
            }
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content",
                "You are Hector, a helpful personal assistant. User's local memory: " + readSecureString("memory")))
            val history = readHistory()
            for (i in 0 until history.length()) {
                val item = history.getJSONObject(i)
                val role = item.optString("role")
                val content = item.optString("content")
                if ((role == "user" || role == "assistant") && content != "در حال دریافت پاسخ…") {
                    messages.put(JSONObject().put("role", role).put("content", content))
                }
            }
            val payload = JSONObject().put("model", prefs.getString("model", "gpt-4o-mini"))
                .put("messages", messages)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) return "اتصال هوش مصنوعی خطا داد (HTTP $code). تنظیمات اتصال یا کلید API را بررسی کن."
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "اتصال برقرار نشد: ${e.message ?: "خطای نامشخص"}. نشانی سرویس، اینترنت و کلید API را بررسی کن."
        } finally {
            connection?.disconnect()
            synchronized(networkLock) {
                if (activeConnection === connection) activeConnection = null
            }
        }
    }

    private fun showAiSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val endpoint = EditText(this).apply {
            hint = "نشانی کامل API؛ مانند .../v1/chat/completions"
            setText(prefs.getString("endpoint", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val key = EditText(this).apply {
            hint = "کلید API"
            setText(readSecureString("api_key"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply {
            hint = "نام مدل"
            setText(prefs.getString("model", "gpt-4o-mini"))
        }
        box.addView(endpoint); box.addView(key); box.addView(model)
        AlertDialog.Builder(this).setTitle("پیکربندی اتصال هوش مصنوعی")
            .setMessage("برای اتصال واقعی، نشانی سرویس سازگار با OpenAI و کلید معتبر لازم است. کلید در فضای خصوصی برنامه ذخیره می‌شود.")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().putString("endpoint", endpoint.text.toString().trim()).putString("model", model.text.toString().trim().ifEmpty { "gpt-4o-mini" }).apply()
                writeSecureString("api_key", key.text.toString().trim())
                Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun readSecureString(name: String): String {
        val bytes = secureStore.read(name) ?: return ""
        return try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
    }

    private fun writeSecureString(name: String, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try { secureStore.write(name, bytes) } finally { bytes.fill(0) }
    }

    private fun addMessage(role: String, content: String) {
        val history = readHistory()
        history.put(JSONObject().put("role", role).put("content", content))
        while (history.length() > 100) history.remove(0)
        writeSecureString("history", history.toString())
    }

    private fun readHistory(): JSONArray = try {
        JSONArray(readSecureString("history").ifEmpty { "[]" })
    } catch (_: Exception) { JSONArray() }

    private fun removeLastLoadingIfNeeded() {
        val history = readHistory()
        if (history.length() > 0 && history.getJSONObject(history.length() - 1).optString("content") == "در حال دریافت پاسخ…") {
            history.remove(history.length() - 1)
            writeSecureString("history", history.toString())
        }
    }

    private fun renderHistory() {
        val history = readHistory()
        val builder = StringBuilder()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            builder.append(if (item.optString("role") == "user") "تو: " else "هیکتور: ")
                .append(item.optString("content")).append("\n\n")
        }
        transcript.text = builder.toString()
    }

    private fun isEmergencyStopEngaged(): Boolean =
        emergencyStopLatch || prefs.getBoolean("emergency_stop_engaged", false)

    private fun lockNow() {
        sessionGeneration++
        unlocked = false
        speechLaunchPending = false
        authenticationStage = AuthenticationStage.NONE
        faceChallengeController?.close()
        faceChallengeController = null
        emergencyStopLatch = true
        val stopPersisted = prefs.edit().putBoolean("emergency_stop_engaged", true).commit()
        synchronized(networkLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
        if (::transcript.isInitialized) removeLastLoadingIfNeeded()
        textToSpeech?.stop()
        showLogin()
        if (!stopPersisted && ::status.isInitialized) {
            status.text = "هیکتور قفل شد، اما ذخیرهٔ توقف اضطراری در حافظه ناموفق بود؛ برنامه را باز نگه دار و دوباره توقف را فعال کن."
        }
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(24, 18, 24, 18)
        setBackgroundColor(Color.rgb(15, 23, 42))
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; setPadding(0, 10, 0, 10)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setOnClickListener { action() }
    }

    override fun onStop() {
        super.onStop()
        if (unlocked && !isChangingConfigurations && !speechLaunchPending) {
            // Backgrounding invalidates the session and actively cancels any in-flight request.
            sessionGeneration++
            unlocked = false
            synchronized(networkLock) {
                activeConnection?.disconnect()
                activeConnection = null
            }
            textToSpeech?.stop()
            if (::transcript.isInitialized) removeLastLoadingIfNeeded()
            if (!isFinishing) showLogin()
        }
    }

    override fun onDestroy() {
        faceChallengeController?.close()
        faceChallengeController = null
        sessionGeneration++
        synchronized(networkLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
        executor.shutdownNow()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}
