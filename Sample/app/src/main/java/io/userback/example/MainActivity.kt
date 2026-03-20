package io.userback.example

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import io.userback.sdk.Userback
import android.webkit.WebView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomMenuContainer: LinearLayout

    private lateinit var homeScreen: View
    private lateinit var shopScreen: View
    private lateinit var profileScreen: View
    private lateinit var endpointTesterScreen: View

    private lateinit var statusTextView: TextView
    private var endpointStatus: String = "Not tested"
    private var currentScreen: View? = null

    // Create OkHttpClient with Userback Interceptor
    private val client = OkHttpClient.Builder()
        .addInterceptor(Userback.getInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Userback SDK
        Userback.init(
            context = this,
            accessToken = BuildConfig.USERBACK_TOKEN,
            userData = mapOf(
                "id" to "android-sample-user-001",
                "info" to mapOf(
                    "name" to "Demo User",
                    "email" to "demo.user@example.com"
                )
            ),
            widgetCSS = "${BuildConfig.USERBACK_BASE_URL}/dist/widget_dev/widget.min.css",
            surveyURL = "${BuildConfig.USERBACK_BASE_URL}/s",
            requestURL = BuildConfig.USERBACK_API_URL,
            trackURL = BuildConfig.USERBACK_EVENTS_URL,
            scriptURL = "${BuildConfig.USERBACK_BASE_URL}/dist/widget_dev/widget.min.js"
        )

        // 2. Setup UI
        setupUI()

        // Handle back button for navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (endpointTesterScreen.visibility == View.VISIBLE) {
                    showScreen(profileScreen)
                } else if (homeScreen.visibility != View.VISIBLE) {
                    showScreen(homeScreen)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Show initial screen
        showScreen(homeScreen)
    }

    private fun setupUI() {
        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(mainLayout)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainLayout.addView(contentFrame)

        // Create Screens
        createHomeView()
        createShopView()
        createProfileView()
        createEndpointTesterView()

        // Create Userback WebView (Overlay)
        Userback.createWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            // Set a red border around the webview for debugging
            setBackgroundResource(R.drawable.webview_border)
            rootContainer.addView(this)
        }

        // Bottom Menu
        bottomMenuContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = 10f
            setPadding(0, 20, 0, 20)
        }

        addMenuButton("Home") { showScreen(homeScreen) }
        addMenuButton("Shop") { showScreen(shopScreen) }
        addMenuButton("Profile") { showScreen(profileScreen) }

        mainLayout.addView(bottomMenuContainer)

        setContentView(rootContainer)
    }

    private fun addMenuButton(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
        bottomMenuContainer.addView(btn)
    }

    private fun createHomeView() {
        homeScreen = ScrollView(this).apply {
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            container.addView(TextView(this@MainActivity).apply {
                text = "Welcome"
                textSize = 32f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            })

            container.addView(TextView(this@MainActivity).apply {
                text = "Explore the app and let us know what you think."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, 48)
            })

            fun addHomeCard(title: String) {
                container.addView(Button(this@MainActivity).apply {
                    text = title
                    isAllCaps = false
                    setPadding(32, 32, 32, 32)
                    setBackgroundColor(Color.parseColor("#F9F9F9"))
                    setTextColor(Color.BLACK)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 0, 24)
                    layoutParams = lp
                })
            }

            addHomeCard("Latest Updates")
            addHomeCard("Special Offers")
            addHomeCard("News")

            addView(container)
        }
        contentFrame.addView(homeScreen)
    }

    private fun createShopView() {
        shopScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)

            addView(TextView(this@MainActivity).apply {
                text = "Shop"
                textSize = 32f
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 32)
            })

            addView(Button(this@MainActivity).apply {
                text = "Checkout Feedback"
                setOnClickListener {
                    Userback.openForm(mode = "general")
                }
            })
        }
        contentFrame.addView(shopScreen)
    }

    private fun createProfileView() {
        profileScreen = ScrollView(this).apply {
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }

            // User Info Section
            container.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 48)

                addView(TextView(this@MainActivity).apply {
                    text = "👤" // Placeholder for person icon
                    textSize = 40f
                })

                val textContainer = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 0, 0, 0)
                    addView(TextView(this@MainActivity).apply {
                        text = "Demo User"
                        textSize = 18f
                        setTextColor(Color.BLACK)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "demo.user@example.com"
                        textSize = 14f
                        setTextColor(Color.GRAY)
                    })
                }
                addView(textContainer)
            })

            fun addSectionTitle(title: String) {
                container.addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(Color.GRAY)
                    setPadding(0, 32, 0, 16)
                })
            }

            addSectionTitle("SUPPORT")
            fun addSupportBtn(label: String, mode: String) {
                container.addView(Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setOnClickListener { Userback.openForm(mode = mode) }
                })
            }
            addSupportBtn("Send Feedback", "general")
            addSupportBtn("Report a Bug", "bug")
            addSupportBtn("Request a Feature", "feature")

            addSectionTitle("SDK ENDPOINT TESTS")
            container.addView(Button(this@MainActivity).apply {
                text = "Open Endpoint Tester"
                isAllCaps = false
                setOnClickListener { showScreen(endpointTesterScreen) }
            })

            addView(container)
        }
        contentFrame.addView(profileScreen)
    }

    private fun createEndpointTesterView() {
        endpointTesterScreen = ScrollView(this).apply {
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }

            fun addSection(title: String) {
                container.addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(Color.BLUE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 32, 0, 16)
                })
            }

            fun addBtn(label: String, action: () -> Unit) {
                container.addView(Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { action() }
                })
            }

            addSection("Status")
            statusTextView = TextView(this@MainActivity).apply {
                text = endpointStatus
                textSize = 12f
                setTextColor(Color.DKGRAY)
            }
            container.addView(statusTextView)

            addSection("Lifecycle")
            addBtn("Check isLoaded") {
                Userback.isLoaded { loaded ->
                    updateStatus("isLoaded: $loaded")
                }
            }
            addBtn("Init Widget") {
                Userback.initWidget()
                updateStatus("Called initWidget()")
            }
            addBtn("Start Widget") {
                Userback.startWidget()
                updateStatus("Called startWidget()")
            }
            addBtn("Refresh") {
                Userback.refresh(refreshFeedback = true, refreshSurvey = true)
                updateStatus("Called refresh()")
            }
            addBtn("Destroy") {
                Userback.destroy(keepInstance = false, keepRecorder = false)
                updateStatus("Called destroy()")
            }

            addSection("Open / Close")
            addBtn("Open Form (general)") {
                Userback.openForm(mode = "general")
                updateStatus("Called openForm(general)")
            }
            addBtn("Open Portal") {
                Userback.openPortal()
                updateStatus("Called openPortal()")
            }
            addBtn("Open Roadmap") {
                Userback.openRoadmap()
                updateStatus("Called openRoadmap()")
            }
            addBtn("Open Announcement") {
                Userback.openAnnouncement()
                updateStatus("Called openAnnouncement()")
            }
            addBtn("Close Widget") {
                Userback.close()
                updateStatus("Called close()")
            }

            addSection("Identity & Data")
            addBtn("Identify User") {
                Userback.identify(userID = "android-sample-user-001", userInfo = mapOf("email" to "demo.user@example.com", "plan" to "pro"))
                updateStatus("Called identify()")
            }
            addBtn("Clear Identity") {
                Userback.clearIdentity()
                updateStatus("Called clearIdentity()")
            }
            addBtn("Set Data") {
                Userback.setData(mapOf("build" to "android-debug", "is_test" to true, "screen" to "profile"))
                updateStatus("Called setData()")
            }
            addBtn("Add Header") {
                Userback.addHeader(key = "X-Debug-Source", value = "android-sample")
                updateStatus("Called addHeader()")
            }

            addSection("Field Setters")
            addBtn("Set Email") { Userback.setEmail("demo.user@example.com"); updateStatus("Called setEmail()") }
            addBtn("Set Name") { Userback.setName("Demo User"); updateStatus("Called setName()") }
            addBtn("Set Categories") { Userback.setCategories("android,dev"); updateStatus("Called setCategories()") }
            addBtn("Set Priority") { Userback.setPriority("high"); updateStatus("Called setPriority()") }
            addBtn("Set Theme (dark)") { Userback.setTheme("dark"); updateStatus("Called setTheme(dark)") }

            addSection("Session Replay & Events")
            addBtn("Start Session Replay") {
                Userback.startSessionReplay(options = mapOf("tags" to listOf("android", "sample"), "mask_rules" to listOf("input[type=password]")))
                updateStatus("Called startSessionReplay()")
            }
            addBtn("Stop Session Replay") {
                Userback.stopSessionReplay()
                updateStatus("Called stopSessionReplay()")
            }
            addBtn("Add Custom Event") {
                Userback.addCustomEvent(title = "android_test_event", details = mapOf("source" to "endpoint_tester"))
                updateStatus("Called addCustomEvent()")
            }

            addSection("Native Logging")
            addBtn("Emit Test Console Log") {
                Log.d("Sample", "Native console event at ${Date()}")
                updateStatus("Printed test console log")
            }
            addBtn("Trigger Test Network Request") {
                val request = Request.Builder().url("https://httpbin.org/get").build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread { updateStatus("Test request failed: ${e.message}") }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread { updateStatus("Sent test request to httpbin") }
                        response.close()
                    }
                })
            }

            addView(container)
        }
        contentFrame.addView(endpointTesterScreen)
    }

    private fun updateStatus(status: String) {
        endpointStatus = status
        statusTextView.text = endpointStatus
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }

    private fun showScreen(screen: View) {
        homeScreen.visibility = View.GONE
        shopScreen.visibility = View.GONE
        profileScreen.visibility = View.GONE
        endpointTesterScreen.visibility = View.GONE
        
        screen.visibility = View.VISIBLE
        currentScreen = screen
        
        // Hide bottom menu if we are in the Endpoint Tester
        bottomMenuContainer.visibility = if (screen == endpointTesterScreen) View.GONE else View.VISIBLE
    }
}
