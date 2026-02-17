package io.userback.example

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import io.userback.sdk.Userback

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Userback SDK
        Userback.init(this, "YOUR_API_KEY_HERE")

        val showButton = findViewById<Button>(R.id.button_show)
        showButton.setOnClickListener {
            Userback.show()
        }
    }
}
