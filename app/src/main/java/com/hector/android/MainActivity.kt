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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hector_private", MODE_PRIVATE) }
    private val secureStore by lazy { SecureMemoryStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var unlocked = false
    private var speechLaunchPending = false
    private var liveVoiceSession: RealtimeVoiceSession? = null
    private var liveStatus: TextView? = null
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
    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && unlocked) showLiveVoiceScreen()
        else Toast.makeText(this, "مجوز میکروفون داده نشد؛ GPT-Live شروع نشد.", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        textToSpeech = TextToSpeech(applicationContext) { result ->
            if (result != TextToSpeech.SUCCESS) {
                ttsReady = false
                runOnUiThread {
                    Toast.makeText(this, "موتور تبدیل متن به گفتار در گوشی آماده نیست. تنظیمات خروجی گفتار را بررسی کن.", Toast.LENGTH_LONG).show()
                }
                return@TextToSpeech
            }
            val persian = Locale.forLanguageTag("fa-IR")
            val languageStatus = textToSpeech?.isLanguageAvailable(persian) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = false
            } else {
                val setStatus = textToSpeech?.setLanguage(persian) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = setStatus != TextToSpeech.LANG_MISSING_DATA &&
                    setStatus != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        // Preserve the emergency-stop state across restarts, but always let the user open the app.
        unlocked = true
        showHome()
    }

    private fun showPersianTtsSetup(languageStatus: Int) {
        val detail = if (languageStatus == TextToSpeech.LANG_MISSING_DATA) {
            "موتور گفتار پیدا شد، اما دادهٔ صدای فارسی نصب نیست."
        } else {
            "موتور گفتار فعلی از فارسی پشتیبانی نمی‌کند یا صدای فارسی در دسترس نیست."
        }
        AlertDialog.Builder(this)
            .setTitle("تنظیم صدای فارسی هیکتور")
            .setMessage("$detail\\n\\nمی‌توانی صفحهٔ نصب داده‌های گفتار را باز کنی. اگر موتور گوشی صدای فارسی ارائه نکند، باید در تنظیمات خروجی متن‌به‌گفتار، موتور یا زبان دیگری انتخاب شود.")
            .setPositiveButton("بررسی / نصب داده‌ها") { _, _ ->
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
                    } catch (_: Exception) {
                        Toast.makeText(this, "صفحهٔ نصب دادهٔ گفتار در این گوشی در دسترس نیست؛ تنظیمات اندروید را باز کن.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("بعداً", null)
            .show()
    }

    // Legacy stop-screen entry point intentionally removed: emergency stop must
    // not lock the whole app or block access to the assistant.

    private fun showHome() {
        root = page()
        root.addView(label("HECTOR", 28f))
        root.addView(label("دستیار شخصی | گفت‌وگو و حافظه محلی", 14f))
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        toolbar.addView(button("تنظیم اتصال هوش مصنوعی") { showAiSettings() })
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
        root.addView(button("گفت‌وگوی زنده GPT-Live") { startLiveVoice() })
        val stopEngaged = prefs.getBoolean("emergency_stop_engaged", false)
        if (stopEngaged) {
            root.addView(label("توقف اضطراری فعال است؛ درخواست‌های هوش مصنوعی و صدا متوقف‌اند.", 14f))
        }
        root.addView(button(if (stopEngaged) "لغو توقف اضطراری HIC-STOP-01" else "توقف اضطراری HIC-STOP-01") {
            if (stopEngaged) {
                AlertDialog.Builder(this)
                    .setTitle("لغو توقف اضطراری؟")
                    .setMessage("با لغو توقف، درخواست‌های هوش مصنوعی و صدای هیکتور دوباره فعال می‌شوند.")
                    .setNegativeButton("ادامهٔ توقف", null)
                    .setPositiveButton("تأیید لغو توقف") { _, _ -> lockNow(activateEmergencyStop = true) }
                    .show()
            } else {
                lockNow(activateEmergencyStop = true)
            }
        })
        setContentView(root)
    }

    private fun sendMessage() {
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            Toast.makeText(this, "توقف اضطراری فعال است؛ ابتدا آن را به‌صورت دستی لغو کن.", Toast.LENGTH_LONG).show()
            return
        }
        if (!unlocked || !::input.isInitialized || !::sendButton.isInitialized) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        addMessage("user", text)
        input.setText("")
        renderHistory()
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1/responses")?.trim().orEmpty()
        val apiKey = readSecureString("api_key").trim()
        if (endpoint.isEmpty() || apiKey.isEmpty()) {
            val notice = "پیامت ذخیره شد. برای پاسخ هوش مصنوعی، از «تنظیم اتصال هوش مصنوعی» نشانی https://api.openai.com/v1/responses و کلید API معتبر را در «تنظیم اتصال هوش مصنوعی» وارد کن. اتصال هنوز پیکربندی نشده است."
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

    private fun startLiveVoice() {
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            Toast.makeText(this, "توقف اضطراری فعال است؛ گفت‌وگوی زنده شروع نشد.", Toast.LENGTH_LONG).show()
            return
        }
        if (!unlocked) return
        if (readSecureString("api_key").isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("اتصال GPT-Live")
                .setMessage("برای شروع گفت‌وگوی زنده فقط کلید API معتبر OpenAI را در تنظیمات ذخیره کن. کلید داخل APK ثابت نشده است.")
                .setNegativeButton("لغو", null)
                .setPositiveButton("تنظیم کلید") { _, _ -> showAiSettings() }
                .show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        showLiveVoiceScreen()
    }

    private fun showLiveVoiceScreen() {
        if (!unlocked || isFinishing || isDestroyed) return
        liveVoiceSession?.close()
        liveVoiceSession = null
        val page = page()
        page.addView(label("GPT-Live | گفت‌وگوی زنده", 24f))
        page.addView(label("صدای دوطرفهٔ زنده با مدل Realtime؛ کلید از حافظهٔ رمزگذاری‌شدهٔ هیکتور خوانده می‌شود.", 14f))
        liveStatus = label("در حال آماده‌سازی میکروفون و اتصال…", 15f)
        page.addView(liveStatus)
        page.addView(button("پایان گفت‌وگوی زنده") {
            liveVoiceSession?.close()
            liveVoiceSession = null
            liveStatus = null
            if (unlocked) showHome()
        })
        setContentView(page)
        val session = RealtimeVoiceSession(this, readSecureString("api_key")) { message ->
            runOnUiThread {
                if (unlocked && liveStatus != null && !isFinishing && !isDestroyed) liveStatus?.text = message
            }
        }
        liveVoiceSession = session
        session.start()
    }

    private fun startSpeechInput() {
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            Toast.makeText(this, "توقف اضطراری فعال است؛ ورودی گفتار شروع نشد.", Toast.LENGTH_LONG).show()
            return
        }
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
        if (answer.isBlank()) return
        if (ttsReady) {
            textToSpeech?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "hector-answer")
        } else {
            Toast.makeText(this, "صدای فارسی هنوز آماده نیست؛ کمی صبر کن یا تنظیمات گفتار را بررسی کن.", Toast.LENGTH_SHORT).show()
        }
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
            val modelName = prefs.getString("model", "gpt-5.5").orEmpty().ifBlank { "gpt-5.5" }
            val isLegacyChatCompletions = url.endsWith("/chat/completions", ignoreCase = true)
            val payload = if (isLegacyChatCompletions) {
                // Backward compatibility: legacy-compatible endpoints do not get hosted web search.
                JSONObject().put("model", modelName).put("messages", messages)
            } else {
                // Responses API with hosted web search for current information.
                JSONObject()
                    .put("model", modelName)
                    .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                    .put("input", messages)
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val errorObject = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
                val detail = errorObject?.optString("message").orEmpty()
                val errorType = errorObject?.optString("type").orEmpty()
                val errorCode = errorObject?.optString("code").orEmpty()
                val diagnosis = when (code) {
                    401 -> "احراز هویت ناموفق است (401). کلید API را در تنظیمات هیکتور دوباره وارد کن؛ کلید باید از همین ارائه‌دهنده باشد و منقضی یا لغو نشده باشد. کلید را برای کسی نفرست."
                    403 -> "دسترسی رد شد (403). مجوز پروژه، دسترسی مدل و محدودیت‌های حساب ارائه‌دهنده را بررسی کن."
                    404 -> "مسیر API یا نام مدل پیدا نشد (404). برای OpenAI از https://api.openai.com/v1/responses استفاده کن و نام مدل مجاز برای حساب خودت را وارد کن."
                    429 -> "محدودیت درخواست یا اعتبار/سهمیه حساب (429). وضعیت مصرف و پرداخت سرویس را بررسی کن."
                    in 500..599 -> "سرویس هوش مصنوعی موقتاً خطای سرور داده است؛ کمی بعد دوباره امتحان کن."
                    else -> "درخواست API رد شد (HTTP $code). نشانی، مدل و مجوز حساب را بررسی کن."
                }
                val metadata = listOf(errorType, errorCode).filter { it.isNotBlank() && it != "null" }.distinct().joinToString(" / ")
                return buildString {
                    append(diagnosis)
                    if (metadata.isNotBlank()) append("\\nکد خطا: ").append(metadata.take(120))
                    if (detail.isNotBlank()) append("\\nجزئیات سرویس: ").append(detail.take(300))
                }
            }
            val response = JSONObject(body)
            if (isLegacyChatCompletions) {
                response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                val output = response.optJSONArray("output") ?: JSONArray()
                val answer = StringBuilder()
                for (i in 0 until output.length()) {
                    val item = output.optJSONObject(i) ?: continue
                    if (item.optString("type") != "message") continue
                    val content = item.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") {
                            answer.append(part.optString("text"))
                        }
                    }
                }
                answer.toString().ifBlank { "مدل پاسخ متنی برنگرداند؛ تنظیمات مدل و API را بررسی کن." }
            }
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
            hint = "نشانی Responses API؛ https://api.openai.com/v1/responses"
            setText(prefs.getString("endpoint", "https://api.openai.com/v1/responses"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val key = EditText(this).apply {
            hint = "کلید API"
            setText(readSecureString("api_key"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply {
            hint = "نام مدل"
            setText(prefs.getString("model", "gpt-5.5"))
        }
        box.addView(endpoint); box.addView(key); box.addView(model)
        AlertDialog.Builder(this).setTitle("پیکربندی اتصال هوش مصنوعی")
            .setMessage("برای هوش مصنوعی آنلاین و جست‌وجوی وب، نشانی https://api.openai.com/v1/responses و کلید معتبر OpenAI لازم است. نشانی /chat/completions فقط حالت قدیمی بدون جست‌وجوی وب را فعال می‌کند. کلید در فضای خصوصی برنامه ذخیره می‌شود.")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().putString("endpoint", endpoint.text.toString().trim()).putString("model", model.text.toString().trim().ifEmpty { "gpt-5.5" }).apply()
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

    private fun lockNow(activateEmergencyStop: Boolean) {
        // Always cancel active work. Persist the stop state; the same visible button
        // is the user's explicit manual control to engage or disengage it.
        sessionGeneration++
        unlocked = true
        speechLaunchPending = false
        liveVoiceSession?.close()
        liveVoiceSession = null
        liveStatus = null
        val wasStopped = prefs.getBoolean("emergency_stop_engaged", false)
        val stopNow = activateEmergencyStop && !wasStopped
        val stopStatePersisted = prefs.edit().putBoolean("emergency_stop_engaged", stopNow).commit()
        if (!stopStatePersisted) {
            if (wasStopped) prefs.edit().putBoolean("emergency_stop_engaged", true).commit()
            Toast.makeText(this, "هشدار: ذخیرهٔ پایدار وضعیت توقف ناموفق بود؛ فضای ذخیره‌سازی گوشی را بررسی کن.", Toast.LENGTH_LONG).show()
        }
        synchronized(networkLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
        if (::transcript.isInitialized) removeLastLoadingIfNeeded()
        textToSpeech?.stop()
        if (activateEmergencyStop) {
            val message = if (stopNow) {
                "توقف اضطراری فعال شد؛ درخواست‌های هوش مصنوعی و صدا متوقف‌اند. هیکتور باز می‌ماند."
            } else {
                "توقف اضطراری با انتخاب دستی لغو شد؛ هیکتور آمادهٔ استفاده است."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            showHome()
        }
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        textDirection = android.view.View.TEXT_DIRECTION_RTL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(24, 18, 24, 18)
        setBackgroundColor(Color.rgb(15, 23, 42))
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        textDirection = android.view.View.TEXT_DIRECTION_RTL
        gravity = Gravity.CENTER; setPadding(0, 10, 0, 10)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textDirection = android.view.View.TEXT_DIRECTION_RTL
        setOnClickListener { action() }
    }

    override fun onStop() {
        super.onStop()
        if (unlocked && !isChangingConfigurations && !speechLaunchPending) {
            // Backgrounding invalidates the session and actively cancels any in-flight request.
            sessionGeneration++
            synchronized(networkLock) {
                activeConnection?.disconnect()
                activeConnection = null
            }
            textToSpeech?.stop()
            liveVoiceSession?.close()
            liveVoiceSession = null
            liveStatus = null
            if (::transcript.isInitialized) removeLastLoadingIfNeeded()
            if (::sendButton.isInitialized) sendButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        liveVoiceSession?.close()
        liveVoiceSession = null
        liveStatus = null
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
