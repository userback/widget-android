package io.userback.sdk

import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class NetworkInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        
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
                put("type", initiatorType(request.header("Accept"), request.header("X-Requested-With")))
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
            put("type", initiatorType(request.header("Accept"), request.header("X-Requested-With")))
            put("encodedBodySize", response.body?.contentLength() ?: 0)
            put("transferSize", response.body?.contentLength() ?: 0)
        }
        Userback.sendNativeEvent(event)

        return response
    }

    private fun initiatorType(accept: String?, xRequestedWith: String?): String {
        if (xRequestedWith?.lowercase() == "xmlhttprequest") return "xmlhttprequest"
        val a = accept?.lowercase() ?: return "other"
        return when {
            a.contains("javascript") -> "script"
            a.contains("text/css") -> "style"
            a.contains("image/") -> "image"
            a.contains("text/html") -> "document"
            else -> "other"
        }
    }
}
