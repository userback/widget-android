package io.userback.sdk

import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap

object Userback {
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

    @JvmStatic
    val instance = this

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
    ) = configure(accessToken, userData, widgetCSS, surveyURL, requestURL, trackURL, scriptURL)

    fun getInterceptor(): NetworkInterceptor {
        return NetworkInterceptor()
    }

    fun open(webView: WebView, destination: String? = null) {
        webView.post {
            webView.visibility = View.VISIBLE
        }
        val js = if (destination != null) {
            "Userback.open('$destination')"
        } else {
            "Userback.open()"
        }
        Log.d("Userback", "Executing JS: $js")
        webView.evaluateJavascript(js, null)
    }

    fun close() {
        Log.d("Userback", "Widget requested to close")
        // We no longer hide the WebView or reload HTML here
        // This keeps the "Initialized" text visible in the background
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
            try {
                val json = JSONObject(payload)
                val type = json.optString("type")
                val event = json.optString("event")
                if (type.equals("close", ignoreCase = true) || event.equals("close", ignoreCase = true)) {
                    Userback.close()
                    return
                }
            } catch (e: Exception) {
                if (payload.equals("close", ignoreCase = true)) {
                    Userback.close()
                    return
                }
            }
            Log.d("Userback", "Ignoring unsupported script message body: $payload")
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
                // Always inject JS SDK so it's ready to be opened over the placeholder text
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
            else -> JSONObject.wrap(value).toString()
        }
        return """
            window.Userback = window.Userback || {};
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
