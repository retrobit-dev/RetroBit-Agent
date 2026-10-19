package dev.retrobit.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    private val mono = DesignSystem()

    @Test
    fun `default preset is grayscale`() {
        assertTrue(mono.isGrayscale())
    }

    @Test
    fun `legacy coloured token is detected`() {
        // The old pre-e-paper theme used a saturated indigo accent.
        assertFalse(mono.copy(accent = "#6C5CE7").isGrayscale())
        assertFalse(mono.copy(primary = "#FF00AA").isGrayscale())
    }

    @Test
    fun `near-neutral tokens still count as grayscale`() {
        // A 12-step channel spread is invisible on an e-paper ramp.
        assertTrue(mono.copy(background = "#101012").isGrayscale())
    }

    @Test
    fun `malformed hex does not report colour`() {
        assertTrue(mono.copy(muted = "not-a-colour").isGrayscale())
    }

    @Test
    fun `css block carries every token and the bundled font`() {
        val css = mono.cssVariables()
        listOf(
            "--rb-primary:${mono.primary}",
            "--rb-accent:${mono.accent}",
            "--rb-bg:${mono.background}",
            "--rb-surface:${mono.surface}",
            "--rb-text:${mono.textColor}",
            "--rb-muted:${mono.muted}",
            "--rb-radius:${mono.radius}px",
            "@font-face",
            "PixelOperator.ttf",
        ).forEach { assertTrue("missing $it in: $css", css.contains(it)) }
    }

    @Test
    fun `grayscale fallback replaces every colour token`() {
        val coloured = mono.copy(
            primary = "#6C5CE7",
            accent = "#00E5FF",
            background = "#1B1035",
            surface = "#2A1B4A",
            textColor = "#FFF7FF",
            muted = "#A28BC4",
        )
        assertFalse(coloured.isGrayscale())
        val snapped = coloured.grayscaleFallback()
        assertTrue(snapped.isGrayscale())
        assertEquals(snapped.primary, snapped.grayscaleFallback().primary)
    }

    @Test
    fun `fallback keeps radius and font`() {
        val snapped = mono.copy(primary = "#FF0000", radius = 12, font = "Inter").grayscaleFallback()
        assertEquals(12, snapped.radius)
        assertEquals("Inter", snapped.font)
    }
}
