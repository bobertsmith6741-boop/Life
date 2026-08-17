package com.mockpilot.xposed.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mockpilot.xposed.ScopeStore

/**
 * Scope picker: choose which installed apps get the mock-hiding hooks. Built with plain views so the
 * Xposed module stays dependency-light. The selection is stored where the hooks (via
 * XSharedPreferences) can read it. This complements LSPosed's own scope UI — either can gate the
 * hooks; here it also drives the per-app targeting inside the module.
 */
class ScopeActivity : Activity() {

    private val checkBoxes = mutableListOf<Pair<String, CheckBox>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(header("MockPilot Guard"))
        root.addView(body(
            "Select the apps that should see MockPilot's location as genuine. Also enable this " +
                "module and scope in LSPosed. For authorized testing on your own device.",
        ))

        val filter = EditText(this).apply { hint = "Filter apps…" }
        root.addView(filter)

        val logButton = Button(this).apply {
            text = "View detection log"
            setOnClickListener { startActivity(Intent(this@ScopeActivity, DetectionLogActivity::class.java)) }
        }
        root.addView(logButton)

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            addView(listContainer)
        }

        val selected = ScopeStore.loadScope(this)
        val apps = loadApps()
        apps.forEach { (label, pkg) ->
            val cb = CheckBox(this).apply {
                text = "$label\n$pkg"
                isChecked = pkg in selected
            }
            checkBoxes.add(pkg to cb)
            listContainer.addView(cb)
        }

        filter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val q = s?.toString()?.lowercase().orEmpty()
                checkBoxes.forEach { (pkg, cb) ->
                    cb.visibility = if (q.isBlank() || cb.text.toString().lowercase().contains(q)) View.VISIBLE else View.GONE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val save = Button(this).apply {
            text = "Save scope"
            setOnClickListener {
                val picked = checkBoxes.filter { it.second.isChecked }.map { it.first }.toSet()
                ScopeStore.saveScope(this@ScopeActivity, picked)
                Toast.makeText(this@ScopeActivity, "Saved ${picked.size} app(s)", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(scroll)
        root.addView(save)
        setContentView(root)
    }

    private fun loadApps(): List<Pair<String, String>> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            // Prefer launchable / third-party apps first, but include all so system trackers can be scoped.
            .map { info: ApplicationInfo -> (pm.getApplicationLabel(info).toString()) to info.packageName }
            .sortedBy { it.first.lowercase() }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 22f
        setPadding(0, 0, 0, dp(8))
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, 0, 0, dp(12))
        gravity = Gravity.START
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
