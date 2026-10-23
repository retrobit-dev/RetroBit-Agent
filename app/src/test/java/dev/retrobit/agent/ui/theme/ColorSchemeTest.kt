package dev.retrobit.agent.ui.theme

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** M3 defaults otherwise introduce purple/red on dialogs, menus and containers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ColorSchemeTest {
    @Test fun `all material roles keep e-paper grayscale in both themes`() {
        listOf(DarkColors, LightColors).forEach { s ->
            val colors = listOf(s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer,
                s.secondary, s.onSecondary, s.secondaryContainer, s.onSecondaryContainer,
                s.tertiary, s.onTertiary, s.tertiaryContainer, s.onTertiaryContainer,
                s.background, s.onBackground, s.surface, s.onSurface, s.surfaceVariant, s.onSurfaceVariant,
                s.error, s.onError, s.errorContainer, s.onErrorContainer, s.outline, s.outlineVariant,
                s.inverseSurface, s.inverseOnSurface, s.inversePrimary, s.surfaceTint, s.scrim,
                s.surfaceBright, s.surfaceDim, s.surfaceContainerLowest, s.surfaceContainerLow,
                s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
            colors.forEach { c ->
                assertTrue("Non-e-paper material color: $c", abs(c.red - c.green) <= 0.07f && abs(c.green - c.blue) <= 0.07f && abs(c.red - c.blue) <= 0.07f)
            }
        }
    }
}
