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
                put("type", "network")
                put("url", request.url.toString())
                put("method", request.method)
                put("status", 0)
                put("error", e.message)
                put("duration", System.currentTimeMillis() - startTime)
                put("timestamp", startTime)
            }
            Userback.sendNativeEvent(event)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        
        val event = JSONObject().apply {
            put("type", "network")
            put("url", request.url.toString())
            put("method", request.method)
            put("status", response.code)
            put("duration", duration)
            put("timestamp", startTime)
        }
        Userback.sendNativeEvent(event)

        return response
    }
}
