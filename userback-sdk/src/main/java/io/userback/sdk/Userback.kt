package io.userback.sdk

import io.userback.sdk.BuildConfig
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.WeakHashMap

object Userback {
    private var appContext: Context? = null
    private var accessToken: String? = null
    private var userData: JSONObject? = null
    private var widgetCSS: String? = null
    private var surveyURL: String? = null
    private var requestURL: String? = null
    private var trackURL: String? = null
    private var scriptURL: String? = null
    const val SDK_VERSION = "1.0.0"
    private const val DEFAULT_JS = "https://static.userback.io/widget/v1.js"

    private var isRecording: Boolean = false
    private val webViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    private var latestWidgetConfig: JSONObject? = null
    private var latestWidgetWidth: Int = 0
    private var latestWidgetHeight: Int = 0
    private var configCallbacks: android.content.ComponentCallbacks? = null
    private var formOpenTimeoutRunnable: Runnable? = null
    private val formOpenHandler = Handler(Looper.getMainLooper())
    private var isLogCaptureStarted = false
    private var isWidgetInjected = false
    private var cachedScreenshotDataUrl: String? = null

    private const val INITIAL_HTML = """
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  body { display: flex; flex-direction: column; justify-content: center; align-items: center; height: 100vh; margin: 0; font-family: sans-serif; background-color: transparent; }
                  h1 { text-align: center; color: #000; margin-bottom: 10px; opacity: 0; }
                </style>
              </head>
              <body>
                <h1>Userback SDK Layer</h1>
              </body>
            </html>
    """

    fun configure(
        accessToken: String,
        userData: Map<String, Any>? = null,
        widgetCSS: String? = null,
        surveyURL: String? = null,
        requestURL: String? = null,
        trackURL: String? = null,
        scriptURL: String? = null
    ) {
        this.accessToken = accessToken
        this.userData = userData?.let { JSONObject(it) }
        this.widgetCSS = widgetCSS
        this.surveyURL = surveyURL
        this.requestURL = requestURL
        this.trackURL = trackURL
        this.scriptURL = scriptURL ?: DEFAULT_JS
        this.isRecording = true
        Log.d("Userback", "Configured. Token: $accessToken")
    }

    fun init(
        context: Context,
        accessToken: String,
        userData: Map<String, Any>? = null,
        widgetCSS: String? = null,
        surveyURL: String? = null,
        requestURL: String? = null,
        trackURL: String? = null,
        scriptURL: String? = null
    ) {
        this.appContext = context.applicationContext
        configure(accessToken, userData, widgetCSS, surveyURL, requestURL, trackURL, scriptURL)
        startLogCapture()
        startConfigurationObserver()

        // Automatically attach WebView overlay after Activity layout is ready
        (context as? Activity)?.let { activity ->
            val webView = makeWebView(activity)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            webView.visibility = View.GONE
            Handler(Looper.getMainLooper()).post {
                activity.addContentView(webView, lp)
            }
        }
    }

    fun getInterceptor(): NetworkInterceptor {
        return NetworkInterceptor()
    }

    fun isLoaded(callback: (Boolean) -> Unit) {
        val webView = webViews.firstOrNull() ?: return callback(false)
        webView.post {
            webView.evaluateJavascript("window.Userback && typeof window.Userback.isLoaded === 'function' ? !!window.Userback.isLoaded() : false") { result ->
                callback(result?.toBoolean() ?: false)
            }
        }
    }

    fun initWidget(options: Map<String, Any> = emptyMap()) {
        val token = accessToken ?: return
        callUserback("init", token, JSONObject(options))
    }

    fun startWidget() { callUserback("start") }

