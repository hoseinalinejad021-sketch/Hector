package com.hector.android

import android.os.Bundle
import android.os.Build
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hector_private", MODE_PRIVATE) }
    private val secureStore by lazy { SecureMemoryStore(this) }
    private val agentTaskJournal by lazy { AgentTaskJournal(this) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var unlocked = false
    private var speechLaunchPending = false
    @Volatile private var sessionGeneration = 0L
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val networkLock = Any()
    private lateinit var root: LinearLayout
    private var lastRenderedEmergencyStop: Boolean? = null
    private lateinit var status: TextView
    private var agentProgress: TextView? = null
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var appUpdateManager: AppUpdateManager? = null
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            appUpdateManager?.completeUpdate()
        }
    }
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
    private val voiceNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && unlocked) showLiveVoiceScreen()
        else Toast.makeText(this, "برای اجرای شفاف صدای پس‌زمینه، اعلان دائمی را مجاز کن.", Toast.LENGTH_LONG).show()
    }
    private val pushNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registerForPushNotifications()
        else Toast.makeText(this, "اعلان‌ها مجاز نشدند؛ برای نمایش اعلان‌های هیکتور باید از تنظیمات گوشی اجازه بدهی.", Toast.LENGTH_LONG).show()
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
        syncCloudIfEnabled()
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
        root.addView(button("همگام‌سازی ابری حافظه") { showCloudSyncDialog() })
        root.addView(button("فعال‌سازی اعلان‌های هیکتور") { enablePushNotifications() })
        root.addView(button("بررسی به‌روزرسانی رسمی") { checkForPlayUpdates() })

        root.addView(label("یادداشت حافظه هیکتور (محلی؛ همگام‌سازی ابری اختیاری)", 16f))
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
            markLocalDataChanged()
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
        val progressView = label("آماده", 12f).apply {
            setTextColor(Color.LTGRAY)
            gravity = Gravity.START
            setPadding(8, 4, 8, 4)
        }
        agentProgress = progressView
        root.addView(progressView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        root.addView(button("محیط کدنویسی و تولید کد با هوش مصنوعی") {
            if (prefs.getBoolean("emergency_stop_engaged", false)) {
                Toast.makeText(this, "توقف اضطراری فعال است؛ ابتدا آن را به‌صورت دستی لغو کن.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, CodeStudioActivity::class.java))
            }
        })
        val stopEngaged = prefs.getBoolean("emergency_stop_engaged", false)
        lastRenderedEmergencyStop = stopEngaged
        if (stopEngaged) {
            root.addView(label("توقف اضطراری فعال است؛ درخواست‌های هوش مصنوعی و صدا متوقف‌اند.", 14f))
        }
        root.addView(button(if (stopEngaged) "لغو توقف اضطراری HIC-STOP-01" else "توقف اضطراری HIC-STOP-01") {
            lockNow(activateEmergencyStop = true)
        })
        setContentView(root)
    }

    private fun publishAgentProgress(message: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) agentProgress?.text = message.take(180)
        }
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
        val endpoint = prefs.getString("endpoint", "")?.trim().orEmpty()
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        showLiveVoiceScreen()
    }

    private fun showLiveVoiceScreen() {
        if (!unlocked || isFinishing || isDestroyed) return
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            Toast.makeText(this, "توقف اضطراری فعال است؛ GPT-Live شروع نشد.", Toast.LENGTH_LONG).show()
            return
        }
        val configuredUrl = prefs.getString("live_gateway_url", "").orEmpty().trim()
        if (!LiveGatewayUrlValidator.isValid(configuredUrl)) {
            AlertDialog.Builder(this)
                .setTitle("تنظیم دروازهٔ GPT-Live")
                .setMessage("نشانی کامل HTTPS دروازهٔ Cloudflare Worker را با مسیر /live در تنظیمات اتصال وارد کن. مثال: https://your-worker.example.workers.dev/live")
                .setPositiveButton("باز کردن تنظیمات") { _, _ -> showAiSettings() }
                .setNegativeButton("لغو", null)
                .show()
            return
        }
        try {
            val serviceIntent = Intent(this, HectorVoiceForegroundService::class.java)
                .setAction(HectorVoiceForegroundService.ACTION_WEBVIEW_SESSION)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "سرویس صدای امن شروع نشد: ${e.message ?: "خطای اندروید"}", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, LiveVoiceActivity::class.java).putExtra(LiveVoiceActivity.EXTRA_URL, configuredUrl))
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
        val url = endpoint.trim().removeSuffix("/")
        if (!url.startsWith("https://", ignoreCase = true)) return "برای محافظت از کلید API، نشانی سرویس باید با HTTPS شروع شود."
        val parsedUrl = try { URL(url) } catch (_: Exception) { return "نشانی سرویس معتبر نیست." }
        if (parsedUrl.protocol != "https" || parsedUrl.host.isNullOrBlank() || parsedUrl.userInfo != null) {
            return "نشانی سرویس معتبر نیست؛ فقط HTTPS با میزبان مشخص و بدون اطلاعات ورود در نشانی پذیرفته می‌شود."
        }
        if (!unlocked || requestGeneration != sessionGeneration) return "درخواست به‌دلیل توقف یا قفل‌شدن هیکتور لغو شد."

        val legacy = url.endsWith("/chat/completions", ignoreCase = true)
        val modelName = prefs.getString("model", "gpt-5.5").orEmpty().ifBlank { "gpt-5.5" }
        val historyJson = readHistory()
        val historyLines = mutableListOf<String>()
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content",
            "You are Hector, a helpful personal assistant. User's local memory: " + readSecureString("memory")))
        for (i in 0 until historyJson.length()) {
            val item = historyJson.optJSONObject(i) ?: continue
            val role = item.optString("role")
            val content = item.optString("content")
            if ((role == "user" || role == "assistant") && content != "در حال دریافت پاسخ…") {
                messages.put(JSONObject().put("role", role).put("content", content))
                historyLines.add(role + ": " + content)
            }
        }

        val taskGoal = (historyJson.length() - 1 downTo 0)
            .mapNotNull { index ->
                val item = historyJson.optJSONObject(index) ?: return@mapNotNull null
                if (item.optString("role") == "user") item.optString("content") else null
            }
            .firstOrNull()
            .orEmpty()
        val validatedTaskGoal = try {
            AgentTaskPolicy.validateTask(taskGoal)
        } catch (e: IllegalArgumentException) {
            return "درخواست برای اجرای وظیفه معتبر نیست: ${e.message ?: "متن نامعتبر"}"
        }
        val toolExecutionChecks = mutableListOf<Boolean>()
        var executedToolCount = 0
        agentTaskJournal.record(validatedTaskGoal, "running", "Task accepted; independent goal verification is pending.")

        fun send(payload: JSONObject): Pair<Int, String>? {
            if (!unlocked || requestGeneration != sessionGeneration || prefs.getBoolean("emergency_stop_engaged", false)) return null
            var connection: HttpURLConnection? = null
            try {
                val current = (parsedUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer " + apiKey)
                }
                connection = current
                synchronized(networkLock) {
                    if (!unlocked || requestGeneration != sessionGeneration || prefs.getBoolean("emergency_stop_engaged", false)) {
                        current.disconnect()
                        return null
                    }
                    activeConnection = current
                }
                OutputStreamWriter(current.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                if (!unlocked || requestGeneration != sessionGeneration || prefs.getBoolean("emergency_stop_engaged", false)) return null
                val code = current.responseCode
                val stream = if (code in 200..299) current.inputStream else current.errorStream
                return code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
            } catch (e: Exception) {
                return -1 to (e.message ?: "Network error")
            } finally {
                connection?.disconnect()
                synchronized(networkLock) { if (activeConnection === connection) activeConnection = null }
            }
        }

        fun diagnoseHttp(code: Int, body: String): String {
            if (code == -1) return "اتصال برقرار نشد: " + body.take(250) + ". نشانی سرویس و اینترنت را بررسی کن."
            val errorObject = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
            val detail = errorObject?.optString("message").orEmpty()
            val metadata = listOf(errorObject?.optString("type").orEmpty(), errorObject?.optString("code").orEmpty())
                .filter { it.isNotBlank() && it != "null" }.distinct().joinToString(" / ")
            val diagnosis = when (code) {
                401 -> "احراز هویت ناموفق است (401). کلید API را در تنظیمات هیکتور بررسی کن؛ آن را برای کسی نفرست."
                403 -> "دسترسی رد شد (403). مجوز پروژه، دسترسی مدل و محدودیت‌های حساب را بررسی کن."
                404 -> "مسیر API یا نام مدل پیدا نشد (404)."
                429 -> "محدودیت درخواست یا اعتبار/سهمیه حساب (429)."
                in 500..599 -> "سرویس هوش مصنوعی موقتاً خطای سرور داده است؛ بعداً دوباره امتحان کن."
                else -> "درخواست API رد شد (HTTP " + code + "). نشانی، مدل و مجوز حساب را بررسی کن."
            }
            return diagnosis + (if (metadata.isNotBlank()) "\nکد خطا: " + metadata.take(120) else "") +
                (if (detail.isNotBlank()) "\nجزئیات سرویس: " + detail.take(300) else "")
        }

        if (legacy) {
            val result = send(JSONObject().put("model", modelName).put("messages", messages))
                ?: return "درخواست به‌دلیل توقف یا قفل‌شدن هیکتور لغو شد."
            if (result.first !in 200..299) return diagnoseHttp(result.first, result.second)
            return try {
                JSONObject(result.second).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    .optString("content").ifBlank { "مدل پاسخ متنی برنگرداند." }
            } catch (_: Exception) { "پاسخ سرویس قابل‌خواندن نبود." }
        }

        val tools = JSONArray()
            .put(JSONObject().put("type", "web_search"))
            .put(JSONObject().put("type", "function").put("name", "read_memory")
                .put("description", "Read the user's locally saved Hector memory.").put("strict", true)
                .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject())
                    .put("required", JSONArray()).put("additionalProperties", false)))
            .put(JSONObject().put("type", "function").put("name", "search_memory")
                .put("description", "Search the user's local Hector memory for a short phrase.").put("strict", true)
                .put("parameters", JSONObject().put("type", "object")
                    .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("query")).put("additionalProperties", false)))
            .put(JSONObject().put("type", "function").put("name", "search_history")
                .put("description", "Search local conversation history for a short phrase.").put("strict", true)
                .put("parameters", JSONObject().put("type", "object")
                    .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("query")).put("additionalProperties", false)))

        var previousResponseId: String? = null
        var nextInput = messages
        val maxRounds = 6
        for (round in 0 until maxRounds) {
            publishAgentProgress("هیکتور: بررسی مرحلهٔ API " + (round + 1) + " از " + maxRounds)
            if (!unlocked || requestGeneration != sessionGeneration || prefs.getBoolean("emergency_stop_engaged", false)) {
                return "وظیفه به‌دلیل توقف اضطراری یا لغو جلسه متوقف شد."
            }
            val payload = JSONObject().put("model", modelName).put("tools", tools)
            if (previousResponseId == null) payload.put("input", nextInput)
            else payload.put("previous_response_id", previousResponseId).put("input", nextInput)
            val result = send(payload) ?: return "درخواست به‌دلیل توقف یا قفل‌شدن هیکتور لغو شد."
            if (result.first !in 200..299) return diagnoseHttp(result.first, result.second)
            val response = try { JSONObject(result.second) } catch (_: Exception) { return "پاسخ سرویس JSON معتبر نبود." }
            val parsed = try { ResponsesToolCallParser.parse(response) } catch (e: Exception) {
                return "فراخوانی ابزار به‌دلیل خروجی نامعتبر مدل رد شد: " + (e.message ?: "خطای اعتبارسنجی")
            }
            if (parsed.calls.isEmpty()) {
                publishAgentProgress("هیکتور: پاسخ دریافت شد")
                val verification = AgentTaskVerifier.evaluate(
                    AgentTaskVerifier.Evidence(
                        executionStarted = executedToolCount > 0,
                        requiredChecks = toolExecutionChecks.toList(),
                        cancelled = !unlocked || requestGeneration != sessionGeneration ||
                            prefs.getBoolean("emergency_stop_engaged", false)
                    )
                )
                val journalStatus = when (verification.outcome) {
                    AgentTaskVerifier.Outcome.FAILED -> "failed"
                    AgentTaskVerifier.Outcome.CANCELLED -> "cancelled"
                    else -> "incomplete"
                }
                val journalSummary = if (executedToolCount == 0) {
                    "A response was generated without tool execution; the user goal was not independently verified."
                } else {
                    "Tool execution: ${verification.summary} User goal not independently verified."
                }
                agentTaskJournal.record(validatedTaskGoal, journalStatus, journalSummary)
                val answer = parsed.text.ifBlank { "مدل پاسخ متنی برنگرداند؛ تنظیمات مدل و API را بررسی کن." }
                return if (verification.outcome == AgentTaskVerifier.Outcome.FAILED) {
                    answer + "\n\nهیکتور: یک یا چند ابزار ناموفق بود؛ نتیجهٔ هدف تأیید نشده است."
                } else answer
            }
            previousResponseId = response.optString("id").takeIf { it.isNotBlank() }
                ?: return "پاسخ API شناسهٔ ادامه نداشت؛ اجرای ابزار متوقف شد."
            nextInput = JSONArray()
            for (call in parsed.calls) {
                publishAgentProgress("هیکتور: اجرای ابزار مجاز «" + call.name + "»")
                if (!unlocked || requestGeneration != sessionGeneration || prefs.getBoolean("emergency_stop_engaged", false)) {
                    agentTaskJournal.record(validatedTaskGoal, "cancelled", "Task cancelled before the next tool call.")
                    return "وظیفه پیش از اجرای ابزار بعدی متوقف شد."
                }
                if (executedToolCount >= AgentTaskPolicy.MAX_STEPS) {
                    agentTaskJournal.record(validatedTaskGoal, "failed", "The task exceeded the configured tool-step limit.")
                    return "هیکتور برای ایمنی پس از ${AgentTaskPolicy.MAX_STEPS} مرحله متوقف شد؛ هدف تأیید نشده است."
                }
                val definition = AgentToolRegistry.definition(call.name)
                val step = AgentTaskPolicy.Step(
                    id = "step_${executedToolCount + 1}",
                    tool = call.name,
                    description = "Run allowlisted read-only tool ${call.name}",
                    risk = definition?.risk ?: AgentTaskPolicy.Risk.PRIVILEGED,
                    arguments = call.arguments
                )
                try {
                    AgentTaskPolicy.validatePlan(listOf(step))
                } catch (e: IllegalArgumentException) {
                    agentTaskJournal.record(validatedTaskGoal, "failed", "Step rejected by task policy: ${e.message ?: "invalid step"}.")
                    return "فراخوانی ابزار به‌دلیل سیاست ایمنی رد شد."
                }
                if (definition == null || AgentTaskPolicy.requiresApproval(definition.risk)) {
                    agentTaskJournal.record(validatedTaskGoal, "needs_approval", "Tool rejected because its risk requires explicit approval.")
                    return "این ابزار بدون تأیید صریح کاربر اجرا نمی‌شود."
                }
                val toolResult = AgentToolRegistry.execute(call.name, call.arguments, readSecureString("memory"), historyLines)
                executedToolCount++
                toolExecutionChecks.add(toolResult.ok)
                val boundedResult = AgentTaskPolicy.boundedOutput(toolResult.text)
                agentTaskJournal.record(
                    validatedTaskGoal,
                    if (toolResult.ok) "running" else "failed",
                    "Step ${executedToolCount}: ${call.name}; ${if (toolResult.ok) "tool returned successfully" else "tool returned an error"}."
                )
                nextInput.put(JSONObject().put("type", "function_call_output").put("call_id", call.callId)
                    .put("output", JSONObject().put("ok", toolResult.ok).put("result", boundedResult).toString()))
            }
        }
        return "هیکتور پس از " + maxRounds + " دور API برای ایمنی متوقف شد. نتیجهٔ نهایی هنوز تأیید نشده؛ درخواست را کوتاه‌تر یا به چند مرحله تقسیم کن."
    }

    private fun showCloudSyncDialog() {
        if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) {
            AlertDialog.Builder(this)
                .setTitle("تنظیم اولیهٔ حافظهٔ ابری")
                .setMessage("برای فعال‌شدن همگام‌سازی واقعی، پروژهٔ Firebase مربوط به com.hector.android و فایل app/google-services.json لازم است. این فایل هنوز در مخزن نیست؛ پس از افزودن آن، ورود ایمیل/رمز و Firestore فعال می‌شوند. کلید API هوش مصنوعی هرگز همگام نمی‌شود.")
                .setPositiveButton("متوجه شدم", null).show()
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val email = EditText(this).apply {
            hint = "ایمیل حساب هیکتور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(this).apply {
            hint = "رمز عبور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(email); box.addView(password)
        AlertDialog.Builder(this)
            .setTitle("همگام‌سازی ابری حافظه")
            .setMessage("با ورود یا ساخت حساب، یادداشت حافظه و تاریخچهٔ گفت‌وگو به Firestore منتقل می‌شوند. کلید API و تنظیمات اتصال منتقل نمی‌شوند. همگام‌سازی دوطرفه بر اساس آخرین زمان تغییر انجام می‌شود.")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ورود و همگام‌سازی") { _, _ ->
                authenticateCloud(email.text.toString().trim(), password.text.toString(), false)
            }
            .setNeutralButton("ساخت حساب") { _, _ ->
                authenticateCloud(email.text.toString().trim(), password.text.toString(), true)
            }.show()
    }

    private fun authenticateCloud(email: String, password: String, create: Boolean) {
        CloudMemorySync.authenticate(this, email, password, create) { error ->
            runOnUiThread {
                if (error != null) {
                    Toast.makeText(this, "ورود ابری انجام نشد: $error", Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putBoolean("cloud_sync_enabled", true).apply()
                    // Re-register the current FCM token after login. onNewToken may have fired
                    // before authentication, when the device could not be attached to this UID.
                    registerCurrentPushTokenForSignedInUser()
                    // Leave a new device's local timestamp at zero so an existing cloud copy
                    // is restored instead of being overwritten by an artificially newer clock.
                    syncCloudNow()
                }
            }
        }
    }

    private fun syncCloudNow() {
        CloudMemorySync.sync(
            this,
            readSecureString("memory"),
            readSecureString("history"),
            prefs.getLong("cloud_local_updated_at", 0L)
        ) { result ->
            runOnUiThread {
                result.onSuccess { sync ->
                    if (sync.remoteWon) {
                        writeSecureString("memory", sync.memory)
                        writeSecureString("history", sync.history)
                    }
                    prefs.edit().putLong("cloud_local_updated_at", sync.updatedAtMillis).apply()
                    Toast.makeText(this, if (sync.remoteWon) "حافظهٔ ابری روی این گوشی بازیابی شد." else "حافظهٔ هیکتور با فضای ابری همگام شد.", Toast.LENGTH_LONG).show()
                    if (::transcript.isInitialized) showHome()
                }.onFailure { error ->
                    Toast.makeText(this, "همگام‌سازی ناموفق بود: ${error.message ?: "خطای نامشخص"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun markLocalDataChanged() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("cloud_local_updated_at", now).apply()
        syncCloudIfEnabled()
    }

    private fun syncCloudIfEnabled() {
        if (!prefs.getBoolean("cloud_sync_enabled", false)) return
        if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        CloudMemorySync.sync(this, readSecureString("memory"), readSecureString("history"), prefs.getLong("cloud_local_updated_at", 0L)) { result ->
            result.onSuccess { sync ->
                if (sync.remoteWon) {
                    runOnUiThread {
                        writeSecureString("memory", sync.memory)
                        writeSecureString("history", sync.history)
                        if (!isFinishing && !isDestroyed && ::transcript.isInitialized) showHome()
                    }
                }
                prefs.edit().putLong("cloud_local_updated_at", sync.updatedAtMillis).apply()
            }
        }
    }

    private fun enablePushNotifications() {
        if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) {
            Toast.makeText(this, "اعلان‌های ابری به تنظیمات پروژهٔ Firebase و فایل google-services.json نیاز دارند.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            registerForPushNotifications()
        }
    }

    private fun registerForPushNotifications() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                prefs.edit().putString("fcm_token", token).apply()
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) registerCurrentPushTokenForSignedInUser()
                val message = if (user != null) {
                    "ثبت دستگاه اعلان انجام شد؛ ارسال اعلان به سرویس سمت سرور نیاز دارد."
                } else {
                    "ثبت اعلان روی گوشی آماده شد؛ برای ثبت دستگاه در حساب، ابتدا وارد حساب ابری شو."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "ثبت دستگاه اعلان ممکن نشد: ${error.localizedMessage ?: "خطای نامشخص"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /**
     * FCM delivery tokens and Firebase Installation IDs are different identifiers.
     * Store the delivery token locally; only bind the installation ID to an authenticated user.
     */
    private fun registerCurrentPushTokenForSignedInUser() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                prefs.edit().putString("fcm_token", token).apply()
                FirebaseInstallations.getInstance().id
                    .addOnSuccessListener { installationId ->
                        if (FirebaseAuth.getInstance().currentUser != null) {
                            CloudMemorySync.registerDeviceInstallationId(this, installationId) { /* best effort */ }
                        }
                    }
            }
            .addOnFailureListener { error ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "ورود انجام شد اما ثبت دستگاه اعلان ناموفق بود: ${error.localizedMessage ?: "خطای نامشخص"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun checkForPlayUpdates(showUnavailable: Boolean = true) {
        val manager = appUpdateManager ?: AppUpdateManagerFactory.create(this).also {
            appUpdateManager = it
            it.registerListener(installStateUpdatedListener)
        }
        val flexibleOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> manager.completeUpdate()
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(flexibleOptions) -> {
                        manager.startUpdateFlowForResult(info, this, flexibleOptions, 2901)
                    }
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        manager.startUpdateFlowForResult(info, this, flexibleOptions, 2901)
                    }
                    showUnavailable -> Toast.makeText(this, "به‌روزرسانی Play در دسترس نیست. این قابلیت فقط برای نسخه‌ای کار می‌کند که از Google Play نصب شده و نسخهٔ جدید آنجا منتشر شده باشد.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                if (showUnavailable) Toast.makeText(this, "بررسی به‌روزرسانی Play ناموفق بود؛ اگر APK را دستی نصب کرده‌ای، ابتدا باید نسخهٔ هیکتور در Google Play منتشر شود.", Toast.LENGTH_LONG).show()
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
        val liveGateway = EditText(this).apply {
            hint = "دروازهٔ GPT-Live؛ HTTPS و مسیر /live"
            setText(prefs.getString("live_gateway_url", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(endpoint); box.addView(key); box.addView(model)
        box.addView(label("نشانی دروازهٔ صوتی (Cloudflare Worker)؛ کلید اصلی OpenAI را اینجا وارد نکن.", 13f))
        box.addView(liveGateway)
        AlertDialog.Builder(this).setTitle("پیکربندی اتصال هوش مصنوعی")
            .setMessage("برای هوش مصنوعی آنلاین و جست‌وجوی وب، نشانی https://api.openai.com/v1/responses و کلید معتبر OpenAI لازم است. نشانی /chat/completions فقط حالت قدیمی بدون جست‌وجوی وب را فعال می‌کند. کلید در فضای خصوصی برنامه ذخیره می‌شود.")
            .setView(box)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val gateway = liveGateway.text.toString().trim()
                val gatewayValid = gateway.isEmpty() || LiveGatewayUrlValidator.isValid(gateway)
                if (!gatewayValid) {
                    Toast.makeText(this, "نشانی دروازه باید HTTPS و دقیقاً مسیر /live یا /live/ داشته باشد.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putString("endpoint", endpoint.text.toString().trim())
                    .putString("model", model.text.toString().trim().ifEmpty { "gpt-5.5" })
                    .putString("live_gateway_url", gateway)
                    .apply()
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
        markLocalDataChanged()
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
        stopService(Intent(this, HectorVoiceForegroundService::class.java))
        val wasStopped = prefs.getBoolean("emergency_stop_engaged", false)
        val desiredStop = activateEmergencyStop && !wasStopped
        val persisted = prefs.edit().putBoolean("emergency_stop_engaged", desiredStop).commit()
        // Fail closed: if persistence fails, keep the in-memory latch engaged rather than
        // accidentally allowing AI or microphone work to resume after a failed clear.
        val stopNow = if (persisted) {
            desiredStop
        } else {
            prefs.edit().putBoolean("emergency_stop_engaged", true).commit()
            true
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
            val finalMessage = if (!persisted) {
                "ذخیرهٔ وضعیت توقف اضطراری ناموفق بود؛ برای ایمنی توقف فعال نگه داشته شد. فضای ذخیره‌سازی گوشی را بررسی کن."
            } else message
            Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show()
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

    override fun onResume() {
        super.onResume()
        if (appUpdateManager == null) checkForPlayUpdates(showUnavailable = false)
        val currentEmergencyStop = prefs.getBoolean("emergency_stop_engaged", false)
        if (::root.isInitialized && lastRenderedEmergencyStop != currentEmergencyStop) {
            showHome()
        }
    }

    override fun onStop() {
        super.onStop()
        if (unlocked && !isChangingConfigurations && !speechLaunchPending) {
            // Backgrounding cancels activity-owned requests and speech output.
            // The user-started microphone foreground service is independent and continues.
            sessionGeneration++
            synchronized(networkLock) {
                activeConnection?.disconnect()
                activeConnection = null
            }
            textToSpeech?.stop()
            if (::transcript.isInitialized) removeLastLoadingIfNeeded()
        }
    }

    override fun onDestroy() {
        sessionGeneration++
        synchronized(networkLock) {
            activeConnection?.disconnect()
            activeConnection = null
        }
        appUpdateManager?.unregisterListener(installStateUpdatedListener)
        appUpdateManager = null
        executor.shutdownNow()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}
