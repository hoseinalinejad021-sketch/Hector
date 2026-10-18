package com.hector.android

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import java.util.concurrent.TimeUnit

/**
 * Offline 1:1 face identity matcher. The embedding model must be supplied as
 * assets/mobilefacenet.tflite by the CI model-fetch step. Face templates are
 * encrypted by SecureMemoryStore (Android Keystore/AES-GCM).
 *
 * This is an additional owner-face check, not a substitute for Android biometric
 * authentication or a validated anti-spoofing system.
 */
internal class FaceIdentityEngine(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val store = SecureMemoryStore(appContext)
    private var interpreter: Interpreter? = null
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
    )

    @Synchronized
    private fun model(): Interpreter {
        interpreter?.let { return it }
        val modelBytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        buffer.rewind()
        val created = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
        require(created.inputTensor(0).shape().contentEquals(intArrayOf(1, 112, 112, 3))) {
            "Face model input must be [1,112,112,3]"
        }
        require(created.inputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32) {
            "Face model input must be FLOAT32"
        }
        require(created.getOutputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32) {
            "Face model output must be FLOAT32"
        }
        interpreter = created
        return created
    }

    fun hasOwnerTemplate(): Boolean = store.read(TEMPLATE_KEY)?.let {
        it.fill(0)
        true
    } ?: false

    /** Explicitly enroll the currently authenticated owner after biometric login. */
    @Synchronized
    fun enroll(preview: Bitmap): Boolean {
        val vector = embedding(preview)
        return try {
            val bytes = ByteBuffer.allocate(4 + vector.size * 4).order(ByteOrder.BIG_ENDIAN)
                .putInt(vector.size).apply { vector.forEach { putFloat(it) } }.array()
            try { store.write(TEMPLATE_KEY, bytes) } finally { bytes.fill(0) }
            true
        } finally {
            vector.fill(0)
        }
    }

    @Synchronized
    fun matchesOwner(preview: Bitmap): Boolean {
        val packed = store.read(TEMPLATE_KEY) ?: return false
        val saved = try {
            if (packed.size < 8) return false
            val input = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
            val size = input.int
            if (size <= 0 || packed.size != 4 + size * 4) return false
            FloatArray(size) { input.float }
        } finally {
            packed.fill(0)
        }
        val current = embedding(preview)
        return try {
            if (current.size != saved.size) return false
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in current.indices) {
                dot += current[i] * saved[i]
                normA += current[i] * current[i]
                normB += saved[i] * saved[i]
            }
            if (normA <= 0.0 || normB <= 0.0) false
            else dot / (sqrt(normA) * sqrt(normB)) >= COSINE_THRESHOLD
        } finally {
            current.fill(0)
            saved.fill(0)
        }
    }

    private fun embedding(source: Bitmap): FloatArray {
        require(source.width > 0 && source.height > 0)
        val faces = Tasks.await(
            faceDetector.process(InputImage.fromBitmap(source, 0)),
            5,
            TimeUnit.SECONDS
        )
        require(faces.size == 1) { "Exactly one face must be visible for identity verification" }
        val bounds = faces.single().boundingBox
        val padding = (maxOf(bounds.width(), bounds.height()) * 0.18f).toInt()
        val side = maxOf(bounds.width(), bounds.height()) + padding * 2
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val left = (centerX - side / 2).coerceIn(0, source.width - 1)
        val top = (centerY - side / 2).coerceIn(0, source.height - 1)
        val safeSide = minOf(side, source.width - left, source.height - top)
        require(safeSide > 0) { "Face crop is invalid" }
        val crop = Bitmap.createBitmap(source, left, top, safeSide, safeSide)
        val scaled = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
        crop.recycle()
        val input = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = scaled.getPixel(x, y)
                input.putFloat((((pixel shr 16) and 0xff) - 127.5f) / 127.5f)
                input.putFloat((((pixel shr 8) and 0xff) - 127.5f) / 127.5f)
                input.putFloat(((pixel and 0xff) - 127.5f) / 127.5f)
            }
        }
        scaled.recycle()
        input.rewind()
        val outputShape = model().getOutputTensor(0).shape()
        val dimension = outputShape.last()
        require(dimension > 0) { "Invalid face embedding output" }
        val output = Array(1) { FloatArray(dimension) }
        model().run(input, output)
        val result = output[0]
        var norm = 0.0
        result.forEach { norm += it * it }
        norm = sqrt(norm)
        require(norm > 0.0) { "Empty face embedding" }
        for (i in result.indices) result[i] = (result[i] / norm).toFloat()
        return result
    }

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
        faceDetector.close()
    }

    companion object {
        private const val MODEL_ASSET = "mobilefacenet.tflite"
        private const val TEMPLATE_KEY = "owner_face_embedding_v1"
        private const val INPUT_SIZE = 112
        // Conservative starting threshold; must be calibrated on supported device/model pairs.
        private const val COSINE_THRESHOLD = 0.55
    }
}
