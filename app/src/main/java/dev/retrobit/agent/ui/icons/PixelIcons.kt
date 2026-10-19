package dev.retrobit.agent.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pixel-art icon set adapted from pixelarticons v2.4.2 (MIT, Gerrit Halfmann).
 * Each icon is a 24x24 ImageVector tinted by Icon().
 */
object PixelIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { path ->
                addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            }
        }.build()

    val Home: ImageVector by lazy { icon("Home", "M4 20h16v2H4zm16-10h2v10h-2zM2 10h2v10H2zm2-2h2v2H4zm2-2h2v2H6zm2-2h2v2H8zm2-2h4v2h-4zm4 2h2v2h-2zm2 2h2v2h-2zm2 2h2v2h-2zM8 14h2v6H8zm2-2h4v2h-4zm4 2h2v6h-2z") }
    val Search: ImageVector by lazy { icon("Search", "M22 22h-2v-2h2v2Zm-2-2h-2v-2h2v2Zm-6-2H6v-2h8v2Zm4 0h-2v-2h2v2ZM6 16H4v-2h2v2Zm10 0h-2v-2h2v2ZM4 14H2V6h2v8Zm14 0h-2V6h2v8ZM6 6H4V4h2v2Zm10 0h-2V4h2v2Zm-2-2H6V2h8v2Z") }
    val Plus: ImageVector by lazy { icon("Plus", "M13 11h7v2h-7v7h-2v-7H4v-2h7V4h2v7Z") }
    val ArrowLeft: ImageVector by lazy { icon("ArrowLeft", "M20 11v2H4v-2zM8 13v2H6v-2zm2 2v2H8v-2zm2 2v2h-2v-2zm-4-6V9H6v2z", "M10 15V7H8v8zm2 2V5h-2v12z") }
    val ArrowRight: ImageVector by lazy { icon("ArrowRight", "M4 11v2h16v-2zm12 2v2h2v-2zm-2 2v2h2v-2zm-2 2v2h2v-2zm4-6V9h2v2z", "M14 15V7h2v8zm-2 2V5h2v12z") }
    val Sparkles: ImageVector by lazy { icon("Sparkles", "M11 2h2v4h-2zM9 6h6v4H9zM5 10h14v4H5zM9 14h6v4H9zM11 18h2v4h-2zM18 4h2v2h-2z") }
    val Comment: ImageVector by lazy { icon("Comment", "M4 2h16v2H4zm0 14h14v2H4zM2 4h2v12H2zm18 0h2v18h-2zm-2 14h2v2h-2z") }
    val Check: ImageVector by lazy { icon("Check", "M10 18H8v-2h2v2Zm-2-2H6v-2h2v2Zm4-2v2h-2v-2h2Zm-6 0H4v-2h2v2Zm8 0h-2v-2h2v2Zm2-2h-2v-2h2v2Zm2-2h-2V8h2v2Zm2-2h-2V6h2v2Z") }
    val CheckDouble: ImageVector by lazy { icon("CheckDouble", "M7 18H5v-2h2v2Zm6 0h-2v-2h2v2Zm-8-2H3v-2h2v2Zm4 0H7v-2h2v2Zm6-2v2h-2v-2h2ZM3 14H1v-2h2v2Zm8 0H9v-2h2v2Zm6 0h-2v-2h2v2Zm-4-2h-2v-2h2v2Zm6 0h-2v-2h2v2Zm-4-2h-2V8h2v2Zm6 0h-2V8h2v2Zm-4-2h-2V6h2v2Zm6 0h-2V6h2v2Z") }
    val Close: ImageVector by lazy { icon("Close", "M7 19H5V17H7V19ZM19 19H17V17H19V19ZM9 15V17H7V15H9ZM17 17H15V15H17V17ZM11 15H9V13H11V15ZM15 15H13V13H15V15ZM13 13H11V11H13V13ZM11 11H9V9H11V11ZM15 11H13V9H15V11ZM9 9H7V7H9V9ZM17 9H15V7H17V9ZM7 7H5V5H7V7ZM19 7H17V5H19V7Z") }
    val Code: ImageVector by lazy { icon("Code", "M11 18H9v-4h2v4Zm-4-1H5v-2h2v2Zm12-2v2h-2v-2h2ZM5 15H3v-2h2v2Zm16 0h-2v-2h2v2Zm-8-1h-2v-4h2v4ZM3 13H1v-2h2v2Zm20 0h-2v-2h2v2ZM5 11H3V9h2v2Zm16 0h-2V9h2v2Zm-6-1h-2V6h2v4ZM7 9H5V7h2v2Zm12 0h-2V7h2v2Z") }
    val Copy: ImageVector by lazy { icon("Copy", "M8 6h12v2H8zM4 2h12v2H4zm2 6h2v12H6zM2 4h2v12H2zm6 16h12v2H8zM20 8h2v12h-2zm-4-4h2v2h-2zM4 16h2v2H4z") }
    val Layout: ImageVector by lazy { icon("Layout", "M20 20H4v-2h4v-8H4v8H2V6h2v2h16V6h2v12h-2v-8H10v8h10v2Zm0-14H4V4h16v2Z") }
    val Trash: ImageVector by lazy { icon("Trash", "M18 22H6V20H18V22ZM9 6H15V4H17V6H22V8H20V20H18V8H6V20H4V8H2V6H7V4H9V6ZM15 4H9V2H15V4Z") }
    val FileText: ImageVector by lazy { icon("FileText", "M6 4H4v16h2zm10-2H6v2h10zm4 4h-2v14h2zm-2 14H6v2h12zM16 4h2v2h-2zm-4 0h2v6h-2z", "M12 8h6v2h-6zm-4 8h8v2H8zm0-4h8v2H8zm0-4h2v2H8z") }
    val Download: ImageVector by lazy { icon("Download", "M21 15v4h-2v-4zm-2 4v2H5v-2zM5 15v4H3v-4zm8-12v14h-2V3z", "M7 11v2h10v-2zm2 2v2h2v-2zm4 0v2h2v-2z", "M15 11v2h2v-2z") }
    val Move: ImageVector by lazy { icon("Move", "M13 2h2v2h2v2h-4v5h5V7h2v2h2v2h2v2h-2v2h-2v2h-2v-4h-5v5h4v2h-2v2h-2v2h-2v-2H9v-2H7v-2h4v-5H6v4H4v-2H2v-2H0v-2h2V9h2V7h2v4h5V6H7V4h2V2h2V0h2v2Z") }
    val Pencil: ImageVector by lazy { icon("Pencil", "M4 16H6V18H8V20H10V22H2V14H4V16ZM12 20H10V18H12V20ZM14 18H12V16H14V18ZM10 16H8V14H10V16ZM16 16H14V14H16V16ZM6 14H4V12H6V14ZM12 14H10V12H12V14ZM18 14H16V12H18V14ZM8 12H6V10H8V12ZM14 12H12V10H14V12ZM20 12H18V10H20V12ZM10 10H8V8H10V10ZM18 10H16V8H18V10ZM22 10H20V8H22V10ZM12 8H10V6H12V8ZM16 8H14V6H16V8ZM20 8H18V6H20V8ZM14 6H12V4H14V6ZM18 6H16V4H18V6ZM16 4H14V2H16V4Z") }
    val Warning: ImageVector by lazy { icon("Warning", "M2 10h2v2H2zm0 4h2v-2H2zm20-4h-2v2h2zm0 4h-2v-2h2zM4 8h2v2H4zm0 8h2v-2H4zm16-8h-2v2h2zm0 8h-2v-2h2zM6 6h2v2H6zm0 12h2v-2H6zM18 6h-2v2h2zm0 12h-2v-2h2zM8 4h2v2H8zm0 16h2v-2H8zm8-16h-2v2h2zm0 16h-2v-2h2zM10 2h2v2h-2zm0 20h2v-2h-2zm4-20h-2v2h2zm0 20h-2v-2h2zm-3-5h2v-2h-2zm0-4h2V7h-2z") }
    val ChevronUp: ImageVector by lazy { icon("ChevronUp", "M13 8h-2v2h2V8Zm-2 2H9v2h2v-2Zm4 0h-2v2h2v-2Zm-6 2H7v2h2v-2Zm8 0h-2v2h2v-2ZM7 14H5v2h2v-2Zm12 0h-2v2h2v-2Z") }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M13 16h-2v-2h2v2Zm-2-2H9v-2h2v2Zm4 0h-2v-2h2v2Zm-6-2H7v-2h2v2Zm8 0h-2v-2h2v2ZM7 10H5V8h2v2Zm12 0h-2V8h2v2Z") }
    val Folder: ImageVector by lazy { icon("Folder", "M4 4h6v2H4zm0 14h16v2H4zM20 8h2v10h-2zM2 6h2v12H2zm8 0h10v2H10z") }
    val Grid: ImageVector by lazy { icon("Grid", "M4 2h16v2H4zM2 4h2v16H2zm2 7h16v2H4zm16-7h2v16h-2z", "M11 4h2v18h-2z", "M4 20h16v2H4z") }
    val Key: ImageVector by lazy { icon("Key", "M11 18H3V16H11V18ZM23 15H21V18H17V16H19V13H21V11H11V8H13V9H23V15ZM3 16H1V8H3V16ZM17 16H15V15H13V16H11V13H17V16ZM9 14H5V10H9V14ZM11 8H3V6H11V8Z") }
    val Globe: ImageVector by lazy { icon("Globe", "M6 2h12v2H6zm0 18h12v2H6zM4 4h2v2H4zm5 0h2v2H9zm0 14h2v2H9zm4 0h2v2h-2zM7 6h2v12H7zm8 0h2v12h-2zm-2-2h2v2h-2zm7 0h-2v2h2zM2 6h2v12H2zm20 0h-2v12h2zM4 18h2v2H4zm16 0h-2v2h2z", "M3 11h18v2H3z") }
    val Lock: ImageVector by lazy { icon("Lock", "M5 8h14v2H5zm0 12h14v2H5zM3 10h2v10H3zm16 0h2v10h-2zM7 4h2v4H7zm2-2h6v2H9zm6 2h2v4h-2z") }
    val MoreVertical: ImageVector by lazy { icon("MoreVertical", "M15 3v2h-2V3zm0 8v2h-2v-2zm0 8v2h-2v-2zM13 1v2h-2V1zm0 8v2h-2V9zm0 8v2h-2v-2zM11 3v2H9V3zm0 8v2H9v-2zm0 8v2H9v-2zm2-14v2h-2V5zm0 8v2h-2v-2zm0 8v2h-2v-2z") }
    val ExternalLink: ImageVector by lazy { icon("ExternalLink", "M11 5H5v2h6V5ZM5 7H3v12h2V7Zm12 12H5v2h12v-2Zm2-6h-2v6h2v-6Zm-8 0H9v2h2v-2Zm2-2h-2v2h2v-2Zm2-2h-2v2h2V9Zm2-2h-2v2h2V7Zm2-2h-2v2h2V5Zm2-2h-2v8h2V3Z", "M21 3h-8v2h8V3Z") }
    val Palette: ImageVector by lazy { icon("Palette", "M14 2h6v2h-6zm0 18h6v2h-6zM4 20h10v2H4zm8-16h2v16h-2zm8 0h2v16h-2zM2 16h2v4H2zm2-2h8v2H4zm12 2h2v2h-2zM6 12h2v2H6zM4 8h2v4H4zm2-2h4v2H6zm4 2h2v2h-2z") }
    val Smartphone: ImageVector by lazy { icon("Smartphone", "M6 2h12v2H6zm0 18h12v2H6zM4 4h2v16H4zm14 0h2v16h-2zm-7 13h2v2h-2z") }
    val Play: ImageVector by lazy { icon("Play", "M15 11h-2V9h2zm0 4h-2v-2h2zm-2 2h-2v-2h2zm0-8h-2V7h2zm-2-2H9V5h2zM9 21H7V3h2zm6-8h2v-2h-2zm-6 4h2v2H9z") }
    val Reload: ImageVector by lazy { icon("Reload", "M16 4h2v6h-2zm-2-2h2v2h-2zm0 2h2v8h-2zM4 8H2v5h2z", "M4 6h16v2H4zm4 14H6v-6h2zm2 2H8v-2h2zm0-2H8v-8h2zm10-4h2v-5h-2z", "M20 18H4v-2h16z") }
    val Undo: ImageVector by lazy { icon("Undo", "M18 20h-6v-2h6v2Zm2-2h-2v-8h2v8Zm-10-4H8v-2H6v-2H4V8h2V6h2V4h2v4h8v2h-8v4Z") }
    val Send: ImageVector by lazy { icon("Send", "M4 19h4v2H2v-8h2v6Zm8 0H8v-2h4v2Zm4-2h-4v-2h4v2Zm4-2h-4v-2h4v2Zm-10-2H4v-2h6v2Zm12 0h-2v-2h2v2ZM8 5H4v6H2V3h6v2Zm12 6h-4V9h4v2Zm-4-2h-4V7h4v2Zm-4-2H8V5h4v2Z") }
    val Star: ImageVector by lazy { icon("Star", "M5 20H8V22H3V16H5V20ZM21 22H16V20H19V16H21V22ZM10 20H8V18H10V20ZM16 20H14V18H16V20ZM14 18H10V16H14V18ZM7 16H5V13H7V16ZM19 16H17V13H19V16ZM5 13H3V11H5V13ZM21 13H19V11H21V13ZM9 9H3V11H1V7H9V9ZM23 11H21V9H15V7H23V11ZM11 7H9V3H11V7ZM15 7H13V3H15V7ZM13 3H11V1H13V3Z") }
    val Stop: ImageVector by lazy { icon("Stop", "M20 20H4V4H20V20ZM6 18H18V6H6V18ZM14 14H10V10H14V14Z") }
    val Database: ImageVector by lazy { icon("Database", "M2 6h2v4H2zm0 4h2v4H2zm0 4h2v4H2zm18-8h2v4h-2zm0 4h2v4h-2zm0 4h2v4h-2zM4 4h4v2H4zm0 8h4v-2H4zm0 4h4v-2H4zm0 4h4v-2H4zM16 4h4v2h-4zm0 8h4v-2h-4zm0 4h4v-2h-4zm0 4h4v-2h-4zM8 2h8v2H8zm0 12h8v-2H8zm0 4h8v-2H8zm0 4h8v-2H8z") }
    val Grid3x3: ImageVector by lazy { icon("Grid3x3", "M4 2h16v2H4zm0 18h16v2H4zM2 4h2v16H2zm18 0h2v16h-2zM4 8h16v2H4zm0 6h16v2H4z", "M8 4h2v16H8zm6 0h2v16h-2z") }
    val Terminal: ImageVector by lazy { icon("Terminal", "M4 2h16v2H4zm0 18h16v2H4zM2 4h2v16H2zm18 0h2v16h-2zM6 16h2v2H6zm2-2h2v2H8zm-2-2h2v2H6z") }
    val Eye: ImageVector by lazy { icon("Eye", "M16 20H8v-2h8v2Zm-8-2H4v-2h4v2Zm12 0h-4v-2h4v2ZM4 16H2v-2h2v2Zm10-6h-2v2h2v-2h2v4h-2v2h-4v-2H8v-4h2V8h4v2Zm8 6h-2v-2h2v2ZM2 14H0v-4h2v4Zm22 0h-2v-4h2v4ZM4 10H2V8h2v2Zm18 0h-2V8h2v2ZM8 8H4V6h4v2Zm12 0h-4V6h4v2Zm-4-2H8V4h8v2Z") }
    val EyeOff: ImageVector by lazy { icon("EyeOff", "M0 10h2v4H0zm24 0h-2v4h2zm-8 0h-2v2h2zm-6 0H8v4h2zM2 8h2v2H2zm0 8h2v-2H2zm20-8h-2v2h2zm0 8h-2v-2h2zM4 6h4v2H4zm0 12h4v-2H4zM20 6h-4v2h4zM10 4h6v2h-6zM8 20h8v-2H8zm4-12h2v2h-2zm-2 6h4v2h-4zM8 8h2v2H8zm2 2h2v4h-2zm2 2h2v2h-2z", "M6 6h2v2H6zM4 4h2v2H4zM2 2h2v2H2zm12 12h2v2h-2zm2 2h2v2h-2zm2 2h2v2h-2zm2 2h2v2h-2z") }
    val Cloud: ImageVector by lazy { icon("Cloud", "M22 10h-4v2h4v-2Zm2 2h-2v6h2v-6Zm-2 6H2v2h20v-2ZM2 12H0v6h2v-6Zm2-2H2v2h2v-2Zm4-2H4v2h4V8Zm8-4h-6v2h6V4Zm-6 2H8v2h2V6Zm0 4H8v2h2v-2Zm8-4h-2v2h2V6Z", "M20 8h-2v4h2V8Zm-2 4h-2v2h2v-2Z") }
    val Info: ImageVector by lazy { icon("Info", "M18 22H6V20H18V22ZM6 20H4V18H6V20ZM20 20H18V18H20V20ZM4 18H2V6H4V18ZM22 18H20V6H22V18ZM13 17H11V11H13V17ZM13 9H11V7H13V9ZM6 6H4V4H6V6ZM20 6H18V4H20V6ZM18 4H6V2H18V4Z") }
    val Tablet: ImageVector by lazy { icon("Tablet", "M5 2h14v2H5zm0 18h14v2H5zM3 4h2v16H3zm16 0h2v16h-2zm-8 12h2v2h-2z") }
    val Monitor: ImageVector by lazy { icon("Monitor", "M4 2h16v2H4zm0 14h16v2H4zM2 4h2v12H2zm18 0h2v12h-2zm-9 14h2v2h-2zm-3 2h8v2H8z") }
    val Square: ImageVector by lazy { icon("Square", "M2 4h2v16H2zm2 16h16v2H4zM20 4h2v16h-2zM4 2h16v2H4z") }
    val Image: ImageVector by lazy { icon("Image", "M4 2h16v2H4zm0 18h16v2H4zM2 4h2v16H2zm18 0h2v16h-2zm-4 8h2v2h-2zm-2 2h2v2h-2zm4 0h2v2h-2zm-8 0h2v2h-2zm2 2h2v2h-2zm2 2h2v2h-2z", "M20 16h2v2h-2zM8 16h2v2H8zm-2 2h2v2H6zM8 6h2v2H8zM6 8h2v2H6zm2 2h2v2H8zm2-2h2v2h-2z") }
    val Menu: ImageVector by lazy { icon("Menu", "M20 18H4v-2h16v2Zm0-5H4v-2h16v2Zm0-5H4V6h16v2Z") }
    val Apple: ImageVector by lazy { icon("Apple", "M11 8h3v2h-3zm3-2h4v2h-4zm4 2h2v3h-2zm-2 3h2v4h-2zm2 4h2v5h-2zm-4 5h4v2h-4zm-3-2h3v2h-3zm-4 2h4v2H7zm-2-2h2v2H5zm-2-8h2v8H3zm2-2h2v2H5zm2-2h4v2H7zm5-3h2v2h-2zm2-2h2v2h-2z") }
    val Android: ImageVector by lazy { icon("Android", "M2 12h2v6H2zm18 0h2v6h-2zM4 10h2v2H4zm4 2h2v4H8zm6 0h2v4h-2zm6-2h-2v2h2zM6 8h2v2H6zM4 6h2v2H4zM2 4h2v2H2zm16 4h-2v2h2zm2-2h-2v2h2zm2-2h-2v2h2zM8 6h8v2H8zM2 18h20v2H2z") }
    val Filter: ImageVector by lazy { icon("Filter", "M11 20H13V22H9V12H11V20ZM15 20H13V12H15V20ZM9 12H7V10H9V12ZM17 12H15V10H17V12ZM7 10H5V8H7V10ZM19 10H17V8H19V10ZM21 8H19V4H5V8H3V2H21V8Z") }
    val Sort: ImageVector by lazy { icon("Sort", "M20 16v2H4v-2zm-10-2v2H6v-2zm0-2v2H8v-2zm0 6v2H6v-2zm0 2v2H8v-2zM4 6v2h16V6zm10-2v2h4V4zm0-2v2h2V2zm0 6v2h4V8zm0 2v2h2v-2z") }
}
