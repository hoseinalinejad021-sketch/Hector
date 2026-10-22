package com.hector.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GPT-Live WebSocket client using PCM16 mono audio at 24 kHz.
 * The user enters their API key in Hector; it is not compiled into the APK.
 */
class RealtimeVoiceSession(
    private val context: Context,
    private val apiKey: String,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val stopped = AtomicBoolean(false)
    private val startupHandler = Handler(Looper.getMainLooper())
    private val startupTimeout = Runnable {
        if (!stopped.get() && !sessionStarted.get()) {
            onStatus("سرور GPT-Live در مهلت مقرر اتصال را تأیید نکرد؛ اتصال متوقف شد.")
            close()
        }
    }
    private val sessionStarted = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>(100)
    private val playbackOverflowReported = AtomicBoolean(false)

    fun start() {
        if (apiKey.isBlank()) {
            onStatus("ابتدا کلید API را در تنظیمات هوش مصنوعی ذخیره کن.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("مجوز میکروفون لازم است.")
            return
        }
        onStatus("در حال اتصال به GPT-Live…")
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=gpt-realtime")
            .header("Authorization", "Bearer $apiKey")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // The Realtime API sends session.created automatically after the WebSocket opens.
                if (stopped.get()) webSocket.close(1000, "closed")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (stopped.get()) return
                try {
                    val event = JSONObject(text)
                    when (event.optString("type")) {
                        "session.created" -> {
                            val session = JSONObject()
                                .put("type", "realtime")
                                .put("model", "gpt-realtime")
                                .put("instructions", "You are Hector, a helpful personal assistant. Speak Persian (Farsi) by default unless the user asks for another language. Keep spoken answers clear and concise.")
                                .put("output_modalities", org.json.JSONArray().put("audio"))
                                .put("audio", JSONObject()
                                    .put("input", JSONObject()
                                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))
                                        .put("turn_detection", JSONObject().put("type", "server_vad"))
                                        .put("transcription", JSONObject().put("model", "gpt-4o-mini-transcribe"))
                                    )
                                    .put("output", JSONObject()
                                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))
                                        .put("voice", "marin")
                                    )
                                )
                            if (!webSocket.send(JSONObject()
                                    .put("type", "session.update")
                                    .put("event_id", "hector_session_update")
                                    .put("session", session).toString())) {
                                onStatus("پیکربندی جلسهٔ صوتی ارسال نشد.")
                                close()
                            }
                        }
                        "session.updated" -> {
                            startupHandler.removeCallbacks(startupTimeout)
                            sessionStarted.set(true)
                            startAudioCapture()
                            onStatus("GPT-Live وصل شد؛ صحبت کن. برای پایان، دکمهٔ پایان را بزن.")
                        }
                        "response.output_audio.delta" -> {
                            val encoded = event.optString("delta")
                            if (encoded.isNotEmpty()) {
                                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                if (!playbackQueue.offer(bytes) && !stopped.get() &&
                                    playbackOverflowReported.compareAndSet(false, true)) {
                                    onStatus("بافر صدای خروجی پر شد؛ ممکن است بخشی از صدا از دست برود.")
                                }
                            }
                        }
                        "conversation.item.input_audio_transcription.delta" -> {
                            val delta = event.optString("delta")
                            if (delta.isNotBlank()) onStatus("شنیدم: " + delta.take(160))
                        }
                        "response.output_audio_transcript.delta" -> {
                            val delta = event.optString("delta")
                            if (delta.isNotBlank()) onStatus("هیکتور: " + delta.take(160))
                        }
                        "error" -> {
                            val error = event.optJSONObject("error")
                            onStatus("خطای GPT-Live: " + (error?.optString("message") ?: "خطای نامشخص").take(220))
                            stopAudio()
                        }
                    }
                } catch (e: Exception) {
                    if (!stopped.get()) onStatus("دادهٔ صوتی قابل پردازش نبود: ${e.message ?: "خطای نامشخص"}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                startupHandler.removeCallbacks(startupTimeout)
                sessionStarted.set(false)
                if (!stopped.get()) onStatus(
                    when (response?.code) {
                        401 -> "کلید API نامعتبر یا لغوشده است (401). کلید را در تنظیمات بررسی کن."
                        403 -> "حساب به GPT-Live دسترسی ندارد (403). مجوز و وضعیت API را بررسی کن."
                        429 -> "سهمیه یا محدودیت GPT-Live (429). مصرف و اعتبار API را بررسی کن."
                        else -> "اتصال GPT-Live برقرار نشد: ${t.message ?: "خطای شبکه"}"
                    }
                )
                stopAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                startupHandler.removeCallbacks(startupTimeout)
                sessionStarted.set(false)
                if (!stopped.get()) onStatus("اتصال GPT-Live پایان یافت.")
                stopAudio()
            }
        })
        startupHandler.postDelayed(startupTimeout, 20000)
    }

    private fun startAudioCapture() {
        if (stopped.get() || !sessionStarted.get()) return
        // Runtime permission can be revoked after start(); check again immediately before
        // touching AudioRecord, and handle the race where Android throws SecurityException.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("مجوز میکروفون در دسترس نیست؛ گفت‌وگوی صوتی متوقف شد.")
            close()
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            24000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onStatus("میکروفون از قالب صوتی موردنیاز GPT-Live پشتیبانی نکرد.")
            close()
            return
        }
        val bufferSize = maxOf(minBuffer * 2, 4800)
        val input = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                24000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: SecurityException) {
            onStatus("اجازهٔ میکروفون رد شد؛ گفت‌وگوی صوتی متوقف شد.")
            close()
            return
        }
        if (input.state != AudioRecord.STATE_INITIALIZED) {
            input.release()
            onStatus("میکروفون آماده نشد.")
            close()
            return
        }
        val output = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(24000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(maxOf(bufferSize, 9600))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            input.release()
            onStatus("ساخت خروجی صوتی ناموفق بود؛ صدای گوشی را بررسی کن.")
            close()
            return
        }
        if (output.state != AudioTrack.STATE_INITIALIZED) {
            input.release()
            output.release()
            onStatus("خروجی صوتی برای GPT-Live آماده نشد.")
            close()
            return
        }
        synchronized(this) { recorder = input; player = output }
        try {
            output.play()
            input.startRecording()
        } catch (_: Exception) {
            synchronized(this) {
                try { input.release() } catch (_: Exception) { }
                try { output.release() } catch (_: Exception) { }
                recorder = null
                player = null
            }
            onStatus("شروع ضبط یا پخش صوتی ناموفق بود؛ مجوز میکروفون و صدای گوشی را بررسی کن.")
            close()
            return
        }
        playbackThread = Thread({
            try {
                while (!stopped.get() && sessionStarted.get()) {
                    val bytes = playbackQueue.poll(250, TimeUnit.MILLISECONDS) ?: continue
                    var offset = 0
                    while (offset < bytes.size && !stopped.get() && sessionStarted.get()) {
                        val track = synchronized(this@RealtimeVoiceSession) { player } ?: break
                        val written = try {
                            track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                        } catch (_: Exception) { -1 }
                        if (written > 0) offset += written
                        else if (written < 0) break
                        else Thread.sleep(5)
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "Hector-GPT-Live-Playback").apply { isDaemon = true; start() }
        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val pcm = ByteArray(4800)
            while (!stopped.get() && sessionStarted.get()) {
                val count = try { input.read(pcm, 0, pcm.size) } catch (_: Exception) { -1 }
                if (count > 0 && !stopped.get() && sessionStarted.get()) {
                    // PCM16 requires complete 16-bit samples; each chunk is kept even-length.
                    val complete = count - (count % 2)
                    if (complete > 0) {
                        val payload = Base64.encodeToString(pcm.copyOf(complete), Base64.NO_WRAP)
                        val sent = socket?.send(JSONObject()
                            .put("type", "input_audio_buffer.append")
                            .put("audio", payload).toString()) == true
                        if (!sent) {
                            onStatus("ارسال صدا قطع شد؛ اتصال GPT-Live بسته می‌شود.")
                            close()
                            break
                        }
                    }
                } else if (count < 0) {
                    if (!stopped.get()) {
                        onStatus("خواندن صدا از میکروفون با خطا متوقف شد.")
                        close()
                    }
                    break
                }
            }
        }, "Hector-GPT-Live-Capture").apply { isDaemon = true; start() }
    }

    private fun stopAudio() {
        sessionStarted.set(false)
        playbackQueue.clear()
        playbackThread?.interrupt()
        playbackThread = null
        synchronized(this) {
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            try { player?.pause() } catch (_: Exception) { }
            try { player?.flush() } catch (_: Exception) { }
            try { player?.stop() } catch (_: Exception) { }
            try { player?.release() } catch (_: Exception) { }
            player = null
        }
    }

    override fun close() {
        startupHandler.removeCallbacks(startupTimeout)
        if (stopped.compareAndSet(false, true)) {
            val ws = socket
            // Closing the WebSocket ends the Realtime session; there is no session.close client event.
            stopAudio()
            ws?.close(1000, "user ended GPT-Live session")
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            onStatus("گفت‌وگوی زنده متوقف شد.")
        }
    }
}
