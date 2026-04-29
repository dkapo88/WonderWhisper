package com.slumdog88.dictationkeyboardai.network

import com.slumdog88.dictationkeyboardai.BuildConfig
import okhttp3.Request

object GroqProxyConfig {
    private const val DIRECT_BASE_URL = "https://api.groq.com"
    private const val OPENAI_PREFIX = "/openai/v1"

    fun isConfigured(): Boolean = BuildConfig.GROQ_PROXY_BASE_URL.isNotBlank()

    fun directBaseUrl(): String = DIRECT_BASE_URL

    fun shouldUseProxy(userApiKey: String?): Boolean {
        return userApiKey.isNullOrBlank() && isConfigured()
    }

    fun baseUrlFor(useProxy: Boolean): String {
        return if (useProxy) {
            BuildConfig.GROQ_PROXY_BASE_URL.trimEnd('/')
        } else {
            DIRECT_BASE_URL
        }
    }

    fun endpoint(path: String, useProxy: Boolean): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "${baseUrlFor(useProxy)}$normalizedPath"
    }

    fun modelsEndpoint(serviceUsesProxy: Boolean): String {
        return if (serviceUsesProxy && isConfigured()) {
            "${BuildConfig.GROQ_PROXY_BASE_URL.trimEnd('/')}$OPENAI_PREFIX/models"
        } else {
            "$DIRECT_BASE_URL$OPENAI_PREFIX/models"
        }
    }

    fun applyHeaders(builder: Request.Builder, userApiKey: String?, useProxy: Boolean): Request.Builder {
        if (!useProxy && !userApiKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $userApiKey")
            return builder
        }

        if (useProxy) {
            val proxyToken = BuildConfig.GROQ_PROXY_APP_TOKEN
            if (proxyToken.isNotBlank()) {
                builder.addHeader("X-App-Proxy-Token", proxyToken)
            }
        }

        return builder
    }
}
