package com.lesego.electrical.utils

import android.util.Patterns
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Validates user inputs (email, password, phone, etc.).
 * Used by both LoginActivity and RegisterActivity.
 *
 * @author Lesego Electrical Team
 */
object ValidationUtils {

    /** Returns true if the email address is correctly formatted. */
    fun isValidEmail(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Returns true if the password meets minimum security requirements:
     *  - At least 8 characters
     *  - At least one uppercase letter
     *  - At least one digit
     *  - At least one special character
     */
    fun isStrongPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUpper && hasDigit && hasSpecial
    }

    /** Returns true if the SA phone number format is acceptable. */
    fun isValidPhone(phone: String): Boolean {
        val stripped = phone.replace(" ", "").replace("-", "")
        return stripped.length in 9..12 && stripped.all { it.isDigit() || it == '+' }
    }

    /** Returns true if the string is non-blank and under [maxLen] characters. */
    fun isValidText(text: String, maxLen: Int = 500): Boolean =
        text.isNotBlank() && text.length <= maxLen
}

/**
 * Utility for client-side password operations.
 *
 * NOTE: Firebase Auth handles server-side bcrypt hashing for stored passwords.
 * This class is used for additional client-side pre-processing (salted SHA-256
 * for any locally cached tokens — not for the auth password itself).
 *
 * @author Lesego Electrical Team
 */
object PasswordUtils {

    /**
     * Computes a SHA-256 hash of [input].
     * Used for local token caching, NOT for Firebase Auth passwords
     * (Firebase handles those with bcrypt server-side).
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns a password strength score 0–4:
     *  0 = very weak, 1 = weak, 2 = fair, 3 = strong, 4 = very strong
     */
    fun strength(password: String): Int {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return minOf(score, 4)
    }

    /** Human-readable label for a strength score. */
    fun strengthLabel(score: Int) = when (score) {
        0, 1 -> "Weak"
        2 -> "Fair"
        3 -> "Strong"
        else -> "Very Strong"
    }
}

/**
 * Extension functions for common view operations.
 */
fun android.view.View.show() { visibility = android.view.View.VISIBLE }
fun android.view.View.hide() { visibility = android.view.View.GONE }
fun android.view.View.invisible() { visibility = android.view.View.INVISIBLE }

/**
 * Extension to safely run a block only when the string is non-empty.
 */
fun String?.orDash(): String = if (isNullOrEmpty()) "—" else this

/**
 * Extension to format a price as a ZAR string.
 */
fun Double.toZar(): String = "R${String.format("%.2f", this)}"
