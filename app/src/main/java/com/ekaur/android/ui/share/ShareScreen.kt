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
import androidx.compose.material3.MaterialTheme
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
import com.ekaur.android.share.CardShape
import com.ekaur.android.share.CardSharing
import com.ekaur.android.share.CardStats
import com.ekaur.android.share.StatsCardRenderer
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The card, before it goes out.
 *
 * Both shapes are offered because no single one travels everywhere: a 9:16
 * card is what WhatsApp status and Instagram stories want, and it is cropped
 * badly in a feed or a timeline, where 1:1 belongs. Rendering both and letting
 * the user pick is cheaper than guessing wrong.
 */
@Composable
fun ShareScreen(
    stats: CardStats,
    avatar: Bitmap?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shape by remember { mutableStateOf(CardShape.Story) }
    var card by remember { mutableStateOf<Bitmap?>(null) }

    // Re-rendered when the shape changes, off the main thread: this draws a
    // 1080x1920 bitmap and has no business blocking a frame.
    LaunchedEffect(shape, stats, avatar) {
        card = withContext(Dispatchers.Default) {
            runCatching { StatsCardRenderer.render(stats, shape, avatar) }.getOrNull()
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
            SectionLabel("Card")
            FlatButton(text = "Close", onClick = onClose)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardShape.entries.forEach { option ->
                FlatButton(
                    text = option.label,
                    emphasised = option == shape,
                    onClick = { shape = option },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val preview = card
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "share card preview",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Making...", color = Smoke)
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
                            "share card",
                        )
                    )
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        Card {
            Text(
                text = "Story wali WhatsApp status aur Instagram story ke liye, " +
                    "post wali Instagram post, X aur chat ke liye.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
