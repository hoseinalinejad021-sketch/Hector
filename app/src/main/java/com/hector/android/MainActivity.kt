package com.hector.android

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal first-build screen for Hector.
 * Security-sensitive capabilities remain disabled until separately implemented and tested.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }

        val title = TextView(this).apply {
            text = "HECTOR"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "نسخه آزمایشی اولیه\n\nتوقف اضطراری HIC-STOP-01: فعال\n\nقابلیت‌های حساس تا زمان پیاده‌سازی و آزمون هویت، غیرفعال می‌مانند."
            textSize = 16f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(status)
        setContentView(root)
    }
}
