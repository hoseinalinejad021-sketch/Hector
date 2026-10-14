package com.example.hector

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 64, 32, 32)
        layout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "HECTOR"
        title.textSize = 32f
        title.setTextColor(Color.BLACK)

        val status = TextView(this)
        status.text = "هیکتور آماده راه‌اندازی است"
        status.textSize = 20f
        status.setTextColor(Color.DKGRAY)

        layout.addView(title)
        layout.addView(status)

        setContentView(layout)
    }
}
