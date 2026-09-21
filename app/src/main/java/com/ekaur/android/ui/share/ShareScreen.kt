package com.ekaur.android.ui.share

import android.graphics.Bitmap
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
    var card by remember { mutableStateOf<Bitmap?>(null) }

    // The Poppins faces the app uses, so the card matches it. Loaded once.
    val heavy = remember { ResourcesCompat.getFont(context, R.font.poppins_bold) }
    val regular = remember { ResourcesCompat.getFont(context, R.font.poppins_regular) }

    // Rendered off the main thread: this draws a 1080x1080 bitmap and has no
    // business blocking a frame. Fast, because the stats come from Room and the
    // avatar is already warm in Coil's cache.
    LaunchedEffect(stats, avatar) {
        card = withContext(Dispatchers.Default) {
            runCatching {
                StatsCardRenderer.render(stats, CardShape.Square, avatar, heavy, regular)
            }.getOrNull()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Share card")
            FlatButton(text = "Close", onClick = onClose)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val preview = card
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Share card preview",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Getting it ready…", color = Smoke)
            }
        }

        Spacer(Modifier.height(16.dp))

        FlatButton(
            text = "Share",
            emphasised = card != null,
            onClick = {
                val ready = card ?: return@FlatButton
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

        Spacer(Modifier.height(24.dp))
    }
}
