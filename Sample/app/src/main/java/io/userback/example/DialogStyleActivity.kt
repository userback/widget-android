package io.userback.example

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.userback.sdk.Userback

// A whole separate Activity styled to look like a dialog (via the Theme.AppCompat.Dialog theme in
// the manifest) — its own task and Window, unlike Dialog/DialogFragment/BottomSheetDialog which
// all attach a second Window on top of the host Activity.
class DialogStyleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        container.addView(TextView(this).apply {
            text = "Dialog-themed Activity"
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        container.addView(TextView(this).apply {
            text = "A whole separate Activity styled to look like a dialog."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 24)
        })

        container.addView(Button(this).apply {
            text = "Send Feedback with Screenshot"
            isAllCaps = false
            setOnClickListener {
                Userback.openForm(mode = "general", directTo = "screenshot")
                finish()
            }
        })

        setContentView(container)
    }
}
