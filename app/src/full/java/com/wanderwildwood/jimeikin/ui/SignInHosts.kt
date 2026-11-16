package com.wanderwildwood.jimeikin.ui

/**
 * Where the YouTube sign-in WebView may go, page by page. Only whole-page navigations are
 * checked; what a page loads inside itself (scripts and images from www.gstatic.com,
 * pictures from googleusercontent.com, calls to googleapis.com) is left alone, since stopping
 * any of it could break the sign-in in ways nobody here can see without a Google account.
 *
 * Allowed:
 * - google.com and every host under it: accounts.google.com, where the sign-in itself, the
 *   two-step pages, security keys, passkeys and "try another way" all live; myaccount,
 *   consent.google.com and support.google.com, which its own links open.
 * - youtube.com and every host under it: accounts.youtube.com, which sets YouTube's sign-in
 *   cookie on the way back; consent.youtube.com; www.youtube.com; music.youtube.com, where it
 *   ends.
 * - accounts.google.<country> (accounts.google.co.uk, accounts.google.com.au, accounts.google.de
 *   and the like), which the sign-in can pass through to set the same cookie on a country
 *   domain. Only the accounts host: a two-letter country ending cannot be told from a
 *   look-alike as surely as .com can, so nothing else on those names is opened.
 *
 * Not allowed: anything else, which includes a company's own sign-in page for a Workspace
 * account that signs in elsewhere.
 */
object SignInHosts {

    private val WHOLE_DOMAINS = listOf("google.com", "youtube.com")
    private val COUNTRY_ACCOUNTS = Regex("^accounts\\.google\\.(?:co\\.|com\\.)?[a-z]{2}$")

    fun allowed(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        if (h.isEmpty()) return false
        if (WHOLE_DOMAINS.any { h == it || h.endsWith(".$it") }) return true
        return COUNTRY_ACCOUNTS.matches(h)
    }
}
