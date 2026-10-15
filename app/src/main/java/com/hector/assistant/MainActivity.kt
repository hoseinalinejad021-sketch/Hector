package com.hector.assistant

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hector prototype with a daily refreshed public-reference layer.
 * This is not a trained AI model. Sensitive operations remain disabled until
 * owner-specific voice verification is implemented and tested.
 */
class MainActivity : Activity() {
    private lateinit var knowledgeView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
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
                setPadding(0, 10, 0, 10)
            })
        }

        addLine("HECTOR", 30f, true)
        addLine("Personal Assistant — Prototype", 18f)
        addLine("Emergency stop: ENGAGED", 20f, true)
        addLine("Owner-specific voice verification: NOT IMPLEMENTED", 16f)
        addLine(
            "Sensitive operations remain disabled until security checks are implemented and tested.",
            15f
        )
        addLine(
            "Daily public knowledge: refreshed from Wikimedia/Wikipedia. This is a reference layer, not a self-training AI model.",
            14f
        )

        root.addView(Button(this).apply {
            text = "Load daily knowledge"
            setOnClickListener { loadDailyKnowledge() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        knowledgeView = TextView(this).apply {
            text = "Press the button to load the latest published daily index."
            textSize = 15f
            setPadding(4, 16, 4, 16)
        }
        root.addView(knowledgeView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        addLine(
            "This prototype does not connect to a general AI model and does not authorize financial or account actions.",
            13f
        )
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun loadDailyKnowledge() {
        knowledgeView.text = "Loading daily public knowledge over HTTPS..."
        Thread {
            val text = try {
                val connection = (URL(
                    "https://raw.githubusercontent.com/hoseinalinejad021-sketch/Hector/main/knowledge/daily.json"
                ).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "HectorAndroid/0.5")
                }
                try {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items")
                    val output = StringBuilder()
                    output.append("Index date (UTC): ")
                        .append(json.optString("date_utc", "not available"))
                        .append("\nUpdated: ")
                        .append(json.optString("updated_utc", "not refreshed yet"))
                        .append("\nSource: ")
                        .append(json.optString("source", "unknown"))
                        .append("\n\n")
                    if (items == null || items.length() == 0) {
                        output.append("The daily index is empty or has not been refreshed yet. Try again later.")
                    } else {
                        for (i in 0 until minOf(items.length(), 8)) {
                            val item = items.getJSONObject(i)
                            output.append(i + 1).append(". ")
                                .append(item.optString("title", "Untitled"))
                                .append("\n")
                            val summary = item.optString("summary", "")
                            if (summary.isNotBlank()) output.append(summary).append("\n")
                            output.append("Type: ").append(item.optString("kind", "reference"))
                                .append("\n")
                            val sourceUrl = item.optString("url", "")
                            if (sourceUrl.isNotBlank()) output.append(sourceUrl).append("\n")
                            output.append("\n")
                        }
                    }
                    output.toString()
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                "Could not load the daily index. Check the internet connection and try again. No private phone data is sent."
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) knowledgeView.text = text }
        }.start()
    }
}
