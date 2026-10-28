package com.webtoapp.core.webview





internal sealed class GoogleSignInAttempt {
    data class Ok(
        val idToken: String,
        val displayName: String,
        val profilePictureUri: String,
        val googleUserId: String,
    ) : GoogleSignInAttempt()

    data class Err(val code: String, val message: String) : GoogleSignInAttempt()
}
