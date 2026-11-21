package com.nayama.music.backend

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom OkHttp Downloader for NewPipeExtractor.
 * Implements exponential backoff retry, mobile-friendly timeouts,
 * and a standard modern browser User-Agent.
 */
class NewPipeDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        fun create(): NewPipeDownloader {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
            return NewPipeDownloader(client)
        }
    }

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var lastException: IOException? = null
        val maxRetries = 2

        for (attempt in 0..maxRetries) {
            try {
                val reqBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)

                for ((key, values) in headers) {
                    for (value in values) {
                        reqBuilder.addHeader(key, value)
                    }
                }

                val body = dataToSend?.toRequestBody(null)
                reqBuilder.method(httpMethod, body)

                val call = client.newCall(reqBuilder.build())
                val okResponse = call.execute()

                val responseBody = okResponse.body?.string().orEmpty()
                val responseHeaders = mutableMapOf<String, List<String>>()
                for (name in okResponse.headers.names()) {
                    responseHeaders[name] = okResponse.headers.values(name)
                }

                return Response(
                    okResponse.code,
                    okResponse.message,
                    responseHeaders,
                    responseBody,
                    okResponse.request.url.toString()
                )
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep((300L * (attempt + 1)))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        throw lastException ?: IOException("Failed to execute request to $url after retries")
    }
}
