package org.example.playtime.auth

import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
