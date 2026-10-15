package com.hector.assistant

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal buildable Hector prototype.
 * Sensitive operations remain disabled until owner-specific voice verification is implemented.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fun addLine(text: String, size: Float, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                gravity = Gravity.CENTER
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 12, 0, 12)
            })
        }

        addLine("HECTOR", 30f, true)
        addLine("Personal Assistant — Prototype", 18f)
        addLine("Emergency stop: ENGAGED", 20f, true)
        addLine("Owner-specific voice verification: NOT IMPLEMENTED", 16f)
        addLine("Sensitive operations are disabled until security checks are implemented and tested.", 16f)
        addLine("This prototype does not authorize financial or account actions.", 14f)

        setContentView(root)
    }
}
