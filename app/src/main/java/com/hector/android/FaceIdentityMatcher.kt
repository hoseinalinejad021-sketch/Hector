package com.hector.android

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Local MobileFaceNet inference adapter.
 *
 * Expects a separately reviewed model at assets/mobilefacenet.tflite:
 * input [1,112,112,3] FLOAT32 RGB normalized to [-1,1], output [1,128] FLOAT32.
 * This class intentionally does not enroll a face or unlock anything. A matching
 * threshold must be calibrated on-device and the embedding must be stored securely
 * before it can be used as one gate in an authorization state machine.
 */
class FaceIdentityMatcher(context: Context) : Closeable {
    private val interpreter: Interpreter

    init {
        val model = context.assets.open(MODEL_ASSET).use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        require(input.shape().contentEquals(intArrayOf(1, 112, 112, 3))) {
            "Unexpected face model input shape"
        }
        require(output.shape().contentEquals(intArrayOf(1, EMBEDDING_SIZE))) {
            "Unexpected face model output shape"
        }
        require(input.dataType() == org.tensorflow.lite.DataType.FLOAT32 &&
            output.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
            "This adapter accepts FLOAT32 MobileFaceNet models only"
        }
    }

    /** pixels must be 112x112 RGB values in [0,255], already face-cropped and aligned. */
    @Synchronized
    fun embed(pixels: IntArray): FloatArray {
        check(pixels.size == INPUT_SIZE * INPUT_SIZE) { "Expected a 112x112 RGB crop" }
        val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        pixels.forEach { pixel ->
            input.putFloat((((pixel shr 16) and 0xff) / 127.5f) - 1f)
            input.putFloat((((pixel shr 8) and 0xff) / 127.5f) - 1f)
            input.putFloat(((pixel and 0xff) / 127.5f) - 1f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interpreter.run(input, output)
        return normalize(output[0])
    }

    /** Compares embeddings only; caller must not treat this as calibrated authentication. */
    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        require(first.size == EMBEDDING_SIZE && second.size == EMBEDDING_SIZE)
        var dot = 0.0
        var a2 = 0.0
        var b2 = 0.0
        for (i in first.indices) {
            require(first[i].isFinite() && second[i].isFinite()) { "Invalid embedding" }
            dot += first[i].toDouble() * second[i].toDouble()
            a2 += first[i].toDouble() * first[i].toDouble()
            b2 += second[i].toDouble() * second[i].toDouble()
        }
        if (a2 <= 1e-12 || b2 <= 1e-12) return -1f
        return (dot / (sqrt(a2) * sqrt(b2))).toFloat().coerceIn(-1f, 1f)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        require(vector.size == EMBEDDING_SIZE && vector.all { it.isFinite() }) {
            "Model returned invalid embedding"
        }
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        require(norm > 1e-9 && norm.isFinite()) { "Model returned empty embedding" }
        return FloatArray(vector.size) { (vector[it] / norm).coerceIn(-1f, 1f) }
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "mobilefacenet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 128
    }
}
