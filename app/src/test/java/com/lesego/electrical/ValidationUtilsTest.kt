package com.lesego.electrical

import com.lesego.electrical.utils.ValidationUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ValidationUtils].
 *
 * These tests run on the JVM (no Android emulator needed) and verify
 * that all input validation logic works correctly before the app ships.
 *
 * Run with: ./gradlew test
 *
 * @author Lesego Electrical Team
 */
class ValidationUtilsTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Email validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `valid email addresses are accepted`() {
        assertTrue(ValidationUtils.isValidEmail("user@example.com"))
        assertTrue(ValidationUtils.isValidEmail("john.doe@gmail.com"))
        assertTrue(ValidationUtils.isValidEmail("support@lethaboelectric.co.za"))
        assertTrue(ValidationUtils.isValidEmail("USER@DOMAIN.COM"))
    }

    @Test
    fun `invalid email addresses are rejected`() {
        assertFalse(ValidationUtils.isValidEmail(""))
        assertFalse(ValidationUtils.isValidEmail("notanemail"))
        assertFalse(ValidationUtils.isValidEmail("missing@"))
        assertFalse(ValidationUtils.isValidEmail("@nodomain.com"))
        assertFalse(ValidationUtils.isValidEmail("spaces in@email.com"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Password strength
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `strong password passes all checks`() {
        assertTrue(ValidationUtils.isStrongPassword("Lesego@2024!"))
        assertTrue(ValidationUtils.isStrongPassword("Electric1c!an"))
        assertTrue(ValidationUtils.isStrongPassword("MyP@ssw0rd"))
    }

    @Test
    fun `weak passwords are rejected`() {
        assertFalse(ValidationUtils.isStrongPassword("short"))        // too short
        assertFalse(ValidationUtils.isStrongPassword("alllowercase1!"))  // no uppercase
        assertFalse(ValidationUtils.isStrongPassword("ALLUPPERCASE!"))   // no digit
        assertFalse(ValidationUtils.isStrongPassword("NoSpecial12"))     // no special char
    }

    @Test
    fun `password shorter than 8 characters is rejected`() {
        assertFalse(ValidationUtils.isStrongPassword("Ab1!"))
        assertFalse(ValidationUtils.isStrongPassword("Ab1!567"))  // exactly 7
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Phone validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `valid SA phone numbers are accepted`() {
        assertTrue(ValidationUtils.isValidPhone("0821234567"))
        assertTrue(ValidationUtils.isValidPhone("082 123 4567"))
        assertTrue(ValidationUtils.isValidPhone("+27821234567"))
    }

    @Test
    fun `invalid phone numbers are rejected`() {
        assertFalse(ValidationUtils.isValidPhone(""))
        assertFalse(ValidationUtils.isValidPhone("123"))          // too short
        assertFalse(ValidationUtils.isValidPhone("abcdefghij"))   // not digits
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Text validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-blank text within limit is valid`() {
        assertTrue(ValidationUtils.isValidText("Hello"))
        assertTrue(ValidationUtils.isValidText("A".repeat(500)))
    }

    @Test
    fun `blank or over-limit text is invalid`() {
        assertFalse(ValidationUtils.isValidText(""))
        assertFalse(ValidationUtils.isValidText("   "))
        assertFalse(ValidationUtils.isValidText("A".repeat(501)))
    }
}
