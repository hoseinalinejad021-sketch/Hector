package com.hector.android

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A small, app-private Android shell, not a bundled Termux/Linux distribution.
 * Commands run as Hector's normal Android UID, without root, in this app sandbox.
 * Every command requires explicit confirmation and obeys Hector's emergency stop.
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
            text = "محیط محلی محدود به مجوزهای هیکتور؛ بدون دسترسی root و بدون توزیع کامل لینوکس. هر فرمان پیش از اجرا تأیید می‌شود."
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
            text = "Hector local shell\nType pwd, ls, mkdir, cat, echo, or another command available to Android's /system/bin/sh.\n"
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(226, 232, 240))
            setTextIsSelectable(true)
            setPadding(10, 10, 10, 10)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(15, 23, 42))
            addView(output)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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
        val command = input.text.toString().trim()
        if (command.isEmpty() || command.length > 2000) {
            status.text = "فرمان خالی است یا از ۲۰۰۰ نویسه بیشتر است."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأیید اجرای فرمان")
            .setMessage("این فرمان با دسترسی عادی اندروید و در محیط خصوصی هیکتور اجرا می‌شود؛ root یا محیط کامل Termux فراهم نیست. فرمان و مسیر فعلی را بررسی کن:\n\n${command}\n\nمسیر: ${currentDirectory.path}")
            .setNegativeButton("لغو", null)
            .setPositiveButton("تأیید و اجرا") { _, _ -> executeConfirmed(command) }
            .show()
    }

    private fun executeConfirmed(command: String) {
        if (destroyed || prefs.getBoolean("emergency_stop_engaged", false)) {
            status.text = "توقف اضطراری فعال است؛ فرمان اجرا نشد."
            return
        }
        if (activeProcess != null) {
            status.text = "یک فرمان هنوز در حال اجراست؛ ابتدا آن را متوقف کن."
            return
        }
        if (command == "cd" || command.startsWith("cd ")) {
            changeDirectory(command.removePrefix("cd").trim().trim('"').trim('\''))
            return
        }
        appendOutput("\n$ ${command}\n")
        input.setText("")
        runButton.isEnabled = false
        status.text = "در حال اجرا… سقف زمان ۱۲ ثانیه"
        executor.execute {
            var process: Process? = null
            val timedOut = AtomicBoolean(false)
            try {
                if (prefs.getBoolean("emergency_stop_engaged", false) || destroyed) {
                    runOnUiThread { status.text = "توقف اضطراری فعال است؛ فرمان اجرا نشد." }
                    return@execute
                }
                val running = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(currentDirectory)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] = workspace.absolutePath
                        environment()["TMPDIR"] = cacheDir.absolutePath
                        environment()["PATH"] = "/system/bin:/system/xbin"
                        environment()["TERM"] = "xterm"
                    }
                    .start()
                process = running
                activeProcess = running
                val outputTask = FutureTask {
                    val captured = StringBuilder()
                    running.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(1024)
                        var count: Int
                        while (reader.read(buffer).also { count = it } >= 0) {
                            if (captured.length < 32768) {
                                val allowed = minOf(count, 32768 - captured.length)
                                captured.append(buffer, 0, allowed)
                            }
                        }
                    }
                    captured.toString()
                }
                Thread(outputTask, "HectorTerminalOutput").apply { isDaemon = true; start() }
                val watchdog = Thread({
                    try {
                        Thread.sleep(12000)
                        val stillRunning = try {
                            running.exitValue()
                            false
                        } catch (_: IllegalThreadStateException) {
                            true
                        }
                        if (stillRunning) {
                            timedOut.set(true)
                            terminateProcess(running)
                        }
                    } catch (_: InterruptedException) {
                        // Process completed before the deadline.
                    }
                }, "HectorTerminalTimeout").apply { isDaemon = true; start() }
                val exitCode = running.waitFor()
                watchdog.interrupt()
                val text = runCatching { outputTask.get(2, TimeUnit.SECONDS) }
                    .getOrDefault("[خروجی تا پایان مهلت خواندن آماده نشد.]")
                running.inputStream.close()
                val message = buildString {
                    append(text)
                    if (text.length >= 32768) append("\n[خروجی به ۳۲ کیلوبایت محدود شد.]")
                    if (timedOut.get()) append("\n[فرمان پس از ۱۲ ثانیه متوقف شد.]")
                    else append("\n[exit code: ${exitCode}]")
                }
                runOnUiThread {
                    if (!destroyed) {
                        appendOutput(message + "\n")
                        status.text = if (timedOut.get()) "فرمان به دلیل پایان مهلت متوقف شد." else "پایان فرمان • کد خروج: ${exitCode}"
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
        val targetPath = if (path.isBlank()) workspace.path else path
        val target = try {
            val candidate = if (File(targetPath).isAbsolute) {
                File(targetPath)
            } else {
                File(currentDirectory, targetPath)
            }
            candidate.canonicalFile
        } catch (_: Exception) {
            status.text = "مسیر پوشه معتبر نیست."
            return
        }
        if (target != workspace && !target.path.startsWith(workspace.path + File.separator)) {
            status.text = "برای جلوگیری از خروج از فضای کاری، فقط پوشه‌های داخل فضای ترمینال مجازند."
            return
        }
        if (!target.isDirectory) {
            status.text = "پوشه پیدا نشد: ${target.name}"
            return
        }
        currentDirectory = target
        appendOutput("$ cd ${path.ifBlank { "~" }}\n")
        appendOutput("${currentDirectory.path}\n")
        status.text = "مسیر فعلی: ${currentDirectory.name}"
    }

    private fun stopCommand() {
        val process = activeProcess
        if (process == null) {
            status.text = "فرمانی در حال اجرا نیست."
        } else {
            terminateProcess(process)
            status.text = "درخواست توقف برای فرمان ارسال شد."
        }
    }

    private fun terminateProcess(process: Process) {
        runCatching { process.destroy() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun appendOutput(value: String) {
        if (destroyed) return
        output.append(value)
        val max = 60000
        if (output.text.length > max) output.text = output.text.takeLast(max)
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
