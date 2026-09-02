package com.example.loyaltyapp.core.session

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes an attendant PIN for offline verification (see [AttendantCredentialStore.verifyPinOffline]) —
 * the PIN itself is never stored. PBKDF2 iteration count only defends against an attacker with raw
 * file access; a 4-digit PIN's real protection against on-device brute force is the local lockout
 * in [AttendantCredentialStore], which mirrors the server's own 5-attempts/15-minute policy.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /** Constant-time comparison — never use `==`/`.equals()` on hash output. */
    fun matches(pin: String, saltBase64: String, expectedHashBase64: String): Boolean =
        MessageDigest.isEqual(hash(pin, saltBase64).toByteArray(), expectedHashBase64.toByteArray())
}
