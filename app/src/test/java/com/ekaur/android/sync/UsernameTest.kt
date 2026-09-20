package com.ekaur.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules have to agree with the database's `username_shape` check exactly.
 * Where they drift, the screen says a name is fine and the insert refuses it,
 * with nothing on the phone to explain why.
 */
class UsernameTest {

    @Test
    fun `a good name passes`() {
        assertNull(Username.problemWith("hakkan"))
        assertNull(Username.problemWith("rohit.99"))
        assertNull(Username.problemWith("shy_one"))
        assertNull(Username.problemWith("a1b"))
    }

    @Test
    fun `capitals are folded rather than rejected`() {
        // The database refuses capitals outright, so the field lower-cases as
        // the user types. Accepting one here would only be a lie to undo later.
        assertEquals("hakkan", Username.normalise("HaKkAn"))
        assertTrue(Username.isValid("HAKKAN"))
    }

    @Test
    fun `surrounding space does not count`() {
        assertEquals("hakkan", Username.normalise("  hakkan \n"))
        assertTrue(Username.isValid("  hakkan  "))
    }

    @Test
    fun `length is bounded at both ends`() {
        assertEquals(Username.Problem.TooShort, Username.problemWith("ab"))
        assertEquals(Username.Problem.TooLong, Username.problemWith("a".repeat(17)))
        assertNull(Username.problemWith("a".repeat(16)))
    }

    @Test
    fun `nothing may start with a separator`() {
        assertEquals(Username.Problem.BadStart, Username.problemWith("_nope"))
        assertEquals(Username.Problem.BadStart, Username.problemWith(".nope"))
    }

    @Test
    fun `only letters, digits and the two separators`() {
        assertEquals(Username.Problem.BadCharacter, Username.problemWith("has space"))
        assertEquals(Username.Problem.BadCharacter, Username.problemWith("emoji💀"))
        assertEquals(Username.Problem.BadCharacter, Username.problemWith("sla/sh"))
        assertEquals(Username.Problem.BadCharacter, Username.problemWith("da-sh"))
    }

    @Test
    fun `separators may not double up or trail`() {
        assertEquals(Username.Problem.DoubledSeparator, Username.problemWith("bad..er"))
        assertEquals(Username.Problem.DoubledSeparator, Username.problemWith("bad__er"))
        assertEquals(Username.Problem.DoubledSeparator, Username.problemWith("bad._er"))
        assertEquals(Username.Problem.TrailingSeparator, Username.problemWith("trail."))
        assertEquals(Username.Problem.TrailingSeparator, Username.problemWith("trail_"))
    }

    @Test
    fun `an empty field is its own case, not an error to shout about`() {
        assertEquals(Username.Problem.Empty, Username.problemWith(""))
        assertEquals(Username.Problem.Empty, Username.problemWith("   "))
        assertEquals(Username.Problem.Empty, Username.problemWith(null))
    }

    @Test
    fun `every problem has something to say`() {
        // A blank message under the field would leave the user with a rejected
        // name and no idea which rule they broke.
        for (problem in Username.Problem.entries) {
            assertTrue(problem.name, Username.message(problem).isNotBlank())
        }
    }

    @Test
    fun `an invalid name is never worth a network call`() {
        // The whole point of the first layer: these all resolve on the device.
        val rejects = listOf("ab", "_x", "has space", "bad..er", "trail.", "a".repeat(20))

        assertTrue(rejects.none { Username.isValid(it) })
        assertFalse(Username.isValid(null))
    }
}
