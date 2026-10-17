package com.hector.android

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * On-device voice-profile prototype.
 * Raw PCM is never written to disk or sent over the network. Only an averaged speaker
 * embedding is stored through SecureMemoryStore (AES-GCM / Android Keystore).
 *
 * This is NOT a production authentication factor yet: cosine threshold requires device/
 * model-specific calibration, and dynamic phrase validation, liveness and replay detection
 * are not implemented. Therefore this class must not unlock sensitive actions by itself.
 */
class VoiceIdentityFlow(context: Context) {
    private val memory = SecureMemoryStore(context.applicationContext)

    fun hasProfile(): Boolean {
        val bytes = memory.read(PROFILE_KEY) ?: return false
        return try {
            if (bytes.size < HEADER_BYTES) return false
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != FORMAT_VERSION) return false
            val dimension = buffer.int
            dimension > 0 &&
                bytes.size.toLong() == HEADER_BYTES.toLong() + dimension.toLong() * 4L &&
                (0 until dimension).all { buffer.getFloat(HEADER_BYTES + it * 4).isFinite() }
        } finally {
            bytes.fill(0)
        }
    }

    /** Captures exactly three seconds of mono PCM16 from the microphone at 16 kHz. */
    fun captureThreeSeconds(): FloatArray {
        val sampleRate = SpeakerModelEngine.SAMPLE_RATE
        val sampleCount = sampleRate * DURATION_SECONDS
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "AudioRecord is not supported on this device" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, sampleCount * 2)
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone could not be initialized"
        }

        val pcm16 = ShortArray(sampleCount)
        try {
            recorder.startRecording()
            var offset = 0
            var attempts = 0
            while (offset < sampleCount && attempts < 30) {
                val count = recorder.read(pcm16, offset, sampleCount - offset, AudioRecord.READ_BLOCKING)
                if (count < 0) throw IllegalStateException("Microphone read failed: $count")
                if (count > 0) offset += count
                attempts++
            }
            check(offset == sampleCount) { "Could not capture a complete three-second sample" }
            var energy = 0.0
            val normalized = FloatArray(sampleCount) { i ->
                val value = pcm16[i].toFloat() / 32768.0f
                energy += value * value
                value
            }
            val rms = sqrt(energy / sampleCount)
            check(rms >= MIN_RMS) {
                normalized.fill(0f)
                "Sample was too quiet; try again in a quiet room and speak normally"
            }
            return normalized
        } finally {
            try { recorder.stop() } catch (_: Exception) { }
            recorder.release()
            pcm16.fill(0)
        }
    }

    /** Persists a normalized centroid from at least three separate spoken samples. */
    fun enroll(embeddings: List<FloatArray>) {
        require(embeddings.size >= 3) { "Three samples are required" }
        val dimension = embeddings.first().size
        require(dimension > 0 && embeddings.all { it.size == dimension && it.all(Float::isFinite) }) {
            "Embedding dimensions are invalid"
        }
        val centroid = FloatArray(dimension)
        var packed: ByteArray? = null
        try {
            embeddings.forEach { vector ->
                for (i in vector.indices) centroid[i] += vector[i]
            }
            normalize(centroid)
            packed = ByteBuffer.allocate(HEADER_BYTES + centroid.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(FORMAT_VERSION)
                .putInt(centroid.size)
                .apply { centroid.forEach { putFloat(it) } }
                .array()
            memory.write(PROFILE_KEY, packed)
        } finally {
            centroid.fill(0f)
            packed?.fill(0)
        }
    }

    /** Cosine comparison is a prototype and does not prove liveness. */
    fun verify(embedding: FloatArray): Boolean {
        val packed = memory.read(PROFILE_KEY) ?: return false
        try {
            if (packed.size < HEADER_BYTES) return false
            val buffer = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != FORMAT_VERSION) return false
            val dimension = buffer.int
            if (dimension <= 0 ||
                packed.size.toLong() != HEADER_BYTES.toLong() + dimension.toLong() * 4L ||
                embedding.size != dimension
            ) return false
            val stored = FloatArray(dimension) { buffer.float }
            if (!stored.all(Float::isFinite)) {
                stored.fill(0f)
                return false
            }
            return try {
                val candidate = embedding.copyOf()
                try {
                    normalize(stored)
                    normalize(candidate)
                    var cosine = 0.0
                    for (i in stored.indices) cosine += stored[i].toDouble() * candidate[i].toDouble()
                    cosine >= COSINE_THRESHOLD
                } finally {
                    candidate.fill(0f)
                }
            } finally {
                stored.fill(0f)
            }
        } finally {
            packed.fill(0)
        }
    }

    private fun normalize(vector: FloatArray) {
        var normSquared = 0.0
        vector.forEach { normSquared += it.toDouble() * it.toDouble() }
        val norm = sqrt(normSquared)
        require(norm > 1e-9 && norm.isFinite()) { "Invalid speaker embedding" }
        val normFloat = norm.toFloat()
        require(normFloat.isFinite() && normFloat > 0f) { "Invalid speaker embedding norm" }
        for (i in vector.indices) vector[i] = (vector[i] / normFloat).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        private const val PROFILE_KEY = "owner_voice_profile_v1"
        private const val FORMAT_VERSION = 1
        private const val HEADER_BYTES = 8
        private const val DURATION_SECONDS = 3
        private const val MIN_RMS = 0.008
        // Initial prototype threshold only. Must be calibrated against false accept/reject rates.
        private const val COSINE_THRESHOLD = 0.72
    }
}
