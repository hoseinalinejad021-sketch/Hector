package com.hector.android

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Restricted app-private helper terminal. User input is never passed to a shell.
 * Only allowlisted Android toolbox commands run as the normal app UID.
 */
class TerminalActivity : Activity() {
    private lateinit var workspace: File
    private lateinit var currentDirectory: File
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var runButton: Button
    @Volatile private var activeProcess: Process? = null
    @Volatile private var destroyed = false
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("hector_private", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = File(filesDir, "terminal-workspace").apply { mkdirs() }.canonicalFile
        currentDirectory = workspace
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(9, 14, 22))
        }
        root.addView(TextView(this).apply {
            text = "HECTOR • ترمینال داخلی"
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "محیط محدود و خصوصی هیکتور؛ بدون root و بدون اجرای پوستهٔ آزاد. فقط فرمان‌های مجاز و مسیرهای داخل فضای کاری."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 8)
        })
        status = TextView(this).apply {
            setTextColor(Color.rgb(125, 211, 252))
            text = "آماده • ${currentDirectory.name}"
        }
        root.addView(status)
        output = TextView(this).apply {
            text = "HECTOR SAFE TERMINAL\nفرمان‌ها: help, pwd, ls, mkdir, touch, cat, cp, mv, rm, wc, echo, date, id, cd, clear\n"
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(226, 232, 240))
            setTextIsSelectable(true)
            setPadding(10, 10, 10, 10)
        }
        root.addView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(15, 23, 42))
            addView(output)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "فرمان را وارد کن…"
            setSingleLine(true)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 41, 59))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        runButton = makeButton("بررسی و اجرای فرمان") { confirmAndRun() }
        root.addView(runButton)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(makeButton("توقف فرمان") { stopCommand() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(makeButton("پاک‌کردن خروجی") { output.text = "" }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        root.addView(makeButton("بازگشت به Code Studio") { finish() })
        setContentView(root)
    }

    private fun confirmAndRun() {
        if (destroyed) return
        if (prefs.getBoolean("emergency_stop_engaged", false)) {
            status.text = "توقف اضطراری فعال است؛ فرمان اجرا نشد."
            return
        }
        val raw = input.text.toString().trim()
        val parsed = try {
            TerminalCommandPolicy.parse(raw)
        } catch (e: IllegalArgumentException) {
            status.text = e.message ?: "فرمان رد شد."
            appendOutput("[رد شد] ${status.text}\n")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأیید اجرای فرمان")
            .setMessage("این فرمان در محیط محدود هیکتور اجرا می‌شود. پوسته، زنجیره‌سازی و مسیرهای خارج از فضای کاری مجاز نیستند.\n\n${raw}\n\nمسیر: ${currentDirectory.path}")
            .setNegativeButton("لغو", null)
            .setPositiveButton("تأیید و اجرا") { _, _ -> executeConfirmed(parsed, raw) }
            .show()
    }

    private fun executeConfirmed(command: TerminalCommandPolicy.ParsedCommand, raw: String) {
        if (destroyed || prefs.getBoolean("emergency_stop_engaged", false)) {
            status.text = "توقف اضطراری فعال است؛ فرمان اجرا نشد."
            return
        }
        if (activeProcess != null) {
            status.text = "یک فرمان هنوز در حال اجراست؛ ابتدا آن را متوقف کن."
            return
        }
        try {
            when (command.name) {
                "help" -> appendOutput("فرمان‌های مجاز: pwd, ls, mkdir, touch, cat, cp, mv, rm, wc, echo, date, id, cd, clear\nعملگرهای پوسته و مسیر خارج از فضای کاری ممنوع‌اند.\n")
                "clear" -> output.text = ""
                "pwd" -> appendOutput("${currentDirectory.path}\n")
                "date" -> appendOutput(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date()) + "\n")
                "id" -> appendOutput("UID عادی برنامهٔ اندروید؛ بدون root\n")
                "echo" -> appendOutput(command.args.joinToString(" ") + "\n")
                "cd" -> changeDirectory(command.args.firstOrNull().orEmpty())
                else -> launchAllowedProcess(command, raw)
            }
        } catch (e: Exception) {
            status.text = e.message ?: "اجرای فرمان ناموفق بود."
            appendOutput("[خطا] ${status.text}\n")
        }
    }

    private fun launchAllowedProcess(command: TerminalCommandPolicy.ParsedCommand, raw: String) {
        val resolvedArgs = command.args.map { arg ->
            TerminalCommandPolicy.resolveWorkspacePath(workspace, currentDirectory, arg).absolutePath
        }
        appendOutput("\n$ $raw\n")
        input.setText("")
        runButton.isEnabled = false
        status.text = "در حال اجرا… سقف زمان ۱۲ ثانیه"
        executor.execute {
            var process: Process? = null
            val timedOut = AtomicBoolean(false)
            val emergencyStopped = AtomicBoolean(false)
            try {
                if (destroyed || prefs.getBoolean("emergency_stop_engaged", false)) {
                    runOnUiThread { if (!destroyed) status.text = "توقف اضطراری فعال است؛ فرمان اجرا نشد." }
                    return@execute
                }
                val running = ProcessBuilder(listOf("/system/bin/${command.name}") + resolvedArgs)
                    .directory(currentDirectory)
                    .redirectErrorStream(true)
                    .apply {
                        environment().clear()
                        environment()["HOME"] = workspace.absolutePath
                        environment()["TMPDIR"] = cacheDir.absolutePath
                        environment()["PATH"] = "/system/bin"
                    }
                    .start()
                process = running
                activeProcess = running
                val reader = Thread({
                    try {
                        val captured = StringBuilder()
                        running.inputStream.bufferedReader(Charsets.UTF_8).use { stream ->
                            val buffer = CharArray(1024)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                synchronized(captured) {
                                    if (captured.length < 32768) {
                                        captured.append(buffer, 0, minOf(count, 32768 - captured.length))
                                    }
                                }
                            }
                        }
                        synchronized(captured) { if (!destroyed) runOnUiThread { appendOutput(captured.toString()) } }
                    } catch (_: Exception) { }
                }, "HectorTerminalOutput").apply { isDaemon = true; start() }
                val watchdog = Thread({
                    try {
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
                        while (!Thread.currentThread().isInterrupted && running.isAlive) {
                            if (destroyed || prefs.getBoolean("emergency_stop_engaged", false)) {
                                emergencyStopped.set(true)
                                terminateProcess(running)
                                break
                            }
                            if (System.nanoTime() >= deadline) {
                                timedOut.set(true)
                                terminateProcess(running)
                                break
                            }
                            Thread.sleep(100)
                        }
                    } catch (_: InterruptedException) { }
                }, "HectorTerminalWatchdog").apply { isDaemon = true; start() }
                val exitCode = running.waitFor()
                watchdog.interrupt()
                reader.join(1500)
                runCatching { running.inputStream.close() }
                runOnUiThread {
                    if (!destroyed) {
                        appendOutput(
                            when {
                                emergencyStopped.get() -> "\n[به‌دلیل توقف اضطراری متوقف شد]\n"
                                timedOut.get() -> "\n[پس از ۱۲ ثانیه متوقف شد]\n"
                                else -> "\n[exit code: $exitCode]\n"
                            }
                        )
                        status.text = when {
                            emergencyStopped.get() -> "توقف اضطراری؛ فرمان متوقف شد."
                            timedOut.get() -> "فرمان به دلیل پایان مهلت متوقف شد."
                            else -> "پایان فرمان • کد خروج: $exitCode"
                        }
                        runButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!destroyed) {
                        appendOutput("[خطا] ${e.message ?: "اجرای فرمان ناموفق بود."}\n")
                        status.text = "اجرای فرمان ناموفق بود."
                        runButton.isEnabled = true
                    }
                }
            } finally {
                activeProcess = null
                runCatching { process?.inputStream?.close() }
                if (!destroyed) runOnUiThread { runButton.isEnabled = true }
            }
        }
    }

    private fun changeDirectory(path: String) {
        val target = try {
            TerminalCommandPolicy.resolveWorkspacePath(workspace, currentDirectory, path.ifBlank { workspace.path })
        } catch (e: Exception) {
            status.text = e.message ?: "مسیر پوشه مجاز نیست."
            return
        }
        if (!target.isDirectory) {
            status.text = "پوشه پیدا نشد: ${target.name}"
            return
        }
        currentDirectory = target
        appendOutput("$ cd ${path.ifBlank { "~" }}\n${currentDirectory.path}\n")
        status.text = "مسیر فعلی: ${currentDirectory.name}"
    }

    private fun stopCommand() {
        val process = activeProcess
        if (process == null) status.text = "فرمانی در حال اجرا نیست."
        else {
            terminateProcess(process)
            status.text = "درخواست توقف برای فرمان ارسال شد."
        }
    }

    private fun terminateProcess(process: Process) {
        runCatching { process.destroy() }
        runCatching { if (process.isAlive) process.destroyForcibly() }
    }

    private fun appendOutput(value: String) {
        if (destroyed) return
        output.append(value)
        if (output.text.length > 60000) output.text = output.text.takeLast(60000)
    }

    private fun makeButton(caption: String, action: () -> Unit): Button =
        Button(this).apply { text = caption; setOnClickListener { action() } }

    override fun onDestroy() {
        destroyed = true
        activeProcess?.let(::terminateProcess)
        executor.shutdownNow()
        super.onDestroy()
    }
}
