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
    private enum class AuthenticationStage { NONE, BIOMETRIC, FACE, VOICE }
    private var voiceChallengePhrase: String? = null
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
    private val voiceAuthInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        speechLaunchPending = false
        if (authenticationStage != AuthenticationStage.VOICE) return@registerForActivityResult
        val expected = voiceChallengePhrase
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (result.resultCode == RESULT_OK && !expected.isNullOrBlank() &&
            normalizeVoiceChallenge(heard) == normalizeVoiceChallenge(expected)) {
            completeAuthentication()
        } else {
            failAuthentication("چالش گفتاری تطبیق نکرد؛ ورود لغو شد. این آزمون تطبیق هویت گوینده یا ضدبازپخش نیست.")
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
                startVoiceChallenge()
            } else if (!success) {
                failAuthentication(message)
            }
        }
        faceChallengeController?.start()
    }

    /**
     * Adds a randomized Persian spoken-response step after the face movement challenge.
     * This validates the spoken phrase only; it does NOT identify the speaker or prevent replay.
     */
    private fun startVoiceChallenge() {
        authenticationStage = AuthenticationStage.VOICE
        val phrases = listOf(
            "هیکتور قفل را باز کن",
            "من درخواست ورود دارم",
            "هیکتور آماده باش",
            "ورود را بررسی کن"
        )
        voiceChallengePhrase = phrases.random()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "مرحلهٔ سوم: این عبارت را با صدای بلند بگو: $voiceChallengePhrase")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLaunchPending = true
            status.text = "مرحلهٔ سوم: چالش گفتاری؛ عبارت نمایش‌داده‌شده را بخوان."
            voiceAuthInput.launch(intent)
        } catch (_: Exception) {
            speechLaunchPending = false
            failAuthentication("سرویس تشخیص گفتار در دسترس نیست؛ ورود لغو شد.")
        }
    }

    private fun normalizeVoiceChallenge(value: String): String =
        value.lowercase(Locale.forLanguageTag("fa-IR"))
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

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
        toolbar.addView(button("قفل") { lockSession() })
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
            if (writeSecureString("memory", memory.text.toString())) {
                Toast.makeText(this, "یادداشت روی گوشی ذخیره شد.", Toast.LENGTH_SHORT).show()
            }
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
        root.addView(button("توقف اضطراری HIC-STOP-01") { engageEmergencyStop() })
        setContentView(root)
    }

    private fun sendMessage() {
        if (!unlocked || !::input.isInitialized || !::sendButton.isInitialized) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (!addMessage("user", text)) return
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
            hint = "نشانی کامل API؛ مانند https://.../v1/chat/completions"
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
        val dialog = AlertDialog.Builder(this).setTitle("پیکربندی اتصال هوش مصنوعی")
            .setMessage("نشانی HTTPS، مدل و کلید معتبرِ سرویس سازگار با OpenAI لازم است. کلید در فضای خصوصی برنامه ذخیره می‌شود.")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setNeutralButton("آزمون اتصال", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val endpointValue = endpoint.text.toString().trim()
                val keyValue = key.text.toString().trim()
                val modelValue = model.text.toString().trim().ifEmpty { "gpt-4o-mini" }
                if (endpointValue.isBlank() || keyValue.isBlank()) {
                    Toast.makeText(this, "نشانی و کلید API را وارد کن.", Toast.LENGTH_LONG).show()
                } else if (writeSecureString("api_key", keyValue)) {
                    prefs.edit().putString("endpoint", endpointValue).putString("model", modelValue).apply()
                    Toast.makeText(this, "تنظیمات اتصال ذخیره شد.", Toast.LENGTH_SHORT).show()
                }
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                testAiConnection(endpoint.text.toString().trim(), key.text.toString().trim(),
                    model.text.toString().trim().ifEmpty { "gpt-4o-mini" })
            }
        }
        dialog.show()
    }

    /**
     * Sends a minimal, non-personal test prompt. It deliberately does not send local memory
     * or conversation history, so testing the API cannot disclose prior chat content.
     */
    private fun testAiConnection(endpoint: String, apiKey: String, model: String) {
        if (endpoint.isBlank() || apiKey.isBlank()) {
            Toast.makeText(this, "ابتدا نشانی سرویس و کلید API را وارد کن.", Toast.LENGTH_LONG).show()
            return
        }
        val generation = sessionGeneration
        Toast.makeText(this, "در حال آزمون اتصال…", Toast.LENGTH_SHORT).show()
        executor.execute {
            var connection: HttpURLConnection? = null
            val result = try {
                val normalized = endpoint.removeSuffix("/")
                val parsed = URL(normalized)
                if (parsed.protocol != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
                    "نشانی نامعتبر است؛ فقط HTTPS بدون اطلاعات ورود در نشانی پذیرفته می‌شود."
                } else if (!unlocked || generation != sessionGeneration) {
                    "نشست قفل شده است؛ آزمون اتصال لغو شد."
                } else {
                    connection = (parsed.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = "POST"
                        connectTimeout = 10000
                        readTimeout = 15000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    val payload = JSONObject()
                        .put("model", model)
                        .put("messages", JSONArray().put(
                            JSONObject().put("role", "user").put("content", "Reply with OK only.")
                        ))
                        .put("max_tokens", 8)
                    OutputStreamWriter(connection!!.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                    val code = connection!!.responseCode
                    if (code !in 200..299) {
                        "آزمون اتصال ناموفق بود (HTTP $code). نشانی، مدل و اعتبار کلید را بررسی کن."
                    } else {
                        val body = connection!!.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val answer = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").optString("content")
                        if (answer.isNotBlank()) "اتصال هوش مصنوعی موفق است؛ پاسخ آزمایشی دریافت شد."
                        else "سرویس پاسخ قابل‌خواندن نداد؛ قالب API را بررسی کن."
                    }
                }
            } catch (_: Exception) {
                "آزمون اتصال انجام نشد؛ اینترنت، نشانی HTTPS، مدل و کلید API را بررسی کن."
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                if (unlocked && generation == sessionGeneration && !isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this).setTitle("نتیجهٔ آزمون هوش مصنوعی")
                        .setMessage(result).setPositiveButton("باشه", null).show()
                }
            }
        }
    }

    private fun readSecureString(name: String): String {
        val bytes = secureStore.read(name) ?: return ""
        return try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
    }

    private fun writeSecureString(name: String, value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try {
            secureStore.write(name, bytes)
            true
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "ذخیرهٔ امن ناموفق بود؛ دادهٔ جدید ذخیره نشده است. وضعیت ذخیره‌سازی امن گوشی را بررسی کن.",
                Toast.LENGTH_LONG
            ).show()
            false
        } finally {
            bytes.fill(0)
        }
    }

    private fun addMessage(role: String, content: String): Boolean {
        val history = readHistory()
        history.put(JSONObject().put("role", role).put("content", content))
        while (history.length() > 100) history.remove(0)
        return writeSecureString("history", history.toString())
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

    /** Locks the current session without engaging the separate emergency-stop control. */
    private fun lockSession(updateUi: Boolean = true) {
        sessionGeneration++
        unlocked = false
        speechLaunchPending = false
        authenticationStage = AuthenticationStage.NONE
        voiceChallengePhrase = null
        explicitStopResumeApproved = false
        faceChallengeController?.close()
        faceChallengeController = null
        synchronized(networkLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
        if (::transcript.isInitialized) removeLastLoadingIfNeeded()
        textToSpeech?.stop()
        if (updateUi && !isFinishing && !isDestroyed) showLogin()
    }

    /** Engages the persistent HIC-STOP-01 control, then locks the session. */
    private fun engageEmergencyStop() {
        emergencyStopLatch = true
        val stopPersisted = prefs.edit().putBoolean("emergency_stop_engaged", true).commit()
        lockSession()
        if (!stopPersisted && ::status.isInitialized) {
            status.text = "هیکتور قفل شد، اما ذخیرهٔ توقف اضطراری ناموفق بود؛ برنامه را باز نگه دار و ذخیره را دوباره امتحان کن."
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
            // Backgrounding locks the session but does not engage the emergency stop.
            lockSession(updateUi = !isFinishing)
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
