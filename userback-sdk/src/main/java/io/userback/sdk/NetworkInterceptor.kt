package io.userback.sdk

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class NetworkInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val requestInitiatorType = initiatorType(request)
        
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val event = JSONObject().apply {
                put("eventType", "network")
                put("name", request.url.toString())
                put("method", request.method)
                put("status", 0)
                put("responseStatus", 0)
                put("startTime", startTime)
                put("duration", System.currentTimeMillis() - startTime)
                put("timestamp", System.currentTimeMillis())
                put("type", requestInitiatorType)
                put("initiatorType", requestInitiatorType)
                put("error", e.message)
            }
            Userback.sendNativeEvent(event)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        
        val event = JSONObject().apply {
            put("eventType", "network")
            put("name", request.url.toString())
            put("method", request.method)
            put("status", response.code)
            put("responseStatus", response.code)
            put("startTime", startTime)
            put("duration", duration)
            put("timestamp", System.currentTimeMillis())
            put("type", requestInitiatorType)
            put("initiatorType", requestInitiatorType)
            put("encodedBodySize", response.body?.contentLength() ?: 0)
            put("transferSize", response.body?.contentLength() ?: 0)
        }
        Userback.sendNativeEvent(event)

        return response
    }

    private fun initiatorType(request: Request): String {
        val xRequestedWith = request.header("X-Requested-With")?.lowercase()
        if (xRequestedWith == "xmlhttprequest") return "xmlhttprequest"

        val secFetchDest = request.header("Sec-Fetch-Dest")?.lowercase()
        if (!secFetchDest.isNullOrBlank()) {
            if (secFetchDest == "empty") return "fetch"
            return secFetchDest
        }

        val secFetchMode = request.header("Sec-Fetch-Mode")?.lowercase()
        if (!secFetchMode.isNullOrBlank()) {
            if (secFetchMode == "navigate") return "document"
            if (secFetchMode == "cors" || secFetchMode == "no-cors" || secFetchMode == "same-origin") {
                return "fetch"
            }
        }

        val a = request.header("Accept")?.lowercase() ?: return "fetch"
        return when {
            a.contains("javascript") -> "script"
            a.contains("text/css") -> "style"
            a.contains("image/") -> "image"
            a.contains("text/html") -> "document"
            a.contains("application/json") -> "fetch"
            a.contains("text/plain") -> "fetch"
            a.contains("application/octet-stream") -> "fetch"
            a.contains("*/*") -> "fetch"
            else -> "other"
        }
    }
}
