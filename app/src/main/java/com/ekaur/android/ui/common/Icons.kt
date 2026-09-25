package com.ekaur.android.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons: small line drawings on a 24-unit grid, drawn as vectors.
 *
 * They replace the emoji the screens used to carry. Emoji render differently
 * on every phone brand, can't take the theme's colour, and are heavier to draw
 * than a handful of path segments. These tint with whatever colour they are
 * given, stay crisp at any size, and look the same on a Realme and a Pixel.
 *
 * The Reels and Shorts marks are deliberately generic (a film strip, a phone
 * with a play mark) -- never either company's logo.
 */
object EkIcons {
    val Back by lazy { line("back", "M15 18l-6-6 6-6") }
    val Close by lazy { line("close", "M18 6L6 18", "M6 6l12 12") }
    val Check by lazy { line("check", "M20 6L9 17l-5-5") }
    val ChevronDown by lazy { line("chevron-down", "M6 9l6 6 6-6") }
    val Warning by lazy { line("warning", "M12 3.5L2.5 20h19L12 3.5z", "M12 10v4.5", "M12 17.4v.1") }
    val Alert by lazy { line("alert", CIRCLE_9, "M12 7.5v5.5", "M12 16.4v.1") }
    val Lock by lazy { line("lock", "M6 11h12v10H6z", "M8.5 11V7.5a3.5 3.5 0 0 1 7 0V11") }
    val Refresh by lazy { line("refresh", "M20 12a8 8 0 1 1-2.35-5.65", "M20 4.5v5h-5") }
    val WifiOff by lazy {
        line("wifi-off", "M3 3l18 18", "M8.5 16.4a5 5 0 0 1 7 0", "M5 12.9a10 10 0 0 1 4.3-2.4", "M19 12.9a10 10 0 0 0-1.9-1.4", "M12 20h.01")
    }
    val EyeOff by lazy {
        line(
            "eye-off",
            "M3 3l18 18",
            "M10.6 5.1C11 5 11.5 5 12 5c6 0 10 7 10 7a17 17 0 0 1-3.1 3.9",
            "M6.6 6.6C3.9 8.4 2 12 2 12s4 7 10 7a9.8 9.8 0 0 0 5.4-1.6",
            "M9.9 9.9a3 3 0 0 0 4.2 4.2",
        )
    }
    val Crown by lazy { filled("crown", "M3 18.5L4.8 7.5l5 4.6L12 5.5l2.2 6.6 5-4.6L21 18.5z") }
    val Pencil by lazy { line("pencil", "M4 20h4L19 9l-4-4L4 16v4z", "M14 6l4 4") }
    val Image by lazy { line("image", "M4 5h16v14H4z", "M9 10.5m-1.5 0a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0", "M20 15.5l-4.5-4.5L7 19.5") }
    val Folder by lazy { line("folder", "M3 6.5h6l2 2.5h10v10H3z") }
    val Copy by lazy { line("copy", "M9 9h11v11H9z", "M5 15H4V4h11v1") }
    val Share by lazy { line("share", "M12 3.5v11", "M7.5 8L12 3.5 16.5 8", "M5 14v6h14v-6") }
    val Sparkle by lazy { filled("sparkle", "M12 2.5l2 5.9 5.9 2-5.9 2-2 5.9-2-5.9-5.9-2 5.9-2z") }
    val Target by lazy { line("target", CIRCLE_9, "M12 12m-5 0a5 5 0 1 0 10 0a5 5 0 1 0-10 0", "M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0-2 0") }
    val Moon by lazy { line("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val ArrowUp by lazy { line("arrow-up", "M12 19V5", "M6 11l6-6 6 6") }
    val ArrowDown by lazy { line("arrow-down", "M12 5v14", "M6 13l6 6 6-6") }
    val TrendDown by lazy { line("trend-down", "M3 7l6 6 4-4 8 8", "M21 11v6h-6") }
    val Timer by lazy { line("timer", "M12 14m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M12 14v-4", "M9.5 3h5") }
    val Person by lazy {
        line("accessibility", "M12 5m-1.8 0a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0-3.6 0", "M5 9.5l7 1.5 7-1.5", "M12 11v4", "M8.5 21l3.5-6 3.5 6")
    }
    val Layers by lazy { line("layers", "M12 3l9 5-9 5-9-5 9-5z", "M3 13l9 5 9-5") }
    val Battery by lazy { line("battery", "M3.5 8h15v8h-15z", "M21 11v2", "M7 12h5") }
    val Activity by lazy { line("activity", "M3 12h4l3 7 4-14 3 7h4") }
    val FloatingButton by lazy { line("floating-button", "M12 12m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0", "M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0") }
    val Tiles by lazy { line("tiles", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z") }
    val Pause by lazy { line("pause", "M8.5 5v14", "M15.5 5v14") }
    val Download by lazy { line("download", "M12 4v11", "M7 10l5 5 5-5", "M5 20h14") }
    val Gift by lazy { line("gift", "M4 11h16v9H4z", "M3 7.5h18V11H3z", "M12 7.5V20", "M12 7.5C11 4.5 7 4.5 7 7.5", "M12 7.5c1-3 5-3 5 0") }
    val Box by lazy { line("box", "M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M4 7.5l8 4.5 8-4.5", "M12 12v9") }
    val CheckCircle by lazy { line("check-circle", CIRCLE_9, "M8 12.5l2.7 2.7L16 9.8") }
    val MoreVert by lazy { filled("more-vert", dot(12f, 5.5f), dot(12f, 12f), dot(12f, 18.5f)) }

    /** Reels, as a plain film strip. */
    val Reels by lazy {
        line(
            "reels",
            "M4 4h16v16H4z",
            "M8 4v16", "M16 4v16",
            "M4 9h4", "M4 15h4", "M16 9h4", "M16 15h4",
        )
    }

    /** Shorts, as a phone standing up with a play mark on it. */
    val Shorts by lazy {
        builder("shorts")
            .path(stroke = true, "M7 2.5h10v19H7z", "M10.8 18.8h2.4")
            .path(stroke = false, "M10.5 8.8v5.4l4.4-2.7z")
            .build()
    }

    // --- building ------------------------------------------------------------

    private const val CIRCLE_9 = "M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0"

    private fun dot(x: Float, y: Float) = "M${x - 1.6f} ${y}a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0"

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun ImageVector.Builder.path(stroke: Boolean, vararg d: String): ImageVector.Builder {
        d.forEach { data ->
            val nodes = PathParser().parsePathString(data).toNodes()
            if (stroke) {
                addPath(
                    pathData = nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            } else {
                addPath(pathData = nodes, fill = SolidColor(Color.Black))
            }
        }
        return this
    }

    private fun line(name: String, vararg d: String): ImageVector = builder(name).path(true, *d).build()

    private fun filled(name: String, vararg d: String): ImageVector = builder(name).path(false, *d).build()
}

/** An icon from [EkIcons], tinted. Decorative unless given a [description]. */
@Composable
fun EkIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    description: String? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(size),
    )
}
