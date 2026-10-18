package com.hector.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class LiveVoiceActivity : FragmentActivity() {
    private var pendingPermissionRequest: PermissionRequest? = null
    private lateinit var webView: WebView
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (granted && request != null &&
                request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request?.deny()
                Toast.makeText(this, "برای مکالمهٔ زنده، مجوز میکروفون لازم است.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!url.startsWith("https://") || url.contains("@")) {
            Toast.makeText(this, "نشانی امن دروازهٔ GPT-Live معتبر نیست.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
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
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = false
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.origin.toString().startsWith(url.substringBefore("/live") .trimEnd('/'))) {
                        if (ContextCompat.checkSelfPermission(this@LiveVoiceActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingPermissionRequest = request
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        request.deny()
                    }
                }
            }
        }
        layout.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(layout)
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        pendingPermissionRequest = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "com.hector.android.GPT_LIVE_URL"
    }
}