    private fun handleConfigurationChanged(newConfig: android.content.res.Configuration) {
        val webView = webViews.firstOrNull() ?: return
        val dm = webView.resources.displayMetrics
        val screenWidth = (dm.widthPixels / dm.density).toInt()
        val screenHeight = (dm.heightPixels / dm.density).toInt()
        val orientation = newConfig.orientation

        val message = JSONObject().apply {
            put("type", "native_rotate")
            put("mobileSDK", true)
            put("payload", JSONObject().apply {
                put("orientation", orientation)
                put("screenWidth", screenWidth)
                put("screenHeight", screenHeight)
                put("mobileSDK", true)
            })
        }
        val js = """
            (function() {
                var detail = $message;
                var event = new CustomEvent('userback:rotate', { detail: detail });
                window.dispatchEvent(event);
            })();
        """.trimIndent()

        Log.d("Userback", "Dispatching rotate event: $message")
        webViews.forEach { it.post { it.evaluateJavascript(js, null) } }

        if (latestWidgetWidth > 0 && latestWidgetHeight > 0) {
            resizeWebView(latestWidgetWidth, latestWidgetHeight)
        }
        applyBreakpoint()
    }

    private fun startConfigurationObserver() {
        if (configCallbacks != null) return
        val callbacks = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                handleConfigurationChanged(newConfig)
            }
            override fun onLowMemory() {}
        }
        appContext?.registerComponentCallbacks(callbacks)
        configCallbacks = callbacks
    }

    private fun stopConfigurationObserver() {
        configCallbacks?.let { appContext?.unregisterComponentCallbacks(it) }
        configCallbacks = null
    }
    fun refresh(refreshFeedback: Boolean = true, refreshSurvey: Boolean = true) { callUserback("refresh", refreshFeedback, refreshSurvey) }
    fun destroy(keepInstance: Boolean = false, keepRecorder: Boolean = false) { callUserback("destroy", keepInstance, keepRecorder) }

    fun openForm(mode: String = "general", directTo: String? = null) {
        Log.d("Userback", "openForm called (Mode: $mode).")
        // Capture screenshot BEFORE making the WebView visible so there is no flicker.
        webViews.firstOrNull()?.let { webView ->
            webView.post {
                val root = webView.rootView
                if (root.width > 0 && root.height > 0) {
                    val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bmp))
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    cachedScreenshotDataUrl = "data:image/jpeg;base64," +
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
                val lp = webView.layoutParams as? android.widget.FrameLayout.LayoutParams
                    ?: android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                lp.gravity = android.view.Gravity.CENTER
                webView.layoutParams = lp
                webView.visibility = View.VISIBLE
                webView.bringToFront()

                if (latestWidgetConfig == null) {
                    formOpenTimeoutRunnable?.let { formOpenHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        Log.d("Userback", "openForm timed out — JS SDK did not respond. Closing WebView.")
                        close()
                    }
                    formOpenTimeoutRunnable = runnable
                    formOpenHandler.postDelayed(runnable, 5000)
                }
            }
        }
        callUserback("openForm", mode, directTo)
    }

    fun openPortal() {
        val target = latestWidgetConfig?.optString("portal_target")?.lowercase()
        val url = latestWidgetConfig?.optString("portal_url")
        if (target == "redirect" || target == "window") {
            url?.let { if (it.isNotEmpty()) openURL(it) } ?: callUserback("openPortal")
        } else {
            callUserback("openPortal", "portal")
        }
    }

    fun openRoadmap() {
        val target = latestWidgetConfig?.optString("roadmap_target")?.lowercase()
        if (target == "redirect" || target == "window") {
            latestWidgetConfig?.optString("portal_url")?.let { if (it.isNotEmpty()) openURL(it) } ?: callUserback("openRoadmap")
        } else {
            callUserback("openPortal", "roadmap")
        }
    }

    fun openAnnouncement() {
        val target = latestWidgetConfig?.optString("announcement_target")?.lowercase()
        if (target == "redirect" || target == "window") {
            latestWidgetConfig?.optString("portal_url")?.let { if (it.isNotEmpty()) openURL(it) } ?: callUserback("openAnnouncement")
        } else {
            callUserback("openPortal", "announcement")
        }
    }

    private fun openURL(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext?.startActivity(intent)
        } catch (e: Exception) {
            Log.e("Userback", "Failed to open URL: $url", e)
        }
    }

    fun setEmail(email: String) { callUserback("setEmail", email) }
    fun setName(name: String) { callUserback("setName", name) }
    fun setCategories(categories: String) { callUserback("setCategories", categories) }
    fun setPriority(priority: String) { callUserback("setPriority", priority) }
    fun setTheme(theme: String) { callUserback("setTheme", theme) }
    fun stopSessionReplay() { callUserback("stopSessionReplay") }
    fun startSessionReplay(options: Map<String, Any> = emptyMap()) { callUserback("startSessionReplay", JSONObject(options)) }
    fun addCustomEvent(title: String, details: Map<String, Any>? = null) { callUserback("addCustomEvent", title, details?.let { JSONObject(it) }) }
    fun identify(userID: Any, userInfo: Map<String, Any>? = null) { callUserback("identify", userID, userInfo?.let { JSONObject(it) }) }
    fun clearIdentity() { callUserback("identify", -1) }
    fun setData(data: Map<String, Any>) { callUserback("setData", JSONObject(data)) }
    fun addHeader(key: String, value: String) { callUserback("addHeader", key, value) }

    fun close() {
        callUserback("close")
        webViews.forEach { it.post { it.visibility = View.GONE } }
    }

    fun sendNativeEvent(event: JSONObject) {
        if (!isRecording) return
        val eventType = event.optString("eventType", "console")
        val jsEventName = when (eventType) {
            "network" -> "userback:nativeNetworkEvent"
            else -> "userback:nativeLogEvent"
        }

        val js = """
            (function() {
                var nativePayload = $event;
                if (nativePayload && typeof nativePayload === 'object' && nativePayload.mobileSDK === undefined) {
                    nativePayload.mobileSDK = true;
                }
                var nativeDetail = {
                    type: 'native_event',
                    payload: nativePayload,
                    mobileSDK: true
                };
                var customEvent = new CustomEvent('$jsEventName', { detail: nativeDetail });
                window.dispatchEvent(customEvent);
            })();
        """.trimIndent()

        webViews.forEach { it.post { it.evaluateJavascript(js, null) } }
    }

    private fun startLogCapture() {
        if (isLogCaptureStarted) return
        isLogCaptureStarted = true

        val pid = android.os.Process.myPid().toString()
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "--pid=$pid", "-v", "brief"))
                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val msg = line ?: continue
                    if (msg.isBlank() || msg.startsWith("-----")) continue
                    if (msg.contains("Userback") || msg.contains("UserbackJS")) continue
                    if (msg.contains("chromium", ignoreCase = true)) continue
                    if (msg.contains("native log event", ignoreCase = true)) continue
                    if (msg.contains("userback:nativeLogEvent", ignoreCase = true)) continue
                    // Skip noisy Android system tags
                    if (msg.startsWith("I/HWUI") || msg.startsWith("D/HWUI")) continue
                    if (msg.contains("Davey!", ignoreCase = true)) continue
                    if (msg.startsWith("D/InsetsController") || msg.startsWith("I/InsetsController")) continue
                    if (msg.startsWith("D/ViewRootImpl") || msg.startsWith("I/ViewRootImpl")) continue
                    if (msg.startsWith("D/InputMethodManager") || msg.startsWith("I/InputMethodManager")) continue
                    if (msg.startsWith("I/OpenGLRenderer") || msg.startsWith("D/OpenGLRenderer")) continue
                    if (msg.startsWith("I/SurfaceFlinger") || msg.startsWith("D/SurfaceFlinger")) continue
                    if (msg.startsWith("D/EGL_emulation") || msg.startsWith("I/EGL_emulation")) continue
                    if (msg.startsWith("D/gralloc") || msg.startsWith("I/gralloc")) continue
                    if (msg.startsWith("I/chatty") || msg.startsWith("D/chatty")) continue
                    if (msg.startsWith("W/System.err")) continue
                    if (msg.startsWith("E/RippleDrawable") || msg.startsWith("W/RippleDrawable")) continue
                    if (msg.startsWith("D/WindowOnBackDispatcher") || msg.startsWith("W/WindowOnBackDispatcher")) continue
                    if (msg.startsWith("W/InputEventReceiver")) continue
                    if (msg.startsWith("I/AssistStructure") || msg.startsWith("D/AssistStructure")) continue
                    if (msg.startsWith("I/ImeTracker") || msg.startsWith("D/ImeTracker") || msg.startsWith("W/ImeTracker")) continue
                    if (msg.startsWith("W/InteractionJankMonitor") || msg.startsWith("I/InteractionJankMonitor")) continue
                    if (msg.startsWith("D/ProfileInstaller") || msg.startsWith("I/ProfileInstaller")) continue
                    if (msg.contains("userfaultfd", ignoreCase = true)) continue
                    if (msg.contains("NativeAlloc concurrent", ignoreCase = true)) continue
                    if (msg.contains("concurrent mark compact GC", ignoreCase = true)) continue

                    val level = when {
                        msg.startsWith("E/") -> "error"
                        msg.startsWith("W/") -> "warn"
                        msg.startsWith("I/") -> "info"
                        msg.startsWith("D/") -> "debug"
                        else -> "log"
                    }

                    val event = JSONObject().apply {
                        put("eventType", "console")
                        put("type", "log")
                        put("message", msg)
                        put("level", level)
                        put("timestamp", System.currentTimeMillis())
                    }

                    sendNativeEvent(event)
                }
            } catch (_: Exception) { }
        }.also { it.isDaemon = true }.start()
    }

    private fun captureAndSendScreenshot() {
        val webView = webViews.firstOrNull() ?: return
        val dataUrl = cachedScreenshotDataUrl
        if (dataUrl != null) {
            // Use the pre-captured screenshot — no need to hide/show the WebView.
            val payload = JSONObject().apply {
                put("type", "native_screenshot")
                put("payload", JSONObject().apply { put("data_url", dataUrl) })
            }
            val js = """
                (function() {
                    var nativeDetail = $payload;
                    var event = new CustomEvent('userback:nativeScreenshot', { detail: nativeDetail });
                    try {
                        event.payload = nativeDetail;
                        Object.assign(event, nativeDetail);
                    } catch (ignored) {}
                    window.dispatchEvent(event);
                })();
            """.trimIndent()
            webView.post { webView.evaluateJavascript(js, null) }
            return
        }

        // Fallback: no cached screenshot, capture now (causes brief flicker).
        webView.post {
            webView.visibility = View.INVISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                val window = webView.rootView
                if (window.width > 0 && window.height > 0) {
                    val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
                    window.draw(Canvas(bitmap))
                    webView.visibility = View.VISIBLE
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    val payload = JSONObject().apply {
                        put("type", "native_screenshot")
                        put("payload", JSONObject().apply { put("data_url", "data:image/jpeg;base64,$base64Image") })
                    }
                    val js = """
                        (function() {
                            var nativeDetail = $payload;
                            var event = new CustomEvent('userback:nativeScreenshot', { detail: nativeDetail });
                            try {
                                event.payload = nativeDetail;
                                Object.assign(event, nativeDetail);
                            } catch (ignored) {}
                            window.dispatchEvent(event);
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                } else {
                    webView.visibility = View.VISIBLE
                }
            }, 50)
        }
    }

    private fun callUserback(function: String, vararg arguments: Any?) {
        val argsString = arguments.joinToString(", ") { arg ->
            when (arg) {
                null -> "null"
                is String -> "\"$arg\""
                else -> arg.toString()
            }
        }
        val js = "window.Userback && typeof window.Userback.$function === 'function' && window.Userback.$function($argsString);"
        webViews.forEach { it.post { it.evaluateJavascript(js, null) } }
    }

    private fun sendDeviceSize() {
        val webView = webViews.firstOrNull() ?: return
        webView.post {
            val dm = webView.resources.displayMetrics
            val container = webView.parent as? android.view.View
            val deviceWidth = ((container?.width ?: dm.widthPixels) / dm.density).toInt()
            val deviceHeight = ((container?.height ?: dm.heightPixels) / dm.density).toInt()
            val message = JSONObject().apply {
                put("type", "native_device_size")
                put("mobileSDK", true)
                put("payload", JSONObject().apply {
                    put("deviceWidth", deviceWidth)
                    put("deviceHeight", deviceHeight)
                    put("mobileSDK", true)
                })
            }
            val js = """
                (function() {
                    var detail = $message;
                    window.dispatchEvent(new CustomEvent('userback:nativeDeviceSize', { detail: detail }));
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    private fun applyBreakpoint() {
        val webView = webViews.firstOrNull() ?: return
        webView.post {
            val density = webView.resources.displayMetrics.density
            val isTablet = webView.resources.displayMetrics.widthPixels / density > 800
            val js = """
                (function() {
                    var container = document.querySelector('.userback-button-container');
                    if (container) {
                        if ($isTablet) {
                            container.setAttribute('data-breakpoint', 'tablet');
                        } else {
                            container.removeAttribute('data-breakpoint');
                        }
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    private enum class WidgetPosition(val value: String) {
        W("w"), E("e"), SW("sw"), SE("se");

        companion object {
            fun from(rawValue: String): WidgetPosition =
                entries.find { it.value == rawValue.lowercase() } ?: SE
        }
    }

    private fun widgetPositionFromConfig(): WidgetPosition {
        val raw = latestWidgetConfig?.optString("position") ?: return WidgetPosition.SE
        if (raw.isEmpty()) return WidgetPosition.SE
        return WidgetPosition.from(raw)
    }

    private fun resizeWebView(widthDp: Int, heightDp: Int) {
        val webView = webViews.firstOrNull() ?: return
        webView.post {
            val density = webView.resources.displayMetrics.density
            val screenWidthDp = webView.resources.displayMetrics.widthPixels / density
            val lp = webView.layoutParams as? android.widget.FrameLayout.LayoutParams
                ?: android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

            if (widthDp > 0 && heightDp > 0 && screenWidthDp > 800) {
                lp.width = (widthDp * density).toInt()
                lp.height = ((heightDp + 20) * density).toInt()
                lp.gravity = when (widgetPositionFromConfig()) {
                    WidgetPosition.W  -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    WidgetPosition.E  -> android.view.Gravity.END   or android.view.Gravity.CENTER_VERTICAL
                    WidgetPosition.SW -> android.view.Gravity.START  or android.view.Gravity.BOTTOM
                    WidgetPosition.SE -> android.view.Gravity.END    or android.view.Gravity.BOTTOM
                }
            } else {
                lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                lp.gravity = android.view.Gravity.CENTER
            }

            webView.layoutParams = lp
            sendDeviceSize()
        }
    }

    private class UserbackJsBridge {
        @JavascriptInterface
        fun postMessage(payload: String) {
            try {
                val body = JSONObject(payload)
                when (body.optString("type").lowercase()) {
                    "load" -> {
                        latestWidgetConfig = body.optJSONObject("payload")
                        if (latestWidgetWidth > 0 && latestWidgetHeight > 0) {
                            resizeWebView(latestWidgetWidth, latestWidgetHeight)
                        }
                    }
                    "widget_action" -> if (body.optJSONObject("payload")?.optString("action") == "attachScreenshot") captureAndSendScreenshot()
                    "widget_resize" -> {
                        val p = body.optJSONObject("payload")
                        if (p != null) {
                            val w = p.optInt("width", 0)
                            val h = p.optInt("height", 0)
                            if (h > 0) {
                                formOpenTimeoutRunnable?.let { formOpenHandler.removeCallbacks(it) }
                                formOpenTimeoutRunnable = null
                                latestWidgetWidth = w
                                latestWidgetHeight = h
                                resizeWebView(w, h)
                                applyBreakpoint()
                            }
                        }
                    }
                    "load_error" -> close()
                    "hcaptcha_required" -> {
                        val message = body.optJSONObject("payload")?.optString("message") ?: "hCaptcha required"
                        Log.d("Userback", "JS SDK hCaptcha required: $message. Closing WebView.")
                    }
                    "close" -> close()
                    else -> if (body.optString("event").lowercase() == "close") close()
                }
            } catch (e: Exception) {
                if (payload.equals("close", ignoreCase = true)) close()
            }
        }
    }

    fun makeWebView(context: Context): WebView {
        val webView = WebView(context)
        webViews.add(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(UserbackJsBridge(), "userbackSDK")
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!isWidgetInjected) {
                    isWidgetInjected = true
                    view.evaluateJavascript(buildInjectedJS(), null)
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                if (BuildConfig.DEBUG && (scriptURL?.contains(".net") == true || scriptURL?.contains("ngrok") == true)) handler.proceed() else super.onReceivedSslError(view, handler, error)
            }
        }
        webView.loadDataWithBaseURL("https://static.userback.io", INITIAL_HTML.trimIndent(), "text/html", "utf-8", "https://static.userback.io")
        return webView
    }

    fun createWebView(context: Context) = makeWebView(context)

    private fun buildInjectedJS(): String {
        fun jsonString(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"$value\""
            is JSONObject -> value.toString()
            else -> JSONObject.wrap(value).toString()
        }

        val ctx = appContext ?: return ""
        val pm = ctx.packageManager
        val packageInfo = try { pm.getPackageInfo(ctx.packageName, 0) } catch (e: Exception) { null }
        val appVersion = packageInfo?.versionName ?: "unknown"
        val versionCode = packageInfo?.longVersionCode?.toString() ?: ""
        val fullAppVersion = if (versionCode.isNotEmpty()) "$appVersion ($versionCode)" else appVersion

        val dm = ctx.resources.displayMetrics
        val screenWidthPx = dm.widthPixels
        val screenHeightPx = dm.heightPixels
        val density = dm.density

        val nativeEnv = JSONObject().apply {
            put("platform", "android")
            put("sdk_version", SDK_VERSION)
            put("app_version", fullAppVersion)
            put("os_version", android.os.Build.VERSION.RELEASE)
            put("device_model", android.os.Build.MODEL)
            put("device_name", android.os.Build.DEVICE)
            put("resolution_x", screenWidthPx)
            put("resolution_y", screenHeightPx)
            put("screen_width_pt", (screenWidthPx / density).toInt())
            put("screen_height_pt", (screenHeightPx / density).toInt())
            put("dpi_scale", density)
        }

        val nativeUaData = JSONObject().apply {
            put("platform", "android")
            put("platformVersion", android.os.Build.VERSION.RELEASE)
            put("model", android.os.Build.MODEL)
            put("sdkVersion", SDK_VERSION)
        }

        return """
            window.Userback = window.Userback || {};
            Userback.load_type = "mobile_sdk";
            Userback.access_token = ${jsonString(accessToken)};
            Userback.user_data = ${jsonString(userData)};
            Userback.native_env = $nativeEnv;
            Userback.native_ua_data = $nativeUaData;
            ${if (BuildConfig.DEBUG) "Userback.widget_css = ${jsonString(widgetCSS)};" else ""}
            ${if (BuildConfig.DEBUG) "Userback.survey_url = ${jsonString(surveyURL)};" else ""}
            ${if (BuildConfig.DEBUG) "Userback.request_url = ${jsonString(requestURL)};" else ""}
            ${if (BuildConfig.DEBUG) "Userback.track_url = ${jsonString(trackURL)};" else ""}
            (function(d){
                var s = d.createElement('script');
                s.async = true;
                s.src = '${scriptURL ?: DEFAULT_JS}';
                (d.head || d.body).appendChild(s);
            })(document);
        """.trimIndent()
    }
}
