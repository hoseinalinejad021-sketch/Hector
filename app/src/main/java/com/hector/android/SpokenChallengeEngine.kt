package com.hector.android

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig

/**
 * Fully local English speech-to-text used only to validate the random spoken challenge.
 * The transcript is derived from the exact PCM buffer also used for speaker embedding.
 */
class SpokenChallengeEngine(context: Context) : AutoCloseable {
    private val recognizer: OfflineRecognizer
    @Volatile private var closed = false

    init {
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$MODEL_DIR/tiny.en-encoder.int8.onnx",
                decoder = "$MODEL_DIR/tiny.en-decoder.int8.onnx",
                language = "en",
                task = "transcribe"
            ),
            tokens = "$MODEL_DIR/tiny.en-tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "whisper"
        )
        recognizer = OfflineRecognizer(
            assetManager = context.applicationContext.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = model
            )
        )
    }

    @Synchronized
    fun transcribeSameCapture(samples: FloatArray): String {
        check(!closed) { "Speech recognizer is closed" }
        require(samples.size >= SAMPLE_RATE * MIN_SECONDS) { "Challenge recording is too short" }
        require(samples.all { it.isFinite() && it in -1.0f..1.0f }) {
            "Invalid normalized PCM"
        }
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            recognizer.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MIN_SECONDS = 1
        private const val MODEL_DIR = "sherpa-onnx-whisper-tiny.en"
    }
}
