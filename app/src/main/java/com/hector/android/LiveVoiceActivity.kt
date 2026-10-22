package com.hector.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class LiveVoiceActivity : ComponentActivity() {
    private var pendingPermissionRequest: PermissionRequest? = null
    private var allowedOrigin: String? = null
    private var stopReceiverRegistered = false
    private lateinit var webView: WebView

    private val stopBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (::webView.isInitialized) {
                webView.evaluateJavascript("window.hectorStop && window.hectorStop()", null)
            }
            finish()
        }
    }

    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (isFinishing || isDestroyed) {
                safelyDeny(request)
                return@registerForActivityResult
            }

            if (granted && request != null &&
                isAllowedOrigin(request.origin) &&
                request.resources.contentEquals(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            ) {
                try {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } catch (_: IllegalStateException) {
                    // WebView may cancel a permission request while Android's dialog is open.
                }
            } else {
                safelyDeny(request)
                Toast.makeText(
                    this,
                    "برای مکالمهٔ زنده، مجوز میکروفون و مبدأ معتبر لازم است.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val pageUri = Uri.parse(rawUrl)
        val origin = normalizedHttpsOrigin(pageUri)
        if (origin == null ||
            !LiveGatewayUrlValidator.isValid(rawUrl) ||
            pageUri.encodedPath != "/live" ||
            pageUri.encodedQuery != null ||
            pageUri.encodedFragment != null
        ) {
            Toast.makeText(this, "نشانی امن دروازهٔ GPT-Live معتبر نیست.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (getSharedPreferences("hector_private", MODE_PRIVATE).getBoolean("emergency_stop_engaged", false)) {
            Toast.makeText(this, "توقف اضطراری فعال است؛ GPT-Live باز نشد.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        allowedOrigin = origin

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }
        layout.addView(TextView(this).apply {
            text = "هیکتور — مکالمهٔ صوتی زنده\nکلید اصلی API در سرور نگهداری می‌شود."
            textSize = 16f
            setPadding(8, 8, 8, 8)
        })
        layout.addView(Button(this).apply {
            text = "بازگشت به هیکتور و پایان صدا"
            setOnClickListener {
                stopService(Intent(this@LiveVoiceActivity, HectorVoiceForegroundService::class.java))
                finish()
            }
        })
        layout.addView(Button(this).apply {
            text = "توقف اضطراری HIC-STOP-01"
            setOnClickListener { engageEmergencyStop() }
        })

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Only the configured HTTPS /live page may remain in this WebView.
                if (isAllowedNavigation(request.url)) return false
                if (request.isForMainFrame) {
                    stopService(Intent(this@LiveVoiceActivity, HectorVoiceForegroundService::class.java))
                    Toast.makeText(this@LiveVoiceActivity, "مسیر خارج از دروازهٔ GPT-Live مسدود شد.", Toast.LENGTH_LONG).show()
                    finish()
                }
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !isFinishing) {
                    stopService(Intent(this@LiveVoiceActivity, HectorVoiceForegroundService::class.java))
                    Toast.makeText(this@LiveVoiceActivity, "دروازهٔ GPT-Live بارگذاری نشد؛ نشانی و انتشار Worker را بررسی کن.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 400 && !isFinishing) {
                    stopService(Intent(this@LiveVoiceActivity, HectorVoiceForegroundService::class.java))
                    Toast.makeText(this@LiveVoiceActivity, "دروازهٔ GPT-Live پاسخ HTTP ${errorResponse.statusCode} داد.", Toast.LENGTH_LONG).show()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val audioOnly = request.resources.isNotEmpty() &&
                        request.resources.all { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (isFinishing || isDestroyed ||
                        getSharedPreferences("hector_private", MODE_PRIVATE).getBoolean("emergency_stop_engaged", false) ||
                        !isAllowedOrigin(request.origin) || !audioOnly
                    ) {
                        safelyDeny(request)
                        return@runOnUiThread
                    }

                    if (ContextCompat.checkSelfPermission(
                            this@LiveVoiceActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } catch (_: IllegalStateException) {
                            // Ignore a request WebView has already cancelled.
                        }
                    } else {
                        pendingPermissionRequest?.let { previous ->
                            if (previous !== request) safelyDeny(previous)
                        }
                        pendingPermissionRequest = request
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermissionRequest === request) pendingPermissionRequest = null
            }
        }

        layout.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(layout)
        ContextCompat.registerReceiver(
            this,
            stopBroadcastReceiver,
            IntentFilter(HectorVoiceForegroundService.ACTION_STOP_LIVE_VOICE_BROADCAST),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        stopReceiverRegistered = true
        webView.loadUrl(pageUri.toString())
    }

    private fun engageEmergencyStop() {
        // Stop the live page and any native audio service before returning to the app.
        val persisted = getSharedPreferences("hector_private", MODE_PRIVATE)
            .edit()
            .putBoolean("emergency_stop_engaged", true)
            .commit()
        stopService(android.content.Intent(this, HectorVoiceForegroundService::class.java))
        if (!persisted) {
            Toast.makeText(
                this,
                "ذخیرهٔ توقف اضطراری ناموفق بود؛ سرویس صوتی متوقف شد. فضای ذخیره‌سازی را بررسی کن.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "توقف اضطراری فعال شد؛ GPT-Live متوقف شد.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun isAllowedOrigin(uri: Uri): Boolean =
        normalizedHttpsOrigin(uri) != null && normalizedHttpsOrigin(uri) == allowedOrigin

    private fun isAllowedNavigation(uri: Uri): Boolean {
        if (!isAllowedOrigin(uri)) return false
        return uri.encodedPath == "/live" &&
            uri.encodedQuery == null &&
            uri.encodedFragment == null
    }

    private fun normalizedHttpsOrigin(uri: Uri): String? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT) ?: return null
        val port = uri.port
        if (port != -1 && port !in 1..65535) return null
        return "https://$host" + if (port == -1 || port == 443) "" else ":$port"
    }

    private fun safelyDeny(request: PermissionRequest?) {
        try {
            request?.deny()
        } catch (_: IllegalStateException) {
            // WebView may cancel the request before the callback runs.
        }
    }

    override fun onDestroy() {
        if (stopReceiverRegistered) {
            try {
                unregisterReceiver(stopBroadcastReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver may already have been unregistered during activity teardown.
            }
            stopReceiverRegistered = false
        }
        pendingPermissionRequest?.let(::safelyDeny)
        pendingPermissionRequest = null
        allowedOrigin = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "com.hector.android.GPT_LIVE_URL"
    }
}
