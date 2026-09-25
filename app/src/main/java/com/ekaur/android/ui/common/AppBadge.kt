package com.ekaur.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.ui.theme.ReelsMark
import com.ekaur.android.ui.theme.ReelsMarkSoft
import com.ekaur.android.ui.theme.ShortsMark
import com.ekaur.android.ui.theme.ShortsMarkSoft

/** The icon that stands for an app's videos: a film strip or a phone with a play mark. */
val TrackedApp.icon: ImageVector
    get() = when (this) {
        TrackedApp.Instagram -> EkIcons.Reels
        TrackedApp.YouTube -> EkIcons.Shorts
    }

/** The app's mark colour, for dots, bars and tags. */
val TrackedApp.mark: Color
    get() = when (this) {
        TrackedApp.Instagram -> ReelsMark
        TrackedApp.YouTube -> ShortsMark
    }

/** A pale wash of [mark], for chip backgrounds. */
val TrackedApp.markSoft: Color
    get() = when (this) {
        TrackedApp.Instagram -> ReelsMarkSoft
        TrackedApp.YouTube -> ShortsMarkSoft
    }

// Built once: badges are drawn in lists, and a brush per row adds up.
private val ReelsBadgeFill = Brush.linearGradient(
    listOf(Color(0xFF8134AF), Color(0xFFDD2A7B), Color(0xFFF58529)),
)
private val ShortsBadgeFill = Brush.linearGradient(
    listOf(Color(0xFFFF4E45), Color(0xFFD0001A)),
)

private val TrackedApp.badgeFill: Brush
    get() = when (this) {
        TrackedApp.Instagram -> ReelsBadgeFill
        TrackedApp.YouTube -> ShortsBadgeFill
    }

/**
 * An app's badge: a rounded square in the app's own colours with its icon in
 * white. The one visual that says "this number came from Reels" or "from
 * Shorts" everywhere in the app -- the Setup rows, the Home split, the
 * leaderboard -- so people learn it once.
 */
@Composable
fun AppBadge(
    app: TrackedApp,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(app.badgeFill),
        contentAlignment = Alignment.Center,
    ) {
        EkIcon(app.icon, tint = Color.White, size = size * 0.58f)
    }
}

/** Badges for several apps, slightly overlapped, in a fixed order. */
@Composable
fun AppBadges(
    apps: Set<TrackedApp>,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TrackedApp.entries.filter { it in apps }.forEach { AppBadge(it, size = size) }
    }
}

/** A small pill: the app's mini badge and a label, e.g. "Shorts" or "45 Shorts". */
@Composable
fun AppTag(
    app: TrackedApp,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(app.markSoft)
            .padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(app, size = 16.dp)
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = app.mark, maxLines = 1)
    }
}
