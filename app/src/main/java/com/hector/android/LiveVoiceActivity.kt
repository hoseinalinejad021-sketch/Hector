package com.hector.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
    private lateinit var webView: WebView

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
            pageUri.encodedPath !in setOf("/live", "/live/") ||
            pageUri.encodedQuery != null ||
            pageUri.encodedFragment != null
        ) {
            Toast.makeText(this, "نشانی امن دروازهٔ GPT-Live معتبر نیست.", Toast.LENGTH_LONG).show()
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
            text = "بازگشت به هیکتور"
            setOnClickListener { finish() }
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
                return !isAllowedNavigation(request.url)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val audioOnly = request.resources.isNotEmpty() &&
                        request.resources.all { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (isFinishing || isDestroyed ||
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
        webView.loadUrl(pageUri.toString())
    }

    private fun isAllowedOrigin(uri: Uri): Boolean =
        normalizedHttpsOrigin(uri) != null && normalizedHttpsOrigin(uri) == allowedOrigin

    private fun isAllowedNavigation(uri: Uri): Boolean {
        if (isAllowedOrigin(uri)) return uri.encodedPath in setOf("/live", "/live/")
        return false
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
