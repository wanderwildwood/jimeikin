package com.wanderwildwood.jimeikin.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.jimeikin.R
import com.wanderwildwood.jimeikin.forgetYouTubeWebSession
import com.wanderwildwood.jimeikin.youTubeAccount

private const val YOUTUBE_MUSIC_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"
private const val YOUTUBE_MUSIC_COOKIE_URL = "https://music.youtube.com"

/**
 * Lets the user sign in to their own YouTube account by loading Google's real
 * sign-in page in a WebView. The user enters their credentials directly into
 * Google's page; this screen only reads back the resulting session cookie
 * from [CookieManager] once the WebView lands on a signed-in music.youtube.com
 * page, then hands that cookie to [onLoginSuccess].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginSuccess: (cookie: String) -> Unit,
    onBackClick: () -> Unit,
) {
    var isCompletingLogin by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    // Set when the page tried to go somewhere outside Google and YouTube, which is not opened.
    var refusedElsewhere by remember { mutableStateOf(false) }
    var webView: WebView? = null

    fun completeLoginIfSignedIn() {
        if (isCompletingLogin) return
        val cookie = CookieManager.getInstance().getCookie(YOUTUBE_MUSIC_COOKIE_URL).orEmpty()
        if (cookie.isBlank() || !cookie.contains("SAPISID")) return

        isCompletingLogin = true
        webView?.apply {
            stopLoading()
            clearHistory()
        }
        onLoginSuccess(cookie)
        // The cookie is kept sealed from here on (see YouTubeMusic.Account); the WebView's own
        // cookie store would otherwise keep a second, unsealed copy of the whole Google session.
        forgetYouTubeWebSession()
    }

    if (rendererGone) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TextMMD(
                text = stringResource(R.string.library_youtube_login_failed),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            ButtonMMD(onClick = {
                rendererGone = false
                retryKey++
            }) {
                TextMMD(text = stringResource(R.string.library_youtube_login_retry))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
        if (refusedElsewhere) {
            TextMMD(
                text = stringResource(R.string.library_youtube_login_elsewhere),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        key(retryKey) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            // https pages only: nothing in plain http, and no other kind of
                            // address (intent:, file:, content:, javascript:) is opened here.
                            // A whole page is opened only on Google and YouTube (SignInHosts);
                            // what a page loads inside itself is left alone.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.scheme != "https") return true
                                if (request.isForMainFrame && !SignInHosts.allowed(request.url.host)) {
                                    refusedElsewhere = true
                                    return true
                                }
                                if (request.isForMainFrame) refusedElsewhere = false
                                return false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                if (url != null && android.net.Uri.parse(url).host == YOUTUBE_MUSIC_HOST) {
                                    completeLoginIfSignedIn()
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                webView = null
                                rendererGone = true
                                return true
                            }
                        }
                        settings.apply {
                            // Google's sign-in needs JavaScript; nothing on the page can reach
                            // the app (no JavaScript interface) or the phone's files.
                            javaScriptEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setGeolocationEnabled(false)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        webView = this
                        loadUrl(YOUTUBE_MUSIC_LOGIN_URL)
                    }
                },
            )
        }
        }
    }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            onBackClick()
        }
    }
}

/** Signs in to YouTube Music, for the account row in Settings; see [YouTubeLoginScreen]. */
@Composable
fun OnlineSignInScreen(
    onDone: () -> Unit,
    onBackClick: () -> Unit,
) {
    val account = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.wanderwildwood.jimeikin.CalmMusic)
        .youTubeAccount
    YouTubeLoginScreen(
        onLoginSuccess = { cookie ->
            account.set(cookie)
            onDone()
        },
        onBackClick = onBackClick,
    )
}
