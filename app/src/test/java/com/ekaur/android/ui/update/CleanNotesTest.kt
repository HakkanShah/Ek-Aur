package com.ekaur.android.ui.update

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanNotesTest {

    @Test
    fun `markdown is flattened to readable lines`() {
        val notes = """
            ## What's new

            - **Guided setup** for the Restricted setting
            * Smoother tabs
            > quoted
        """.trimIndent()
        assertEquals(
            "What's new\n• Guided setup for the Restricted setting\n• Smoother tabs\nquoted",
            cleanNotes(notes),
        )
    }

    @Test
    fun `only the first six lines are kept`() {
        val notes = (1..10).joinToString("\n") { "line $it" }
        assertEquals((1..6).joinToString("\n") { "line $it" }, cleanNotes(notes))
    }

    @Test
    fun `blank notes stay blank`() {
        assertEquals("", cleanNotes("\n\n   \n"))
    }
}
