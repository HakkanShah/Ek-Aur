package com.ekaur.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCopyTest {

    @Test
    fun `every count has a dare to go with it`() {
        // The line is the only thing on the card doing any selling, so it must
        // never come out blank.
        val counts = listOf(0, 1, 49, 50, 99, 100, 199, 200, 499, 500, 5000)

        assertTrue(counts.all { CardCopy.challengeFor(it).isNotBlank() })
    }

    @Test
    fun `the dare gets bolder as the number does`() {
        assertEquals(CardCopy.challengeFor(10), CardCopy.challengeFor(49))
        assertTrue(CardCopy.challengeFor(500) != CardCopy.challengeFor(10))
    }

    @Test
    fun `one reel is not called reels`() {
        assertEquals("Reel today", CardCopy.subtitleFor(1))
        assertEquals("Reels today", CardCopy.subtitleFor(2))
        assertEquals("Reels today", CardCopy.subtitleFor(0))
    }

    @Test
    fun `the caption carries both the number and the link`() {
        val caption = CardSharing.captionFor(
            CardStats("hakkan", 969, 0, emptyList(), 969, null)
        )

        assertTrue(caption.contains("969"))
        assertTrue(caption.contains(CardCopy.LINK))
    }
}
