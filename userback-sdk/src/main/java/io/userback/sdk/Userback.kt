package io.userback.sdk

import android.content.Context

object Userback {
    private var apiKey: String? = null

    fun init(context: Context, apiKey: String) {
        this.apiKey = apiKey
        // Initialization logic here
    }

    fun show() {
        if (apiKey == null) {
            throw IllegalStateException("Userback SDK must be initialized with an API key first.")
        }
        // Logic to show the Userback widget
    }
}
