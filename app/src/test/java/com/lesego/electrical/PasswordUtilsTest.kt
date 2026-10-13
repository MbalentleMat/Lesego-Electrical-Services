package com.lesego.electrical

import com.lesego.electrical.utils.PasswordUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for [PasswordUtils].
 *
 * @author Lesego Electrical Team
 */
class PasswordUtilsTest {

    @Test
    fun `sha256 produces 64-char hex string`() {
        val hash = PasswordUtils.sha256("test")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256 is deterministic`() {
        val hash1 = PasswordUtils.sha256("lesego")
        val hash2 = PasswordUtils.sha256("lesego")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different inputs produce different hashes`() {
        assertNotEquals(PasswordUtils.sha256("password1"), PasswordUtils.sha256("password2"))
    }

    @Test
    fun `very weak password scores 0 or 1`() {
        val score = PasswordUtils.strength("abc")
        assertTrue(score <= 1)
    }

    @Test
    fun `strong password scores 4`() {
        val score = PasswordUtils.strength("MyStr0ng!Pass")
        assertEquals(4, score)
    }

    @Test
    fun `strength label maps correctly`() {
        assertEquals("Weak", PasswordUtils.strengthLabel(0))
        assertEquals("Weak", PasswordUtils.strengthLabel(1))
        assertEquals("Fair", PasswordUtils.strengthLabel(2))
        assertEquals("Strong", PasswordUtils.strengthLabel(3))
        assertEquals("Very Strong", PasswordUtils.strengthLabel(4))
    }

    private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
}
