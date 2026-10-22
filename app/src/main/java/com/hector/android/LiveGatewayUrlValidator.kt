package com.hector.android

import java.net.URI

/**
 * Accepts only the HTTPS Cloudflare Worker live page; rejects credentials,
 * alternate paths, query parameters, fragments, and malformed ports.
 */
internal object LiveGatewayUrlValidator {
    fun isValid(value: String): Boolean = try {
        val uri = URI(value.trim())
        uri.isAbsolute &&
            !uri.isOpaque &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port in 1..65535) &&
            uri.rawPath == "/live" &&
            uri.rawQuery == null &&
            uri.rawFragment == null
    } catch (_: Exception) {
        false
    }
}
