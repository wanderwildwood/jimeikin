package com.wanderwildwood.jimeikin.data

/**
 * Whether an address the reader typed is plain http to a host out on the internet.
 *
 * An address typed without a scheme is taken as https, so plain http only happens when the
 * reader types `http://` themselves. For a server or station at home that is ordinary, and it
 * needs no word from us; for one anywhere else, anyone on the path can read what goes over it,
 * and the screen says so in plain words.
 */
object PlainHttp {

    /** True when [typed] starts with http:// and names a host that is not on a home network. */
    fun onOpenNetwork(typed: String): Boolean {
        val t = typed.trim()
        if (!t.startsWith("http://", ignoreCase = true)) return false
        val host = hostOf(t.substring("http://".length)) ?: return false
        return !isHomeHost(host)
    }

    /** Whether an address has a scheme at all. */
    fun hasScheme(typed: String): Boolean = "://" in typed.trim()

    private fun hostOf(rest: String): String? {
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        if (authority.isEmpty()) return null
        val host = if (authority.startsWith("[")) authority.substringAfter('[').substringBefore(']')
        else authority.substringBefore(':')
        return host.lowercase().trimEnd('.').ifEmpty { null }
    }

    internal fun isHomeHost(host: String): Boolean {
        if (host == "localhost") return true
        if (':' in host) {
            // IPv6: loopback, unique-local fc00::/7, link-local fe80::/10.
            return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") ||
                host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
        }
        val octets = host.split('.')
        if (octets.size == 4 && octets.all { o -> o.isNotEmpty() && o.all(Char::isDigit) && o.toInt() in 0..255 }) {
            val (a, b) = octets[0].toInt() to octets[1].toInt()
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        // A bare name ("navidrome") or a home-network suffix.
        return '.' !in host || listOf(".local", ".lan", ".home", ".internal", ".home.arpa", ".ts.net").any { host.endsWith(it) }
    }
}
