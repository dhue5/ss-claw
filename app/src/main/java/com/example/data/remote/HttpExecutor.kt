package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HttpResponse(
    val statusCode: Int,
    val isSuccessful: Boolean,
    val body: String,
    val headers: Map<String, String>,
    val durationMs: Long
)

class HttpExecutor {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val requestBuilder = Request.Builder().url(url)

            headers.forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
            }

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val mediaType = (headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
                    val reqBody = (body ?: "{}").toRequestBody(mediaType)
                    requestBuilder.post(reqBody)
                }
                "PUT" -> {
                    val mediaType = (headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
                    val reqBody = (body ?: "{}").toRequestBody(mediaType)
                    requestBuilder.put(reqBody)
                }
                "DELETE" -> requestBuilder.delete()
                else -> requestBuilder.get()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            val respHeaders = mutableMapOf<String, String>()
            response.headers.forEach { pair ->
                respHeaders[pair.first] = pair.second
            }

            HttpResponse(
                statusCode = response.code,
                isSuccessful = response.isSuccessful,
                body = responseBody,
                headers = respHeaders,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            HttpResponse(
                statusCode = 0,
                isSuccessful = false,
                body = "Network Error: ${e.localizedMessage ?: e.message}",
                headers = emptyMap(),
                durationMs = duration
            )
        }
    }
}
