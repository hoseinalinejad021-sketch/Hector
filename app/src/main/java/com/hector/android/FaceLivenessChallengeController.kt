package com.hector.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera face-presence detector for the second login step.
 * Returns a bitmap made from the actual CameraX ImageProxy frame; PreviewView.bitmap
 * is not reliable because its surface may not be backed by a readable bitmap.
 * This checks face presence only; it is not a liveness/anti-spoofing system.
 */
internal class FaceLivenessChallengeController(
    private val activity: FragmentActivity,
    private val previewView: PreviewView,
    private val statusView: TextView,
    private val onResult: (Boolean, String, Bitmap?) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val processing = AtomicBoolean(false)
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )
    @Volatile private var finished = false
    @Volatile private var resourcesClosed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun start() {
        statusView.text = "صورتت را روبه‌روی دوربین جلو نگه دار تا با الگوی مالک مقایسه شود."
        handler.postDelayed({
            finish(false, "زمان بررسی چهره تمام شد؛ ورود لغو شد.")
        }, 25_000L)

        try {
            val providerFuture = ProcessCameraProvider.getInstance(activity)
            providerFuture.addListener({
                if (finished) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(cameraExecutor, this)
                    imageAnalysis = analysis
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        activity,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                    finish(false, "دوربین جلو آماده نشد؛ ورود برای امنیت لغو شد.")
                }
            }, mainExecutor)
        } catch (_: Exception) {
            finish(false, "دسترسی به دوربین برقرار نشد؛ ورود لغو شد.")
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (finished || !processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            processing.set(false)
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener(mainExecutor) { faces -> processFaces(faces, imageProxy) }
            .addOnFailureListener(mainExecutor) {
                // Ignore a transient frame error and allow the next frame to be analyzed.
            }
            .addOnCompleteListener(mainExecutor) {
                imageProxy.close()
                processing.set(false)
            }
    }

    private fun processFaces(faces: List<Face>, imageProxy: ImageProxy) {
        if (finished) return
        if (faces.size > 1) {
            finish(false, "بیش از یک چهره دیده شد؛ ورود لغو شد.")
            return
        }
        if (faces.size != 1) return

        statusView.text = "چهره پیدا شد؛ تصویر دوربین آماده می‌شود…"
        val bitmap = try {
            imageProxyToBitmap(imageProxy)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            finish(false, "فریم واقعی دوربین خوانده نشد؛ دوباره تلاش کن.")
            return
        }
        finish(true, "فریم دوربین دریافت شد.", bitmap)
    }

    /** Convert YUV_420_888 respecting each plane's row/pixel stride, then rotate to upright. */
    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        if (proxy.format != ImageFormat.YUV_420_888 || proxy.planes.size < 3) return null
        val width = proxy.width
        val height = proxy.height
        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]
        val nv21 = ByteArray(width * height + width * height / 2)
        var out = 0

        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                nv21[out++] = yPlane.buffer.get(rowStart + col * yPlane.pixelStride)
            }
        }
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            val uRow = row * uPlane.rowStride
            val vRow = row * vPlane.rowStride
            for (col in 0 until chromaWidth) {
                nv21[out++] = vPlane.buffer.get(vRow + col * vPlane.pixelStride)
                nv21[out++] = uPlane.buffer.get(uRow + col * uPlane.pixelStride)
            }
        }

        val jpeg = ByteArrayOutputStream()
        val success = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 90, jpeg)
        if (!success) return null
        val raw = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return null
        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    private fun finish(success: Boolean, message: String, bitmap: Bitmap? = null) {
        if (finished) {
            bitmap?.recycle()
            return
        }
        finished = true
        handler.removeCallbacksAndMessages(null)
        try {
            onResult(success, message, bitmap)
        } catch (_: Exception) {
            bitmap?.recycle()
        } finally {
            closeResources()
        }
    }

    override fun close() {
        if (finished && resourcesClosed) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        closeResources()
    }

    private fun closeResources() {
        if (resourcesClosed) return
        resourcesClosed = true
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        cameraProvider = null
        try {
            detector.close()
        } catch (_: Exception) {
        }
        cameraExecutor.shutdownNow()
    }
}
