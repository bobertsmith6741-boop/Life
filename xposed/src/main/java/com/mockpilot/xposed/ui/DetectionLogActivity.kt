package com.mockpilot.xposed.ui

import android.app.Activity
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mockpilot.xposed.ScopeStore

/**
 * Shows the log of mock-related calls the hooked apps made (which app, which method, when) so you
 * can see exactly what each target is checking. Populated by [com.mockpilot.xposed.DetectionReceiver].
 */
class DetectionLogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply { text = "Detection log"; textSize = 22f }
        val log = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            addView(log)
        }

        fun refresh() {
            val entries = ScopeStore.loadDetections(this)
            log.text = if (entries.isEmpty()) {
                "No detections recorded yet.\n\nEnable the module in LSPosed, scope a target app, " +
                    "start MockPilot, then use the target app. Calls it makes to mock-check APIs " +
                    "appear here."
            } else {
                entries.joinToString("\n") { (time, pkg, method) ->
                    val stamp = DateFormat.format("MM-dd HH:mm:ss", time)
                    "$stamp  $pkg\n    $method"
                }
            }
        }

        val clear = Button(this).apply {
            text = "Clear"
            setOnClickListener { ScopeStore.clearDetections(this@DetectionLogActivity); refresh() }
        }
        val refreshBtn = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }

        root.addView(title)
        root.addView(refreshBtn)
        root.addView(clear)
        root.addView(scroll)
        setContentView(root)
        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
