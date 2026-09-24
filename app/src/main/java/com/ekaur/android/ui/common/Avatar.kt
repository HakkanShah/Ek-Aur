package com.ekaur.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ekaur.android.ui.theme.Poppins
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.InkLine

/**
 * Somebody's picture, or their initial when they have not set one.
 *
 * The fallback is not a grey blank: the colour is derived from the name, so a
 * leaderboard of people without pictures is still readable at a glance rather
 * than a column of identical circles.
 */
@Composable
fun UserAvatar(
    username: String,
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val tint = colourFor(username)

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        // The initial is always there underneath, so a picture that is still
        // loading (or never loads) shows a name, not a blank tinted circle.
        Text(
            text = Avatar.initialOf(username),
            style = TextStyle(
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
                color = Ink,
            ),
        )
        if (url != null) {
            val context = LocalPlatformContext.current
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).crossfade(220).build()
            }
            AsyncImage(
                model = request,
                // Named by whose it is; the leaderboard row next to it repeats
                // the name, so a screen reader is not left guessing.
                contentDescription = username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/**
 * A stable colour per name.
 *
 * Deliberately muted and never the accent: these sit in a list where exactly
 * one thing, the leader, is allowed to be loud.
 */
private fun colourFor(username: String): Color {
    if (username.isEmpty()) return InkLine
    val hash = username.fold(0) { acc, c -> acc * 31 + c.code }
    return PALETTE[Math.floorMod(hash, PALETTE.size)]
}

// Deep enough that a white initial reads clearly on every one of them.
private val PALETTE = listOf(
    Color(0xFF6B7A8F),
    Color(0xFF9A7B5F),
    Color(0xFF5F8A6B),
    Color(0xFF8A6B94),
    Color(0xFF5F8594),
    Color(0xFF8F8250),
)
