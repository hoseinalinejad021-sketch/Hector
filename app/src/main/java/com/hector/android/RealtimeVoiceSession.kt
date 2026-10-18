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
import android.util.Base64
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal full-duplex OpenAI Realtime WebSocket client.
 * The user-supplied API key is read from Hector's encrypted local store and is never compiled into the APK.
 */
class RealtimeVoiceSession(
    private val context: Context,
    private val apiKey: String,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    private val stopped = AtomicBoolean(false)
    private val socketReady = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureThread: Thread? = null

    fun start() {
        if (apiKey.isBlank()) {
            onStatus("ابتدا کلید API را در تنظیمات اتصال هوش مصنوعی ذخیره کن.")
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
            .header("OpenAI-Beta", "realtime=v1")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (stopped.get()) { webSocket.close(1000, "closed"); return }
                socketReady.set(true)
                val session = JSONObject()
                    .put("type", "realtime")
                    .put("model", "gpt-realtime")
                    .put("instructions", "You are Hector, a helpful personal assistant. Speak Persian (Farsi) by default unless the user asks for another language.")
                    .put("audio", JSONObject()
                        .put("input", JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24000))
                            .put("turn_detection", JSONObject().put("type", "server_vad"))
                        )
                        .put("output", JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm"))
                            .put("voice", "marin")
                        )
                    )
                webSocket.send(JSONObject().put("type", "session.update").put("session", session).toString())
                startAudioCapture()
                onStatus("GPT-Live وصل شد؛ صحبت کن. برای پایان، «پایان گفت‌وگوی زنده» را بزن.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (stopped.get()) return
                try {
                    val event = JSONObject(text)
                    when (event.optString("type")) {
                        "response.output_audio.delta", "response.audio.delta" -> {
                            val encoded = event.optString("delta")
                            if (encoded.isNotEmpty()) {
                                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                synchronized(this@RealtimeVoiceSession) {
                                    player?.write(bytes, 0, bytes.size)
                                }
                            }
                        }
                        "error" -> {
                            val error = event.optJSONObject("error")
                            onStatus("خطای GPT-Live: " + (error?.optString("message") ?: "خطای نامشخص").take(220))
                        }
                        "session.created", "session.updated" -> { /* connection is ready */ }
                    }
                } catch (e: Exception) {
                    if (!stopped.get()) onStatus("دادهٔ صوتی قابل پردازش نبود: ${e.message ?: "خطای نامشخص"}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketReady.set(false)
                if (!stopped.get()) onStatus(
                    when (response?.code) {
                        401 -> "کلید API نامعتبر یا لغوشده است (401). کلید را در تنظیمات بررسی کن."
                        403 -> "حساب به GPT-Live دسترسی ندارد (403). مجوز و وضعیت پرداخت API را بررسی کن."
                        429 -> "سهمیه یا محدودیت درخواست GPT-Live (429). مصرف و اعتبار API را بررسی کن."
                        else -> "اتصال GPT-Live برقرار نشد: ${t.message ?: "خطای شبکه"}"
                    }
                )
                stopAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketReady.set(false)
                if (!stopped.get()) onStatus("اتصال GPT-Live پایان یافت.")
                stopAudio()
            }
        })
    }

    private fun startAudioCapture() {
        val minBuffer = AudioRecord.getMinBufferSize(
            24000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onStatus("میکروفون از قالب صوتی موردنیاز GPT-Live پشتیبانی نکرد.")
            close()
            return
        }
        val bufferSize = maxOf(minBuffer * 2, 4800)
        val input = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            24000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (input.state != AudioRecord.STATE_INITIALIZED) {
            input.release()
            onStatus("میکروفون آماده نشد.")
            close()
            return
        }
        val output = AudioTrack.Builder()
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
        if (output.state != AudioTrack.STATE_INITIALIZED) {
            input.release()
            output.release()
            onStatus("خروجی صوتی برای GPT-Live آماده نشد.")
            close()
            return
        }
        synchronized(this) { recorder = input; player = output }
        output.play()
        input.startRecording()
        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val pcm = ByteArray(4800)
            while (!stopped.get() && socketReady.get()) {
                val count = try { input.read(pcm, 0, pcm.size) } catch (_: Exception) { -1 }
                if (count > 0 && !stopped.get()) {
                    val payload = Base64.encodeToString(pcm.copyOf(count), Base64.NO_WRAP)
                    val sent = socket?.send(JSONObject()
                        .put("type", "input_audio_buffer.append")
                        .put("audio", payload).toString()) ?: false
                    if (!sent) break
                }
            }
        }, "Hector-GPT-Live-Capture").apply { isDaemon = true; start() }
    }

    private fun stopAudio() {
        stopped.set(true)
        socketReady.set(false)
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
        if (stopped.compareAndSet(false, true)) {
            socketReady.set(false)
            socket?.close(1000, "user ended live session")
            stopAudio()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            onStatus("گفت‌وگوی زنده متوقف شد.")
        }
    }
}
