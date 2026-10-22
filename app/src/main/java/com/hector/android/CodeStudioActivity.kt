package com.hector.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * A small offline code/text workspace inside Hector.
 * Files are stored only in this app's private files directory.
 * This is an editor, not a shell; it never executes user-entered code.
 */
class CodeStudioActivity : Activity() {
    private lateinit var workspace: File
    private lateinit var fileName: EditText
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var prompt: EditText
    private lateinit var generateButton: Button
    private val prefs by lazy { getSharedPreferences("hector_private", MODE_PRIVATE) }
    private val secureStore by lazy { SecureMemoryStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = File(filesDir, "code-workspace").apply { mkdirs() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(16, 21, 31))
        }
        root.addView(TextView(this).apply {
            text = "Hector Code Studio"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "محیط کدنویسی محلی — فایل‌ها فقط داخل فضای خصوصی هیکتور ذخیره می‌شوند؛ اجرای فرمان‌ها غیرفعال است."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 12)
        })

        fileName = EditText(this).apply {
            hint = "نام فایل، مثلاً main.js"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 41, 59))
        }
        root.addView(fileName, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(makeButton("فایل جدید") { newFile() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(makeButton("باز کردن") { openFile() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(makeButton("ذخیره") { saveFile() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        root.addView(makeButton("اجرای فرمان در Termux") { requestTermuxCommand() })

        prompt = EditText(this).apply {
            hint = "به هیکتور بگو چه کدی بسازد یا در کد فعلی چه چیزی را اصلاح کند..."
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 41, 59))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(prompt, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        generateButton = makeButton("تولید / اصلاح کد با هوش مصنوعی") { generateCode() }
        root.addView(generateButton)

        editor = EditText(this).apply {
            hint = "کد یا متن را اینجا بنویس..."
            gravity = Gravity.TOP or Gravity.START
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(24, 33, 48))
            typeface = android.graphics.Typeface.MONOSPACE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 10
            setPadding(12, 12, 12, 12)
        }
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            text = "فضای کاری آماده است. برای تولید کد، اتصال هوش مصنوعی را در صفحهٔ اصلی تنظیم کن."
            setPadding(0, 8, 0, 4)
        }
        root.addView(status)
        root.addView(makeButton("بازگشت به هیکتور") { finish() })
        setContentView(root)
    }



    /**
     * Sends only the command explicitly entered and confirmed by the user to the
     * separately installed Termux app. Hector does not bundle a shell or execute
     * this command inside its own process.
     */
    private fun requestTermuxCommand() {
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            status.text = "توقف اضطراری فعال است؛ فرمان به Termux ارسال نشد."
            return
        }
        val commandInput = EditText(this).apply {
            hint = "مثال: pwd && ls -la"
            minLines = 2
            maxLines = 5
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(24, 16, 24, 16)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("اجرای فرمان در Termux")
            .setMessage("فرمان فقط پس از تأیید تو به برنامهٔ جداگانهٔ Termux فرستاده می‌شود. پیش از اجرا، متن فرمان را بررسی کن.")
            .setView(commandInput)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ارسال و اجرا") { _, _ ->
                val command = commandInput.text.toString().trim()
                if (command.isEmpty() || command.length > 4000) {
                    status.text = "فرمان خالی است یا از حد ۴۰۰۰ نویسه بیشتر است."
                    return@setPositiveButton
                }
                if (prefs.getBoolean("emergency_stop_engaged", false)) {
                    status.text = "توقف اضطراری فعال است؛ فرمان به Termux ارسال نشد."
                    return@setPositiveButton
                }
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux", "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                    putExtra("com.termux.RUN_COMMAND_LABEL", "Hector user-confirmed command")
                    putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "A command explicitly confirmed in Hector")
                }
                try {
                    startService(intent)
                    status.text = "فرمان به Termux ارسال شد. نتیجه در نشست Termux نمایش داده می‌شود؛ اجرای موفق هنوز تأیید نشده است."
                } catch (_: SecurityException) {
                    status.text = "دسترسی اجرا رد شد. Termux را نصب کن، allow-external-apps را فعال کن و مجوز Run commands in Termux environment را به Hector بده."
                } catch (_: Exception) {
                    status.text = "Termux در دسترس نیست یا اجرای فرمان مجاز نشد. نصب بودن Termux و تنظیمات RUN_COMMAND را بررسی کن."
                }
            }
            .show()
    }

    private fun generateCode() {
        if (destroyed) return
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            status.text = "توقف اضطراری فعال است؛ ابتدا آن را از صفحهٔ اصلی به‌صورت دستی لغو کن."
            return
        }
        val task = prompt.text.toString().trim()
        if (task.isEmpty()) {
            status.text = "ابتدا توضیح بده چه کدی ساخته یا اصلاح شود."
            return
        }
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1/responses")?.trim().orEmpty()
        val key = readSecureString("api_key").trim()
        val model = prefs.getString("model", "gpt-5.5").orEmpty().ifBlank { "gpt-5.5" }
        if (key.isEmpty()) {
            status.text = "کلید API ذخیره نشده؛ از صفحهٔ اصلی تنظیم اتصال هوش مصنوعی را باز کن."
            return
        }
        val name = fileName.text.toString().trim().ifEmpty { "unsaved-code" }
        val current = editor.text.toString().take(20000)
        val userPrompt = buildString {
            append("File: ").append(name).append("\nTask:\n").append(task).append("\n")
            if (current.isNotBlank()) append("\nCurrent file contents:\n").append(current).append("\n")
            append("\nReturn the complete updated file contents only, without Markdown fences or explanations. Preserve unrelated behavior. Never execute code or claim it was tested.")
        }
        generateButton.isEnabled = false
        status.text = "در حال درخواست کد از هوش مصنوعی…"
        executor.execute {
            val result = requestCode(endpoint, key, model, userPrompt)
            runOnUiThread {
                if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
                generateButton.isEnabled = true
                if (result.startsWith("ERROR:")) {
                    status.text = result.removePrefix("ERROR:")
                } else {
                    editor.setText(stripCodeFence(result))
                    editor.setSelection(editor.text.length)
                    status.text = "کد دریافت شد؛ برای ذخیره دکمهٔ «ذخیره» را بزن. کد خودکار اجرا یا آزمایش نشده است."
                }
            }
        }
    }

    private fun requestCode(endpoint: String, apiKey: String, model: String, userPrompt: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val address = endpoint.trim().removeSuffix("/")
            val parsed = try { URL(address) } catch (_: Exception) {
                return "ERROR:نشانی API معتبر نیست؛ تنظیمات هوش مصنوعی را بررسی کن."
            }
            if (parsed.protocol != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
                return "ERROR:نشانی API باید HTTPS و بدون اطلاعات ورود داخل نشانی باشد."
            }
            if (prefs.getBoolean("emergency_stop_engaged", false) || destroyed) {
                return "ERROR:توقف اضطراری فعال است؛ درخواست ارسال نشد."
            }
            connection = (parsed.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + apiKey)
            }
            activeConnection = connection
            val legacy = address.endsWith("/chat/completions", ignoreCase = true)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "You are Hector Code Assistant. Produce accurate maintainable code. Never execute code or claim tests passed."))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
            val payload = if (legacy) {
                JSONObject().put("model", model).put("messages", messages)
            } else {
                JSONObject().put("model", model).put("input", messages)
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            if (destroyed || prefs.getBoolean("emergency_stop_engaged", false)) {
                connection.disconnect()
                return "ERROR:درخواست کدنویسی لغو شد."
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
                val detail = error?.optString("message").orEmpty().take(220)
                val diagnosis = when (code) {
                    401 -> "کلید API نامعتبر یا لغوشده است (401)."
                    403 -> "حساب اجازهٔ استفاده از این مدل را ندارد (403)."
                    404 -> "مسیر API یا مدل پیدا نشد (404)."
                    429 -> "محدودیت درخواست یا اعتبار API (429)."
                    in 500..599 -> "سرویس هوش مصنوعی موقتاً خطای سرور داده است."
                    else -> "درخواست کدنویسی رد شد (HTTP " + code + ")."
                }
                return "ERROR:" + diagnosis + if (detail.isNotBlank()) " جزئیات: " + detail else ""
            }
            val response = JSONObject(body)
            val answer = if (legacy) {
                val choices = response.optJSONArray("choices") ?: JSONArray()
                choices.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            } else {
                val output = response.optJSONArray("output") ?: JSONArray()
                val text = StringBuilder()
                for (i in 0 until output.length()) {
                    val item = output.optJSONObject(i) ?: continue
                    if (item.optString("type") != "message") continue
                    val parts = item.optJSONArray("content") ?: continue
                    for (j in 0 until parts.length()) {
                        val part = parts.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") text.append(part.optString("text"))
                    }
                }
                text.toString()
            }
            answer.trim().ifBlank { "ERROR:پاسخ کدنویسی خالی بود؛ مسیر و مدل API را بررسی کن." }
        } catch (e: Exception) {
            "ERROR:اتصال برقرار نشد؛ اینترنت، نشانی و کلید API را بررسی کن. " + (e.message ?: "خطای ناشناخته")
        } finally {
            connection?.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    private fun stripCodeFence(value: String): String {
        val fence = String(charArrayOf(96.toChar(), 96.toChar(), 96.toChar()))
        val text = value.trim()
        if (!text.startsWith(fence)) return text
        val first = text.indexOf('\n')
        val last = text.lastIndexOf(fence)
        return if (first >= 0 && last > first) text.substring(first + 1, last).trim() else text
    }

    private fun readSecureString(name: String): String {
        val bytes = secureStore.read(name) ?: return ""
        return try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
    }

    override fun onDestroy() {
        destroyed = true
        activeConnection?.disconnect()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun makeButton(caption: String, action: () -> Unit): Button =
        Button(this).apply {
            text = caption
            setOnClickListener { action() }
        }

    private companion object {
        const val MAX_FILE_BYTES = 1_000_000L
    }

    private fun validatedFile(): File? {
        val name = fileName.text.toString().trim()
        if (name.isEmpty() || name in listOf(".", "..") ||
            name.contains("/") || name.contains("\\") ||
            name.startsWith(".") || name.length > 100
        ) {
            status.text = "نام فایل نامعتبر است؛ فقط یک نام ساده وارد کن."
            return null
        }
        return try {
            val root = workspace.canonicalFile
            val candidate = File(root, name).canonicalFile
            if (candidate.parentFile != root) {
                status.text = "مسیر فایل خارج از فضای کاری مجاز است."
                null
            } else candidate
        } catch (_: Exception) {
            status.text = "بررسی مسیر فایل ناموفق بود."
            null
        }
    }

    private fun newFile() {
        val file = validatedFile() ?: return
        if (file.exists()) {
            status.text = "این فایل وجود دارد؛ برای خواندن از «باز کردن» استفاده کن."
            return
        }
        editor.setText("")
        status.text = "فایل جدید آماده است؛ برای ایجاد آن «ذخیره» را بزن."
    }

    private fun openFile() {
        val file = validatedFile() ?: return
        if (!file.exists() || !file.isFile) {
            status.text = "فایل در فضای کاری پیدا نشد."
            return
        }
        if (file.length() > MAX_FILE_BYTES) {
            status.text = "فایل بیش از حد بزرگ است؛ سقف ویرایش ۱ مگابایت است."
            return
        }
        try {
            editor.setText(file.readText(Charsets.UTF_8))
            status.text = "باز شد: ${file.name}"
        } catch (e: Exception) {
            status.text = "خواندن فایل ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
        }
    }

    private fun saveFile() {
        val file = validatedFile() ?: return
        if (file.exists() && !file.isFile) {
            status.text = "مسیر انتخاب‌شده فایل نیست."
            return
        }
        try {
            val content = editor.text.toString()
            if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
                status.text = "فایل بیش از حد بزرگ است؛ سقف ذخیره ۱ مگابایت است."
                return
            }
            file.writeText(content, Charsets.UTF_8)
            status.text = "ذخیره شد: ${file.name}"
            Toast.makeText(this, "فایل در فضای خصوصی هیکتور ذخیره شد.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = "ذخیره ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
        }
    }
}
