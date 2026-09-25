package com.ekaur.android.ui.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.ekaur.android.ui.theme.Looks
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.ekaur.android.ui.common.EmptyState
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.ScreenHeader
import com.ekaur.android.ui.common.Skeleton
import com.ekaur.android.ui.theme.Acid
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.ekaur.android.R
import com.ekaur.android.share.CardShape
import com.ekaur.android.share.CardSharing
import com.ekaur.android.share.CardStats
import com.ekaur.android.share.StatsCardRenderer
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The card, before it goes out.
 *
 * One square card that travels everywhere -- WhatsApp chat and status, an
 * Instagram post or DM, X, a feed. It renders the moment the screen opens (from
 * Room, with a cache-first avatar), so "Share" is ready almost immediately rather
 * than after two loading steps and a shape choice.
 */
@Composable
fun ShareScreen(
    stats: CardStats,
    avatar: Bitmap?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var card by remember { mutableStateOf<ImageBitmap?>(null) }
    var raw by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    // The Poppins faces the app uses, so the card matches it. Loaded once, and
    // never a hard dependency -- a font that won't load falls back to the system
    // one rather than breaking the card.
    val heavy = remember { runCatching { ResourcesCompat.getFont(context, R.font.poppins_bold) }.getOrNull() }
    val regular = remember { runCatching { ResourcesCompat.getFont(context, R.font.poppins_regular) }.getOrNull() }

    // Rendered off the main thread: this draws a 1080x1080 bitmap and has no
    // business blocking a frame. Keyed on [attempt] too, so "Try again" works.
    // The card wears the sharer's look: a Shorts person posts a red card.
    val palette = Looks.palette
    val cardPalette = remember(palette) { cardPaletteOf(palette) }

    LaunchedEffect(stats, avatar, attempt, cardPalette) {
        failed = false
        card = null
        val rendered = withContext(Dispatchers.Default) {
            runCatching {
                StatsCardRenderer.render(stats, CardShape.Square, avatar, heavy, regular, cardPalette)
            }.getOrNull()
        }
        raw = rendered
        // Converted once here, not on every recomposition.
        card = rendered?.asImageBitmap()
        failed = rendered == null
    }

    // The card lands with a small scale-and-fade, like a photo dropped on a desk.
    val landed by animateFloatAsState(
        targetValue = if (card != null) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "card-land",
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = "Share card",
            subtitle = "One square card. Works everywhere.",
            onBack = onClose,
        )

        // A fixed 1:1 slot, so nothing below it jumps when the card arrives.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val preview = card
            when {
                preview != null -> Image(
                    bitmap = preview,
                    contentDescription = "Your share card: ${stats.reelsToday} reels today",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = 0.92f + 0.08f * landed
                            scaleX = s
                            scaleY = s
                            alpha = landed.coerceIn(0f, 1f)
                        }
                        .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Acid)
                        .clip(RoundedCornerShape(22.dp)),
                )
                failed -> EmptyState(
                    emoji = "😵",
                    title = "Couldn't build the card.",
                    body = "Happens rarely. One more go usually does it.",
                    action = "Try again",
                    onAction = { attempt++ },
                )
                else -> Skeleton(Modifier.fillMaxSize(), corner = 22.dp)
            }
        }

        Spacer(Modifier.height(18.dp))

        FlatButton(
            text = "Share",
            icon = "↗",
            emphasised = true,
            enabled = card != null,
            loading = card == null && !failed,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val ready = raw ?: return@FlatButton
                runCatching {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            CardSharing.intentFor(context, ready, CardSharing.captionFor(stats)),
                            "Share card",
                        )
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "WhatsApp, Instagram stories and DMs, X — anything that takes a picture.",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
    }
}

/** The live look, as the plain ints the card renderer takes. */
private fun cardPaletteOf(p: com.ekaur.android.ui.theme.Palette) = com.ekaur.android.share.CardPalette(
    canvas = p.canvas.toArgb(),
    chip = p.soft.toArgb(),
    line = p.hairline.toArgb(),
    accent = p.stops[2].toArgb(),
    accentDim = p.accentDim.toArgb(),
    gradient = p.stops.map { it.toArgb() }.toIntArray(),
)
