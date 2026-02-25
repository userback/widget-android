package io.userback.example

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import io.userback.sdk.Userback
import android.webkit.WebView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var contentFrame: FrameLayout
    private lateinit var webView: WebView
    private lateinit var testCenterView: LinearLayout

    // Create OkHttpClient with Userback Interceptor
    private val client = OkHttpClient.Builder()
        .addInterceptor(Userback.getInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Run Userback init when app load
        Userback.init(
            context = this,
            accessToken = BuildConfig.USERBACK_TOKEN,
            userData = mapOf(
                "id" to "123456",
                "info" to mapOf(
                    "name" to "someone",
                    "email" to "someone@example.com"
                )
            ),
            widgetCSS = "${BuildConfig.USERBACK_BASE_URL}/dist/widget_dev/widget.min.css",
            surveyURL = "${BuildConfig.USERBACK_BASE_URL}/s",
            requestURL = BuildConfig.USERBACK_API_URL,
            trackURL = BuildConfig.USERBACK_EVENTS_URL,
            scriptURL = "${BuildConfig.USERBACK_BASE_URL}/dist/widget_dev/widget.min.js"
        )

        // 2. Create UI with Content Frame and bottom menus
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Content Frame to hold different screens
        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentFrame)

        // --- Screen 1: WebView ---
        webView = Userback.createWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        contentFrame.addView(webView)

        // --- Screen 2: Test Center ---
        testCenterView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#E1F5FE")) // Light Blue background

            addView(TextView(this@MainActivity).apply {
                text = "Test Center"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 60)
            })

            // Button 1: Generate Test Log
            addView(Button(this@MainActivity).apply {
                text = "Generate Test Log"
                setOnClickListener {
                    val timestamp = System.currentTimeMillis()

                    // 1. Regular Android Logs of different types
                    Log.v("UserbackTest", "Verbose test log at $timestamp")
                    Log.d("UserbackTest", "Debug test log at $timestamp")
                    Log.i("UserbackTest", "Info test log at $timestamp")
                    Log.w("UserbackTest", "Warning test log at $timestamp")
                    Log.e("UserbackTest", "Error test log at $timestamp")

                    // 2. Direct event triggers for different log levels (manual fallback)
                    val levels = listOf("verbose", "debug", "info", "warn", "error")
                    levels.forEach { level ->
                        val event = JSONObject().apply {
                            put("type", "log")
                            put("level", level)
                            put("message", "MANUAL $level log at $timestamp")
                        }
                        Userback.sendNativeEvent(event)
                    }

                    Toast.makeText(this@MainActivity, "Multiple test logs generated!", Toast.LENGTH_SHORT).show()
                }
            })

            // Spacer
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(1, 30)
            })

            // Button 2: Generate Network Request
            addView(Button(this@MainActivity).apply {
                text = "Generate Network Request"
                setOnClickListener {
                    // Success GET
                    makeSampleRequest("GET", "https://jsonplaceholder.typicode.com/todos/1")

                    // Success POST
                    makeSampleRequest("POST", "https://jsonplaceholder.typicode.com/posts")

                    // Error 404
                    makeSampleRequest("GET", "https://jsonplaceholder.typicode.com/invalid-path-404")

                    Toast.makeText(this@MainActivity, "Multiple requests triggered!", Toast.LENGTH_SHORT).show()
                }
            })
        }
        contentFrame.addView(testCenterView)

        // Bottom Menu (Centered at the bottom)
        val bottomMenuContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER // This centers the contents horizontally
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }

        val homeButton = Button(this).apply {
            text = "Home"
            setOnClickListener {
                showScreen(webView)
            }
        }
        bottomMenuContainer.addView(homeButton)

        val testButton = Button(this).apply {
            text = "Tests"
            setOnClickListener {
                showScreen(testCenterView)
            }
        }
        bottomMenuContainer.addView(testButton)

        val openUserbackButton = Button(this).apply {
            text = "Open Userback"
            setOnClickListener {
                showScreen(webView)
                Userback.open(webView, "general")
            }
        }
        bottomMenuContainer.addView(openUserbackButton)

        rootLayout.addView(bottomMenuContainer)
        setContentView(rootLayout)
    }

    private fun showScreen(screen: View) {
        webView.visibility = View.GONE
        testCenterView.visibility = View.GONE

        screen.visibility = View.VISIBLE
    }

    private fun makeSampleRequest(method: String = "GET", url: String) {
        val requestBuilder = Request.Builder().url(url)

        if (method == "POST") {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val jsonBody = "{\"title\": \"foo\", \"body\": \"bar\", \"userId\": 1}"
            requestBuilder.post(jsonBody.toRequestBody(mediaType))
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainActivity", "Request to $url failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("MainActivity", "Request to $url returned: ${response.code}")
                response.close()
            }
        })
    }
}
