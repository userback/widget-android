package io.userback.sdk

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.JavascriptInterface
import androidx.core.net.toUri
import org.json.JSONObject
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
    private const val DEFAULT_JS = "https://static.userback.io/widget/v1.js"

    private var isRecording: Boolean = false
    private val pendingEvents = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val webViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    private var latestWidgetConfig: JSONObject? = null

    private const val INITIAL_HTML = """
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; font-family: sans-serif; background-color: #F0F0F0; }
                  h1 { text-align: center; color: #333; }
                </style>
              </head>
              <body>
                <h1>Userback SDK Initialized</h1>
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
        Log.d("Userback", "Configured and event recording started.")
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
    }

    fun getInterceptor(): NetworkInterceptor {
        return NetworkInterceptor()
    }

    // --- SDK methods to match JS/iOS functionality ---

    fun widgetConfig(): JSONObject? = latestWidgetConfig

    fun portalTarget(): String? = latestWidgetConfig?.optString("portal_target")?.ifEmpty { null }

    fun roadmapTarget(): String? = latestWidgetConfig?.optString("roadmap_target")?.ifEmpty { null }

    fun portalURL(): String? = latestWidgetConfig?.optString("portal_url")?.ifEmpty { null }

    fun startNativeRecording() {
        Log.d("Userback", "Native recording hook called. Plug in your native recorder integration here.")
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

    fun startWidget() {
        callUserback("start")
    }

    fun refresh(refreshFeedback: Boolean = true, refreshSurvey: Boolean = true) {
        callUserback("refresh", refreshFeedback, refreshSurvey)
    }

    fun destroy(keepInstance: Boolean = false, keepRecorder: Boolean = false) {
        callUserback("destroy", keepInstance, keepRecorder)
    }

    fun openForm(mode: String = "general", directTo: String? = null) {
        webViews.forEach { it.post { it.visibility = View.VISIBLE } }
        callUserback("openForm", mode, directTo)
    }

    fun openPortal() {
        val target = portalTarget()?.lowercase()
        val url = portalURL()
        Log.d("Userback", "openPortal triggered. Current config target: $target, URL: $url")

        when (target) {
            "widget" -> {
                Log.d("Userback", "Target is 'widget', calling JS openPortal('portal')")
                callUserback("openPortal", "portal")
            }
            "redirect", "window" -> {
                if (!url.isNullOrEmpty()) {
                    Log.d("Userback", "Target is $target, opening external browser: $url")
                    openURL(url)
                } else {
                    Log.w("Userback", "Target is $target but portalURL is missing. Falling back to JS.")
                    callUserback("openPortal")
                }
            }
            else -> {
                Log.d("Userback", "Target is unknown ($target), falling back to JS openPortal()")
                callUserback("openPortal")
            }
        }
    }

    fun openRoadmap() {
        val target = roadmapTarget()?.lowercase()
        val url = portalURL()
        Log.d("Userback", "openRoadmap triggered. Target: $target")

        when (target) {
            "widget" -> callUserback("openPortal", "roadmap")
            "redirect", "window" -> {
                if (!url.isNullOrEmpty()) {
                    openURL(url)
                } else {
                    callUserback("openRoadmap")
                }
            }
            else -> callUserback("openRoadmap")
        }
    }

    fun openAnnouncement() {
        val target = latestWidgetConfig?.optString("announcement_target")?.lowercase()?.ifEmpty { null }
        val url = portalURL()
        Log.d("Userback", "openAnnouncement triggered. Target: $target")

        when (target) {
            "widget" -> callUserback("openPortal", "announcement")
            "redirect", "window" -> {
                if (!url.isNullOrEmpty()) {
                    openURL(url)
                } else {
                    callUserback("openAnnouncement")
                }
            }
            else -> callUserback("openAnnouncement")
        }
    }

    private fun openURL(url: String) {
        Log.d("Userback", "openURL called with: $url")
        val context = appContext
        if (context == null) {
            Log.e("Userback", "CRITICAL: appContext is null. Userback.init(context, ...) must be called before opening URLs.")
            return
        }

        try {
            val uri = url.toUri()
            if (uri.scheme == null) {
                Log.e("Userback", "ABORT: Invalid URL scheme for '$url'. Must start with http:// or https://")
                return
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("Userback", "BROWSER START: Intent successfully sent for $url")
        } catch (e: Exception) {
            Log.e("Userback", "ERROR: Could not start browser activity for $url", e)
        }
    }

    fun setEmail(email: String) {
        callUserback("setEmail", email)
    }

    fun setName(name: String) {
        callUserback("setName", name)
    }

    fun setCategories(categories: String) {
        callUserback("setCategories", categories)
    }

    fun setPriority(priority: String) {
        callUserback("setPriority", priority)
    }

    fun setTheme(theme: String) {
        callUserback("setTheme", theme)
    }

    fun startSessionReplay(options: Map<String, Any> = emptyMap()) {
        callUserback("startSessionReplay", JSONObject(options))
    }

    fun stopSessionReplay() {
        callUserback("stopSessionReplay")
    }

    fun addCustomEvent(title: String, details: Map<String, Any>? = null) {
        callUserback("addCustomEvent", title, details?.let { JSONObject(it) })
    }

    fun identify(userID: Any, userInfo: Map<String, Any>? = null) {
        callUserback("identify", userID, userInfo?.let { JSONObject(it) })
    }

    fun clearIdentity() {
        callUserback("identify", -1)
    }

    fun setData(data: Map<String, Any>) {
        callUserback("setData", JSONObject(data))
    }

    fun addHeader(key: String, value: String) {
        callUserback("addHeader", key, value)
    }

    fun close() {
        Log.d("Userback", "Widget requested to close")
        callUserback("close")
        webViews.forEach { webView ->
            webView.post {
                webView.visibility = View.GONE
            }
        }
    }

    fun sendNativeEvent(event: JSONObject) {
        if (!isRecording) return
        if (webViews.isEmpty()) {
            synchronized(pendingEvents) {
                pendingEvents.add(event)
                if (pendingEvents.size > 500) pendingEvents.removeAt(0)
            }
            return
        }
        val js = "window.Userback && Userback.addNativeEvent($event)"
        webViews.forEach { webView ->
            webView.post { webView.evaluateJavascript(js, null) }
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
        webViews.forEach { webView ->
            webView.post { webView.evaluateJavascript(js, null) }
        }
    }

    private fun flushPendingEvents(webView: WebView) {
        synchronized(pendingEvents) {
            if (pendingEvents.isEmpty()) return
            Log.d("Userback", "Flushing ${pendingEvents.size} pending events")
            pendingEvents.forEach { event ->
                val js = "window.Userback && Userback.addNativeEvent($event)"
                webView.evaluateJavascript(js, null)
            }
            pendingEvents.clear()
        }
    }

    private class UserbackJsBridge {
        @JavascriptInterface
        fun postMessage(payload: String) {
            Log.d("Userback", "postMessage received: $payload")
            try {
                val json = JSONObject(payload)
                val type = json.optString("type")
                val eventName = json.optString("event")

                // Handle config/load events
                if (type == "load" || type == "config" || eventName == "load" || eventName == "config") {
                    // Try "payload" first (matching the log provided) then fallback to "config"
                    val config = json.optJSONObject("payload") ?: json.optJSONObject("config")
                    if (config != null) {
                        latestWidgetConfig = config
                        Log.d("Userback", "latestWidgetConfig loaded successfully")
                        return
                    }
                }

                // Handle close events
                if (type.equals("close", ignoreCase = true) || eventName.equals("close", ignoreCase = true)) {
                    close()
                    return
                }
            } catch (e: Exception) {
                if (payload.equals("close", ignoreCase = true)) {
                    close()
                    return
                }
                Log.e("Userback", "Error parsing postMessage payload", e)
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
        webView.addJavascriptInterface(UserbackJsBridge(), "userbackSDK")
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val js = buildInjectedJS()
                view.evaluateJavascript(js, null)
                flushPendingEvents(view)
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (scriptURL?.contains(".net") == true || scriptURL?.contains("ngrok") == true) {
                    handler?.proceed()
                } else {
                    super.onReceivedSslError(view, handler, error)
                }
            }
        }
        webView.loadDataWithBaseURL("https://static.userback.io", INITIAL_HTML.trimIndent(), "text/html", "utf-8", null)
        return webView
    }

    fun createWebView(context: Context) = makeWebView(context)

    private fun buildInjectedJS(): String {
        fun jsonString(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"$value\""
            is JSONObject -> value.toString()
            else -> {
                val wrapped = JSONObject.wrap(value)
                wrapped?.toString() ?: "null"
            }
        }
        return """
            window.Userback = window.Userback || {};
            Userback.load_type = "mobile_sdk";
            Userback.access_token = ${jsonString(accessToken)};
            Userback.user_data = ${jsonString(userData)};
            Userback.widget_css = ${jsonString(widgetCSS)};
            Userback.survey_url = ${jsonString(surveyURL)};
            Userback.request_url = ${jsonString(requestURL)};
            Userback.track_url = ${jsonString(trackURL)};
            (function(d){
                var s = d.createElement('script');
                s.async = true;
                s.src = '${scriptURL ?: DEFAULT_JS}';
                (d.head || d.body).appendChild(s);
            })(document);
        """.trimIndent()
    }
}
