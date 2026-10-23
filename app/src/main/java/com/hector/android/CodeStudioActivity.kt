package com.hector.android

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File

/**
 * A small offline code/text workspace inside Hector.
 * Files are stored only in this app's private files directory.
 * This is an editor, not a shell; it never executes user-entered code.
 */
class CodeStudioActivity : Activity() {
    private companion object {
        const val MAX_FILE_BYTES = 1_000_000L
    }

    private lateinit var workspace: File
    private lateinit var fileName: EditText
    private lateinit var editor: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = File(filesDir, "code-workspace").apply { mkdirs() }.canonicalFile

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
            text = "فضای کاری آماده است."
            setPadding(0, 8, 0, 4)
        }
        root.addView(status)
        root.addView(makeButton("بازگشت به هیکتور") { finish() })
        setContentView(root)
    }

    private fun makeButton(caption: String, action: () -> Unit): Button =
        Button(this).apply {
            text = caption
            setOnClickListener { action() }
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
        val candidate = File(workspace, name).canonicalFile
        if (candidate.parentFile != workspace) {
            status.text = "مسیر فایل خارج از فضای کاری مجاز است."
            return null
        }
        return candidate
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
            status.text = "فایل بزرگ‌تر از حد مجاز ۱ مگابایت است."
            return
        }
        try {
            editor.setText(file.readText(Charsets.UTF_8))
            status.text = "باز شد: ${file.name}"
        } catch (e: Exception) {
            status.text = "باز کردن فایل ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
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
                status.text = "ذخیره انجام نشد؛ حداکثر اندازهٔ فایل ۱ مگابایت است."
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
