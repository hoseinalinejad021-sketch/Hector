package com.hector.android

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager

/**
 * Local-only speaker embedding engine.
 *
 * This class computes embeddings and supports an in-memory verification manager.
 * It deliberately does not capture audio, persist enrollment, or authorize the app.
 * Callers must provide reviewed live-capture, replay/liveness checks, secure enrollment,
 * and a fail-closed authorization state machine before using it for access control.
 */
class SpeakerModelEngine(context: Context) : AutoCloseable {
    private val extractor: SpeakerEmbeddingExtractor
    private val manager: SpeakerEmbeddingManager
    private var closed = false

    init {
        val config = SpeakerEmbeddingExtractorConfig(
            model = MODEL_ASSET,
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        extractor = SpeakerEmbeddingExtractor(
            assetManager = context.applicationContext.assets,
            config = config
        )
        manager = SpeakerEmbeddingManager(extractor.dim())
        check(extractor.dim() > 0) { "Speaker model returned an invalid embedding dimension" }
    }

    /** Produces an embedding from mono Float32 PCM at 16 kHz. Minimum duration: 1 second. */
    @Synchronized
    fun computeEmbedding(samples: FloatArray, sampleRate: Int = SAMPLE_RATE): FloatArray {
        check(!closed) { "Speaker model engine is closed" }
        require(sampleRate == SAMPLE_RATE) { "Audio must be resampled to 16 kHz before inference" }
        require(samples.size >= SAMPLE_RATE) { "At least 1 second of audio is required" }
        require(samples.all { it.isFinite() && it in -1.0f..1.0f }) {
            "Audio samples must be finite normalized Float32 PCM"
        }

        val stream: OnlineStream = extractor.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            check(extractor.isReady(stream)) { "Speaker model could not process this audio" }
            extractor.compute(stream).also {
                check(it.isNotEmpty() && it.all(Float::isFinite)) {
                    "Speaker model returned an invalid embedding"
                }
            }
        } finally {
            stream.release()
        }
    }

    /** Registers multiple enrollment utterances in RAM only; does not persist identity. */
    @Synchronized
    fun enrollInMemory(ownerId: String, embeddings: Array<FloatArray>): Boolean {
        check(!closed) { "Speaker model engine is closed" }
        require(ownerId.isNotBlank())
        require(embeddings.size >= 3) { "At least three separate enrollment samples are required" }
        require(embeddings.all { it.size == extractor.dim() && it.all(Float::isFinite) }) {
            "Enrollment embedding dimensions are invalid"
        }
        return manager.add(ownerId, embeddings)
    }

    /** Returns false on mismatch; the caller must also require liveness and other gates. */
    @Synchronized
    fun verifyInMemory(ownerId: String, embedding: FloatArray, threshold: Float): Boolean {
        check(!closed) { "Speaker model engine is closed" }
        require(threshold in 0.0f..1.0f)
        if (!manager.contains(ownerId) || embedding.size != extractor.dim()) return false
        return manager.verify(ownerId, embedding, threshold)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        manager.release()
        extractor.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MODEL_ASSET = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
    }
}
