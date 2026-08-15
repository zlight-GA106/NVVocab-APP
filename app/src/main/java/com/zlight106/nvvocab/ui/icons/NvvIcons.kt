package com.zlight106.nvvocab.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object NvvIcons {
    val AlertCircle = lucide("alert-circle", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 8v4 M12 16h.01")
    val Bell = lucide("bell", "M10.27 21a2 2 0 0 0 3.46 0 M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9")
    val Bookmark = lucide("bookmark", "M6 3h12a2 2 0 0 1 2 2v16l-8-5-8 5V5a2 2 0 0 1 2-2z")
    val BookOpen = lucide("book-open", "M12 7v14 M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z")
    val BrainCircuit = lucide("brain-circuit", "M9.5 4.5A3 3 0 0 0 4 6v1.5A3.5 3.5 0 0 0 3 14v1a4 4 0 0 0 4 4h1 M14.5 4.5A3 3 0 0 1 20 6v1.5a3.5 3.5 0 0 1 1 6.5v1a4 4 0 0 1-4 4h-1 M8 9h3v3 M16 9h-3v6 M8 18v-3h3 M16 18v-3h-3")
    val Bot = lucide("bot", "M12 8V4H8 M2 14h2 M20 14h2 M15 13v2 M9 13v2 M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z")
    val Check = lucide("check", "M20 6 9 17l-5-5")
    val ChevronDown = lucide("chevron-down", "m6 9 6 6 6-6")
    val CirclePlus = lucide("circle-plus", "M8 12h8 M12 8v8 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z")
    val Cloud = lucide("cloud", "M17.5 19H9a7 7 0 1 1 6.7-9h1.8a4.5 4.5 0 1 1 0 9z")
    val Database = lucide("database", "M4 5c0 1.66 3.58 3 8 3s8-1.34 8-3-3.58-3-8-3-8 1.34-8 3 M4 5v6c0 1.66 3.58 3 8 3s8-1.34 8-3V5 M4 11v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6")
    val Download = lucide("download", "M12 3v12 M7 10l5 5 5-5 M5 21h14a2 2 0 0 0 2-2v-4 M3 15v4a2 2 0 0 0 2 2")
    val Eye = lucide("eye", "M2.06 12.35a1 1 0 0 1 0-.7C3.7 7.6 7.6 5 12 5s8.3 2.6 9.94 6.65a1 1 0 0 1 0 .7C20.3 16.4 16.4 19 12 19s-8.3-2.6-9.94-6.65 M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6")
    val FileQuestion = lucide("file-question", "M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5z M14 2v6h6 M9.1 13a3 3 0 1 1 5.8 1c0 2-3 2-3 4 M12 20h.01")
    val Keyboard = lucide("keyboard", "M10 8h.01 M14 8h.01 M18 8h.01 M6 8h.01 M8 12h.01 M12 12h.01 M16 12h.01 M7 16h10 M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z")
    val LayoutDashboard = lucide("layout-dashboard", "M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z")
    val ListChecks = lucide("list-checks", "m3 5 2 2 4-4 M3 12l2 2 4-4 M3 19l2 2 4-4 M13 6h8 M13 13h8 M13 20h8")
    val LogIn = lucide("log-in", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4 M10 17l5-5-5-5 M15 12H3")
    val LogOut = lucide("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9")
    val Moon = lucide("moon", "M20.99 11.18A9 9 0 1 1 12.82 3a7 7 0 0 0 8.17 8.18z")
    val Palette = lucide("palette", "M12 22a10 10 0 1 1 10-10c0 2.2-1.8 4-4 4h-1.5a2.5 2.5 0 0 0-2.5 2.5A3.5 3.5 0 0 1 10.5 22z M7.5 10.5h.01 M10.5 6.5h.01 M15 6.5h.01 M17.5 10.5h.01")
    val Pencil = lucide("pencil", "M21.17 6.83 17.17 2.83a2 2 0 0 0-2.83 0L3 14.17V21h6.83L21.17 9.66a2 2 0 0 0 0-2.83z M15 5l4 4 M3 21h6")
    val Play = lucide("play", "M6 3l14 9-14 9z")
    val RefreshCw = lucide("refresh-cw", "M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5 M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5")
    val Search = lucide("search", "M21 21l-4.35-4.35 M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0z")
    val Settings = lucide("settings", "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.38a2 2 0 0 0-.73-2.73l-.15-.09a2 2 0 0 1-1-1.74v-.51a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val Sparkles = lucide("sparkles", "m12 3-1.9 4.8a2 2 0 0 1-1.1 1.1L4 11l5 2.1a2 2 0 0 1 1.1 1.1L12 19l1.9-4.8a2 2 0 0 1 1.1-1.1l5-2.1-5-2.1a2 2 0 0 1-1.1-1.1z M5 3v4 M3 5h4 M19 17v4 M17 19h4")
    val Timer = lucide("timer", "M10 2h4 M12 14l3-3 M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M19 5l2 2")
    val Sun = lucide("sun", "M12 2v2 M12 20v2 M4.93 4.93l1.42 1.42 M17.66 17.66l1.41 1.41 M2 12h2 M20 12h2 M6.34 17.66l-1.41 1.41 M19.07 4.93l-1.41 1.41 M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z")
    val Tags = lucide("tags", "M13.172 2.828a4 4 0 0 1 5.656 0l2.344 2.344a4 4 0 0 1 0 5.656l-8.344 8.344a4 4 0 0 1-5.656 0l-2.344-2.344a4 4 0 0 1 0-5.656z M14 7h.01 M2 12l7-7")
    val Trash2 = lucide("trash-2", "M3 6h18 M8 6V4h8v2 M19 6l-1 14H6L5 6 M10 11v5 M14 11v5")
    val Upload = lucide("upload", "M12 3v12 M7 8l5-5 5 5 M5 21h14a2 2 0 0 0 2-2v-4 M3 15v4a2 2 0 0 0 2 2")
    val UserRound = lucide("user-round", "M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10 M20 21a8 8 0 0 0-16 0")
    val Volume2 = lucide("volume-2", "M11 5 6 9H2v6h4l5 4z M15.54 8.46a5 5 0 0 1 0 7.07 M19.07 4.93a10 10 0 0 1 0 14.14")
    val X = lucide("x", "M18 6 6 18 M6 6l12 12")
}

private fun lucide(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Transparent),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()
