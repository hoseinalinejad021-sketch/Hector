package com.hector.android

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.random.Random

/**
 * Challenge-response check for a live face in front of the front camera.
 *
 * This deliberately does NOT claim identity matching. Android's enrolled device
 * biometric is the identity check; this class checks face presence and a fresh
 * head-movement challenge. It must not be used alone to authorize sensitive actions.
 */
internal class FaceLivenessChallengeController(
    private val activity: FragmentActivity,
    private val previewView: PreviewView,
    private val statusView: TextView,
    private val onResult: (Boolean, String) -> Unit
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
    private val turnThreshold = Random.nextInt(18, 27).toFloat()
    @Volatile private var finished = false
    private var resourcesClosed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var sawCenter = false
    private var sawTurn = false

    fun start() {
        statusView.text = "صورت را روبه‌روی دوربین نگه دار؛ ابتدا به مرکز نگاه کن."
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
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        detector.process(inputImage)
            .addOnSuccessListener(mainExecutor) { faces -> processFaces(faces) }
            .addOnFailureListener(mainExecutor) {
                // A transient frame failure is not proof of liveness; wait for another frame.
            }
            .addOnCompleteListener(mainExecutor) {
                imageProxy.close()
                processing.set(false)
            }
    }

    private fun processFaces(faces: List<Face>) {
        if (finished) return
        if (faces.size > 1) {
            finish(false, "بیش از یک چهره دیده شد؛ ورود لغو شد.")
            return
        }
        val face = faces.firstOrNull() ?: return
        val yaw = abs(face.headEulerAngleY)

        if (!sawCenter) {
            if (yaw <= 10f) {
                sawCenter = true
                statusView.text = "حالا سرت را آرام به یک طرف بچرخان."
            }
            return
        }
        if (!sawTurn) {
            if (yaw >= turnThreshold) {
                sawTurn = true
                statusView.text = "حرکت ثبت شد؛ حالا سرت را دوباره به مرکز برگردان."
            }
            return
        }
        if (yaw <= 10f) {
            finish(true, "حرکت زندهٔ چهره ثبت شد.")
        }
    }

    private fun finish(success: Boolean, message: String) {
        if (finished) return
        finished = true
        closeResources()
        onResult(success, message)
    }

    override fun close() {
        if (finished && resourcesClosed) return
        finished = true
        closeResources()
    }

    private fun closeResources() {
        if (resourcesClosed) return
        resourcesClosed = true
        handler.removeCallbacksAndMessages(null)
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
