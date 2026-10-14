package com.example.hector

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this)
        message.text = "هیکتور فعال است"
        message.textSize = 24f
        message.gravity = android.view.Gravity.CENTER

        setContentView(message)
    }
}
