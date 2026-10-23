package dev.retrobit.agent.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * RetroBit pixel icon set.
 *
 * Every glyph is authored on a 12x12 grid and drawn with 2x2 unit blocks in a
 * 24x24 viewport, so strokes stay thick and readable from 14 dp up. Shapes share
 * one optical weight and a consistent silhouette.
 *
 * Regenerate with `python3 tools/pixel_icons.py kotlin`.
 */
object PixelIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
    val Home: ImageVector by lazy { icon("Home", "M8 2h8v2h-8zM6 4h12v2h-12zM4 6h16v2h-16zM2 8h20v2h-20zM0 10h24v2h-24zM2 12h20v2h-20zM2 14h6v6h-6zM16 14h6v6h-6zM2 20h20v2h-20z") }
    val Search: ImageVector by lazy { icon("Search", "M6 2h8v2h-8zM4 4h2v2h-2zM14 4h2v2h-2zM2 6h2v6h-2zM16 6h2v6h-2zM4 12h2v2h-2zM14 12h2v2h-2zM6 14h8v2h-8zM16 16h4v2h-4zM18 18h4v2h-4zM20 20h4v4h-4z") }
    val Plus: ImageVector by lazy { icon("Plus", "M10 2h4v8h-4zM2 10h20v4h-20zM10 14h4v8h-4z") }
    val ArrowLeft: ImageVector by lazy { icon("ArrowLeft", "M8 4h4v2h-4zM6 6h4v2h-4zM4 8h4v2h-4zM2 10h22v4h-22zM4 14h4v2h-4zM6 16h4v2h-4zM8 18h4v2h-4z") }
    val ArrowRight: ImageVector by lazy { icon("ArrowRight", "M12 4h4v2h-4zM14 6h4v2h-4zM16 8h4v2h-4zM0 10h22v4h-22zM16 14h4v2h-4zM14 16h4v2h-4zM12 18h4v2h-4z") }
    val Sparkles: ImageVector by lazy { icon("Sparkles", "M18 0h4v2h-4zM16 2h8v2h-8zM8 4h4v2h-4zM18 4h4v2h-4zM6 6h8v2h-8zM4 8h12v2h-12zM2 10h16v2h-16zM0 12h20v2h-20zM2 14h16v2h-16zM4 16h12v2h-12zM6 18h8v2h-8zM8 20h4v2h-4z") }
    val Comment: ImageVector by lazy { icon("Comment", "M2 2h20v2h-20zM0 4h24v12h-24zM2 16h12v2h-12zM4 18h8v2h-8zM4 20h4v2h-4z") }
    val Check: ImageVector by lazy { icon("Check", "M18 4h4v2h-4zM16 6h6v2h-6zM14 8h6v2h-6zM12 10h6v2h-6zM2 12h4v2h-4zM10 12h6v2h-6zM2 14h12v2h-12zM4 16h8v2h-8zM6 18h4v2h-4z") }
    val Close: ImageVector by lazy { icon("Close", "M2 4h4v2h-4zM18 4h4v2h-4zM2 6h6v2h-6zM16 6h6v2h-6zM4 8h6v2h-6zM14 8h6v2h-6zM6 10h12v2h-12zM8 12h8v2h-8zM6 14h12v2h-12zM4 16h6v2h-6zM14 16h6v2h-6zM2 18h6v2h-6zM16 18h6v2h-6zM2 20h4v2h-4zM18 20h4v2h-4z") }
    val Code: ImageVector by lazy { icon("Code", "M4 4h4v2h-4zM16 4h4v2h-4zM2 6h4v2h-4zM18 6h4v2h-4zM0 8h4v4h-4zM20 8h4v4h-4zM2 12h4v2h-4zM18 12h4v2h-4zM4 14h4v2h-4zM16 14h4v2h-4z") }
    val Copy: ImageVector by lazy { icon("Copy", "M8 2h16v2h-16zM8 4h2v4h-2zM2 8h14v2h-14zM22 4h2v10h-2zM14 10h2v4h-2zM14 14h10v2h-10zM2 10h2v8h-2zM14 16h2v2h-2zM2 18h14v2h-14z") }
    val Layout: ImageVector by lazy { icon("Layout", "M0 2h24v2h-24zM0 4h2v4h-2zM22 4h2v4h-2zM0 8h24v2h-24zM0 10h2v8h-2zM10 10h4v8h-4zM22 10h2v8h-2zM0 18h24v2h-24z") }
    val Trash: ImageVector by lazy { icon("Trash", "M8 2h8v2h-8zM4 4h16v2h-16zM2 6h20v2h-20zM8 8h4v8h-4zM16 8h2v8h-2zM22 8h2v8h-2zM2 8h2v10h-2zM20 16h2v2h-2zM4 18h16v2h-16z") }
    val FileText: ImageVector by lazy { icon("FileText", "M2 2h20v2h-20zM8 6h8v2h-8zM20 4h2v6h-2zM8 10h12v2h-12zM22 10h2v2h-2zM8 14h8v2h-8zM2 4h2v16h-2zM20 12h2v8h-2zM8 18h4v2h-4zM2 20h20v2h-20z") }
    val Download: ImageVector by lazy { icon("Download", "M10 2h4v8h-4zM4 10h16v2h-16zM6 12h12v2h-12zM8 14h8v2h-8zM10 16h4v2h-4zM2 20h2v2h-2zM20 20h2v2h-2zM2 22h20v2h-20z") }
    val Move: ImageVector by lazy { icon("Move", "M10 0h4v2h-4zM8 2h8v2h-8zM6 4h12v2h-12zM0 8h4v2h-4zM20 8h4v2h-4zM0 10h8v2h-8zM16 10h8v2h-8zM0 12h4v2h-4zM20 12h4v2h-4zM10 6h4v10h-4zM6 16h12v2h-12zM8 18h8v2h-8zM10 20h4v2h-4z") }
    val Pencil: ImageVector by lazy { icon("Pencil", "M20 2h4v2h-4zM18 4h4v4h-4zM16 8h6v2h-6zM14 10h6v2h-6zM12 12h6v2h-6zM10 14h6v2h-6zM8 16h8v2h-8zM6 18h10v2h-10zM6 20h8v2h-8zM8 22h4v2h-4z") }
    val Warning: ImageVector by lazy { icon("Warning", "M10 2h4v2h-4zM8 4h8v4h-8zM6 8h12v2h-12zM6 10h4v2h-4zM14 10h4v2h-4zM4 12h6v4h-6zM14 12h6v4h-6zM2 16h6v4h-6zM16 16h6v4h-6zM10 18h4v2h-4zM0 20h24v2h-24z") }
    val ChevronUp: ImageVector by lazy { icon("ChevronUp", "M10 6h4v2h-4zM8 8h8v2h-8zM6 10h12v2h-12zM4 12h6v2h-6zM14 12h6v2h-6zM2 14h6v2h-6zM16 14h6v2h-6zM0 16h4v2h-4zM20 16h4v2h-4z") }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M0 6h4v2h-4zM20 6h4v2h-4zM2 8h6v2h-6zM16 8h6v2h-6zM4 10h6v2h-6zM14 10h6v2h-6zM6 12h12v2h-12zM8 14h8v2h-8zM10 16h4v2h-4z") }
    val Folder: ImageVector by lazy { icon("Folder", "M4 2h10v2h-10zM2 4h14v2h-14zM0 6h24v12h-24zM2 18h20v2h-20z") }
    val Grid: ImageVector by lazy { icon("Grid", "M2 2h8v8h-8zM14 2h8v8h-8zM2 12h8v8h-8zM14 12h8v8h-8z") }
    val Key: ImageVector by lazy { icon("Key", "M8 2h8v2h-8zM6 4h2v4h-2zM16 4h2v4h-2zM8 8h8v2h-8zM16 14h4v4h-4zM10 10h4v14h-4zM16 20h4v4h-4z") }
    val Lock: ImageVector by lazy { icon("Lock", "M8 2h8v2h-8zM6 4h2v6h-2zM16 4h2v6h-2zM2 10h20v2h-20zM10 14h4v4h-4zM2 12h2v8h-2zM20 12h2v8h-2zM2 20h20v2h-20z") }
    val MoreVertical: ImageVector by lazy { icon("MoreVertical", "M10 0h4v4h-4zM10 8h4v4h-4zM10 16h4v4h-4z") }
    val Palette: ImageVector by lazy { icon("Palette", "M8 2h8v2h-8zM4 4h16v2h-16zM4 6h2v2h-2zM18 6h2v2h-2zM10 6h4v4h-4zM2 8h4v2h-4zM18 8h4v2h-4zM2 10h20v4h-20zM2 14h4v2h-4zM18 14h4v2h-4zM10 14h4v4h-4zM4 16h2v2h-2zM18 16h2v2h-2zM4 18h16v2h-16zM8 20h8v2h-8z") }
    val Preview: ImageVector by lazy { icon("Preview", "M0 2h24v2h-24zM0 4h2v10h-2zM22 4h2v10h-2zM0 14h24v2h-24zM8 16h8v2h-8zM4 18h16v2h-16z") }
    val Play: ImageVector by lazy { icon("Play", "M4 4h4v2h-4zM4 6h8v2h-8zM4 8h12v2h-12zM4 10h16v4h-16zM4 14h12v2h-12zM4 16h8v2h-8zM4 18h4v2h-4z") }
    val Reload: ImageVector by lazy { icon("Reload", "M8 2h8v2h-8zM6 4h2v2h-2zM16 4h2v2h-2zM20 4h4v2h-4zM18 6h6v2h-6zM4 6h2v4h-2zM20 8h4v2h-4zM2 10h2v4h-2zM22 14h2v2h-2zM4 14h2v4h-2zM20 16h2v2h-2zM6 18h2v2h-2zM18 18h2v2h-2zM8 20h10v2h-10z") }
    val Undo: ImageVector by lazy { icon("Undo", "M4 2h4v2h-4zM2 4h4v2h-4zM0 6h20v4h-20zM2 10h4v2h-4zM16 10h4v2h-4zM4 12h4v2h-4zM18 12h4v6h-4z") }
    val Send: ImageVector by lazy { icon("Send", "M18 4h4v2h-4zM16 6h8v2h-8zM12 8h12v2h-12zM0 10h24v4h-24zM12 14h12v2h-12zM16 16h8v2h-8zM18 18h4v2h-4z") }
    val Star: ImageVector by lazy { icon("Star", "M10 2h4v2h-4zM8 4h8v4h-8zM0 8h24v2h-24zM2 10h20v4h-20zM4 14h16v2h-16zM4 16h6v2h-6zM14 16h6v2h-6zM2 18h4v2h-4zM18 18h4v2h-4zM0 20h4v2h-4zM20 20h4v2h-4z") }
    val Stop: ImageVector by lazy { icon("Stop", "M4 4h16v16h-16z") }
    val Database: ImageVector by lazy { icon("Database", "M4 2h16v2h-16zM2 4h2v2h-2zM20 4h2v2h-2zM4 6h16v2h-16zM2 8h2v4h-2zM20 8h2v4h-2zM4 12h16v2h-16zM2 14h2v4h-2zM20 14h2v4h-2zM4 18h16v2h-16z") }
    val Terminal: ImageVector by lazy { icon("Terminal", "M2 2h20v2h-20zM6 6h4v2h-4zM8 8h4v2h-4zM10 10h4v2h-4zM8 12h4v2h-4zM20 4h2v12h-2zM6 14h4v2h-4zM2 4h2v14h-2zM14 16h6v2h-6zM22 16h2v2h-2zM2 18h20v2h-20z") }
    val Eye: ImageVector by lazy { icon("Eye", "M6 4h12v2h-12zM4 6h2v2h-2zM18 6h2v2h-2zM2 8h2v2h-2zM20 8h2v2h-2zM0 10h2v4h-2zM22 10h2v4h-2zM8 8h8v8h-8zM2 14h2v2h-2zM20 14h2v2h-2zM4 16h2v2h-2zM18 16h2v2h-2zM6 18h12v2h-12z") }
    val EyeOff: ImageVector by lazy { icon("EyeOff", "M20 0h4v2h-4zM18 2h4v2h-4zM6 4h6v2h-6zM16 4h4v2h-4zM4 6h2v2h-2zM14 6h4v2h-4zM2 8h2v2h-2zM12 8h4v2h-4zM20 8h2v2h-2zM10 10h4v2h-4zM0 10h2v4h-2zM22 10h2v4h-2zM8 12h4v2h-4zM16 12h2v2h-2zM6 14h4v2h-4zM14 14h2v2h-2zM20 14h2v2h-2zM4 16h4v2h-4zM18 16h2v2h-2zM2 18h4v2h-4zM10 18h8v2h-8zM0 20h4v2h-4zM0 22h2v2h-2z") }
    val Cloud: ImageVector by lazy { icon("Cloud", "M8 4h8v2h-8zM6 6h12v2h-12zM4 8h16v2h-16zM2 10h20v2h-20zM0 12h24v6h-24zM2 18h20v2h-20z") }
    val Info: ImageVector by lazy { icon("Info", "M8 2h8v2h-8zM6 4h2v2h-2zM16 4h2v2h-2zM4 6h2v4h-2zM10 6h4v4h-4zM18 6h2v4h-2zM2 10h2v6h-2zM20 10h2v6h-2zM10 12h4v6h-4zM4 16h2v2h-2zM18 16h2v2h-2zM6 18h2v2h-2zM16 18h2v2h-2zM8 20h8v2h-8z") }
    val Menu: ImageVector by lazy { icon("Menu", "M0 6h24v4h-24zM0 12h24v4h-24zM0 18h24v4h-24z") }
    val Apple: ImageVector by lazy { icon("Apple", "M12 2h6v2h-6zM10 4h6v2h-6zM10 6h4v2h-4zM6 8h6v2h-6zM14 8h6v2h-6zM4 10h18v2h-18zM2 12h22v6h-22zM4 18h18v2h-18zM6 20h14v2h-14z") }
    val Android: ImageVector by lazy { icon("Android", "M4 4h2v2h-2zM18 4h2v2h-2zM6 6h4v2h-4zM14 6h4v2h-4zM4 8h16v2h-16zM2 10h20v2h-20zM2 12h4v4h-4zM10 12h4v4h-4zM18 12h4v4h-4zM2 16h20v2h-20zM4 18h16v2h-16z") }
    val Browser: ImageVector by lazy { icon("Browser", "M2 2h20v2h-20zM2 4h2v2h-2zM6 4h4v2h-4zM20 4h2v2h-2zM2 6h20v2h-20zM2 8h2v10h-2zM20 8h2v10h-2zM2 18h20v2h-20z") }
    val Wifi: ImageVector by lazy { icon("Wifi", "M8 4h8v2h-8zM6 6h2v2h-2zM16 6h2v2h-2zM4 8h2v2h-2zM10 8h4v2h-4zM18 8h2v2h-2zM2 10h2v2h-2zM8 10h2v2h-2zM14 10h2v2h-2zM20 10h2v2h-2zM0 12h2v2h-2zM22 12h2v2h-2zM10 14h4v4h-4z") }
}
