package com.wanderwildwood.jimeikin.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInHostsTest {

    @Test fun googleAndYouTubeHostsAreAllowed() {
        listOf(
            "accounts.google.com", "google.com", "myaccount.google.com", "consent.google.com",
            "support.google.com", "accounts.youtube.com", "consent.youtube.com", "www.youtube.com",
            "music.youtube.com", "youtube.com", "ACCOUNTS.GOOGLE.COM", "accounts.google.com.",
            "accounts.google.co.uk", "accounts.google.com.au", "accounts.google.de",
        ).forEach { assertTrue(it, SignInHosts.allowed(it)) }
    }

    @Test fun lookAlikesAreRefused() {
        listOf(
            "google.com.evil.example", "accounts.google.com.evil.example", "evilgoogle.com",
            "google.com-login.example", "youtube.com.evil.example", "notyoutube.com",
            "accounts-google.com", "www.google.co.uk", "google.de", "accounts.google.evil",
            "accounts.google.co.uk.evil.example", "googleusercontent.com", "www.gstatic.com",
            "accounts.google.c0.uk", "", "sso.example.org",
        ).forEach { assertFalse(it, SignInHosts.allowed(it)) }
        assertFalse(SignInHosts.allowed(null))
    }
}
