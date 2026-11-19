package io.github.lacostej.playtime.auth

import kotlin.io.encoding.Base64

/** Cryptographically secure random bytes from the platform. */
expect fun secureRandomBytes(size: Int): ByteArray

expect fun sha256(data: ByteArray): ByteArray

private const val LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Random string of ASCII letters, as used for the PKCE verifier and the state. */
fun randomLetters(length: Int = 50): String =
    secureRandomBytes(length).map { LETTERS[(it.toInt() and 0xFF) % LETTERS.length] }.joinToString("")

/** PKCE S256 challenge: base64url(sha256(verifier)) without padding. */
fun pkceChallenge(verifier: String): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(sha256(verifier.encodeToByteArray()))
