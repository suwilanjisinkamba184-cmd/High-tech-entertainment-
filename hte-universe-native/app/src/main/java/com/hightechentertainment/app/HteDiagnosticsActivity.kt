package com.hightechentertainment.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class HteDiagnosticsActivity : Activity() {
    private lateinit var reportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(combinedReport())
    }

    private fun combinedReport(): String = buildString {
        append(HteDeviceDiagnostics.savedReport(this@HteDiagnosticsActivity))
        HteCrashReporter.pendingReport(this@HteDiagnosticsActivity)?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }

    private fun render(report: String) {
        val gold = Color.rgb(214, 177, 94)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(8, 9, 11))
        }
        root.addView(TextView(this).apply {
            text = "HTE DEVICE DIAGNOSTICS"
            textSize = 20f
            setTextColor(gold)
            gravity = Gravity.CENTER_VERTICAL
        })
        root.addView(TextView(this).apply {
            text = "On-device self-check • safe recovery • crash review • founder escalation"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 18)
        })
        reportText = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.rgb(22, 24, 28))
        }
        root.addView(ScrollView(this).apply { addView(reportText) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "Run diagnostics and safe recovery again"
            setOnClickListener {
                val result = HteDeviceDiagnostics.run(this@HteDiagnosticsActivity, true)
                reportText.text = combinedReport()
                Toast.makeText(this@HteDiagnosticsActivity,
                    if (result.hasFailures) "Issues remain. Send the report to the founder."
                    else "Device self-check completed.", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Email report to HTE founder"
            setOnClickListener {
                runCatching {
                    startActivity(HteDeviceDiagnostics.emailIntent(this@HteDiagnosticsActivity, reportText.text.toString()))
                }.onFailure {
                    Toast.makeText(this@HteDiagnosticsActivity,
                        "No email app found. Contact suwilanjisinkamba184@gmail.com", Toast.LENGTH_LONG).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }
}
