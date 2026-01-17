package com.ledge.splitbook

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "Simple Split (Debug Entry)"
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(root)

        // Try to launch MainActivity after a short delay
        root.postDelayed({
            try {
                startActivity(Intent(this, MainActivity::class.java))
            } catch (_: Throwable) {
                // Stay on this screen; at least app is open
            }
        }, 500)
    }
}
