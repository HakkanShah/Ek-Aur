package com.ekaur.android.ui.avatar

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.CropTransform
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.Smoke
import kotlin.math.roundToInt

/**
 * Framing a photo before it becomes a profile picture.
 *
 * The app used to take the centre square and upload it, which is the wrong
 * square most of the time -- a person standing off to one side loses their
 * head. Here the photo is shown at the size it will actually be used, and the
 * user drags and pinches until the circle holds what they meant.
 *
 * The circle is the guide rather than a square, because every place this
 * picture is ever shown is round: the leaderboard, the setup screen and the
 * share card all clip it. The square around the circle is what gets stored, so
 * nothing in the corners is ever seen and nothing framed inside the circle is
 * ever lost.
 *
 * All the arithmetic lives in [CropTransform], on the JVM where it is tested. A
 * crop rect that leaves the bitmap by one pixel is an exception thrown on a
 * phone with no logcat behind it.
 */
@Composable
fun AvatarCropScreen(
    photo: Bitmap,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: (Avatar.Crop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(photo) { photo.asImageBitmap() }

    // The stage is square and as wide as the screen, so it *is* the crop
    // window: gesture coordinates need no translating, which is one whole class
    // of off-by-a-window bug that cannot happen.
    // The window is a screen measurement and outlives any one photo; the
    // framing is per photo.
    var window by remember { mutableFloatStateOf(0f) }
    var scale by remember(photo) { mutableFloatStateOf(0f) }
    var offsetX by remember(photo) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo) { mutableFloatStateOf(0f) }
    var touched by remember(photo) { mutableStateOf(false) }

    // Keyed on both, because onSizeChanged only reports a size that *changed*:
    // a second photo opened at the same window size would otherwise never be
    // given a scale, and nothing would be drawn at all.
    LaunchedEffect(photo, window) {
        if (window <= 0f) return@LaunchedEffect
        // Centred at the smallest allowed scale, which is exactly the crop the
        // app took on its own before this screen existed -- so opening it and
        // pressing straight through changes nothing.
        scale = CropTransform.minScale(photo.width, photo.height, window)
        offsetX = CropTransform.centreOffset(photo.width, scale, window)
        offsetY = CropTransform.centreOffset(photo.height, scale, window)
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("photo set karo")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ungli se ghumao, do ungli se zoom. circle ke andar jo hai wahi dikhega.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )

        Spacer(Modifier.height(16.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Learned in layout, never written from the draw pass: setting
                // state while drawing schedules another draw, which sets it
                // again.
                .onSizeChanged { window = it.width.toFloat() }
                .pointerInput(photo) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (window <= 0f) return@detectTransformGestures
                        touched = true

                        val next = CropTransform.clampScale(
                            scale * zoom, photo.width, photo.height, window,
                        )
                        // Zoom about the fingers rather than the corner, so the
                        // thing being looked at stays under them.
                        val factor = if (scale > 0f) next / scale else 1f
                        val zoomedX = centroid.x - (centroid.x - offsetX) * factor
                        val zoomedY = centroid.y - (centroid.y - offsetY) * factor

                        scale = next
                        offsetX = CropTransform.clampOffset(
                            zoomedX + pan.x, photo.width, next, window,
                        )
                        offsetY = CropTransform.clampOffset(
                            zoomedY + pan.y, photo.height, next, window,
                        )
                    }
                },
        ) {
            if (scale <= 0f || window <= 0f) return@Canvas
            val side = window

            drawImage(
                image = image,
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(
                    (photo.width * scale).roundToInt(),
                    (photo.height * scale).roundToInt(),
                ),
            )

            // Everything outside the circle is dimmed rather than hidden, so
            // there is still enough of the photo visible to aim with.
            val radius = side / 2f
            val circle = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(radius, radius),
                        radius = radius,
                    )
                )
            }
            clipPath(circle, ClipOp.Difference) {
                drawRect(Ink.copy(alpha = 0.74f))
            }
            drawCircle(
                color = Acid,
                radius = radius - 1.dp.toPx(),
                center = Offset(radius, radius),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlatButton(
                text = "rehne do",
                emphasised = false,
                onClick = { if (!busy) onCancel() },
            )
            FlatButton(
                text = if (busy) "bhej raha hoon..." else "lagao",
                emphasised = !busy,
                onClick = {
                    if (busy || scale <= 0f) return@FlatButton
                    onConfirm(
                        CropTransform.crop(
                            imageWidth = photo.width,
                            imageHeight = photo.height,
                            window = window,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                        )
                    )
                },
            )
        }

        // Shown here rather than back on the setup card, because a failed
        // upload leaves this screen open so lagao can simply be pressed again.
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Heat,
            )
        } else if (!touched) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "waise hi theek lage to seedha lagao daba do.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }
    }
}
