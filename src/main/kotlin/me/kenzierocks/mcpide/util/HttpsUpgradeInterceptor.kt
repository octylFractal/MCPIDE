package me.kenzierocks.mcpide.util

import mu.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response

class HttpsUpgradeInterceptor : Interceptor {

    private val logger = KotlinLogging.logger { }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = chain.request().url
        if (!url.isHttps && response.code == 501 && response.message == "HTTPS Required") {
            logger.debug { "Upgrading $url to HTTPS and trying again after 501" }
            response.close()
            return chain.proceed(chain.request().newBuilder()
                .url(url.newBuilder().scheme("https").build())
                .build())
        }
        return response
    }
}
