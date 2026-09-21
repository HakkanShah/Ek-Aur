package com.ekaur.android.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.stats.formatDuration

/**
 * Draws the card that gets posted.
 *
 * Deliberately plain Android Canvas at a fixed pixel size rather than a capture
 * of the live UI. A screenshot of a composable comes out at whatever size and
 * density the phone happens to be, and this is the one thing in the app whose
 * output lands in front of people who have never seen it -- so it renders the
 * same 1080px card on every device, and can be reasoned about without a screen.
 *
 * The design follows the app: black, one acid accent, heavy numerals, no
 * gradients. The number is the loudest thing on it, because the number is the
 * joke.
 */
object StatsCardRenderer {

    private const val INK = 0xFF0A0A0A.toInt()
    private const val RAISED = 0xFF141414.toInt()
    private const val LINE = 0xFF242424.toInt()
    private const val ACID = 0xFFDD2A7B.toInt()
    private const val ACID_DIM = 0xFF9B2C6A.toInt()
    private const val CHALK = 0xFFF2F2F2.toInt()
    private const val SMOKE = 0xFF8A8A8A.toInt()
    private const val ASH = 0xFF5A5A5A.toInt()
    private const val HEAT = 0xFFFF3B1F.toInt()

    private val black = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    fun render(stats: CardStats, shape: CardShape, avatar: Bitmap? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(shape.width, shape.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(INK)

        val margin = shape.width * 0.09f
        val tall = shape == CardShape.Story

        // The whole layout hangs off this, so the two shapes stay one design
        // rather than two that drift apart.
        val unit = shape.width / 1080f

        var y = if (tall) shape.height * 0.13f else shape.height * 0.10f

        y = drawWordmark(canvas, margin, y, unit)
        y += if (tall) 70f * unit else 34f * unit

        y = drawHero(canvas, stats, margin, y, shape, unit)
        y += if (tall) 64f * unit else 40f * unit

        y = drawWeek(canvas, stats, margin, y, shape, unit, tall)
        y += if (tall) 84f * unit else 40f * unit

        if (tall) {
            y = drawFacts(canvas, stats, margin, y, shape, unit)
        }

        drawFooter(canvas, stats, margin, shape, unit, avatar)
        return bitmap
    }

    private fun drawWordmark(canvas: Canvas, x: Float, y: Float, unit: Float): Float {
        val paint = textPaint(ACID, 34f * unit, black).apply { letterSpacing = 0.34f }
        canvas.drawText("EK AUR", x, y, paint)
        return y
    }

    /** The number, as big as the card can bear, and what it is. */
    private fun drawHero(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        top: Float,
        shape: CardShape,
        unit: Float,
    ): Float {
        val size = if (shape == CardShape.Story) 330f * unit else 250f * unit
        val paint = textPaint(heatFor(stats.reelsToday), size, black).apply {
            letterSpacing = -0.05f
        }

        val bounds = Rect()
        val text = stats.reelsToday.toString()
        paint.getTextBounds(text, 0, text.length, bounds)

        val baseline = top + bounds.height()
        canvas.drawText(text, x, baseline, paint)

        val label = textPaint(SMOKE, 42f * unit, regular)
        canvas.drawText(CardCopy.subtitleFor(stats.reelsToday), x, baseline + 58f * unit, label)

        if (stats.activeMsToday > 0) {
            val time = textPaint(ASH, 34f * unit, regular)
            canvas.drawText(
                formatDuration(stats.activeMsToday) + " watched",
                x,
                baseline + 106f * unit,
                time,
            )
            return baseline + 106f * unit
        }
        return baseline + 58f * unit
    }

    /**
     * Seven columns, one per day.
     *
     * The same rule as the dashboard: height carries the count and the colour
     * does not, so the tallest is simply the brightest rather than being shaded
     * by value twice over.
     */
    private fun drawWeek(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        top: Float,
        shape: CardShape,
        unit: Float,
        tall: Boolean,
    ): Float {
        val week = stats.week.takeLast(7)
        if (week.isEmpty() || week.all { it == 0 }) return top

        val width = shape.width - x * 2
        val height = if (tall) 400f * unit else 200f * unit
        val slot = width / week.size
        val barWidth = slot * 0.52f
        val peak = week.max().coerceAtLeast(1)

        val label = textPaint(ASH, 28f * unit, regular)
        // Text grows upward from its baseline, so drawing at the running cursor
        // would ride up into the line above. Its own height is the offset.
        val labelBaseline = top + 28f * unit
        canvas.drawText("last 7 days", x, labelBaseline, label)

        val base = labelBaseline + 34f * unit + height
        week.forEachIndexed { index, value ->
            val barHeight = (height * value / peak).coerceAtLeast(if (value > 0) 4f * unit else 0f)
            if (barHeight <= 0f) return@forEachIndexed

            val left = x + slot * index + (slot - barWidth) / 2
            val rect = RectF(left, base - barHeight, left + barWidth, base)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (value == peak) ACID else ACID_DIM
            }
            canvas.drawRoundRect(rect, 6f * unit, 6f * unit, paint)
        }

        val rule = Paint().apply { color = LINE }
        canvas.drawRect(x, base, x + width, base + 2f * unit, rule)
        return base + 2f * unit
    }

    /** Two facts, in boxes, so the card has something to read after the number. */
    private fun drawFacts(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        top: Float,
        shape: CardShape,
        unit: Float,
    ): Float {
        val width = shape.width - x * 2
        val gap = 24f * unit
        val boxWidth = (width - gap) / 2
        val boxHeight = 170f * unit

        val facts = listOf(
            "best ever" to stats.bestEver.toString(),
            "peak" to (stats.peakHour ?: "—"),
        )

        facts.forEachIndexed { index, (caption, value) ->
            val left = x + (boxWidth + gap) * index
            val rect = RectF(left, top, left + boxWidth, top + boxHeight)
            canvas.drawRoundRect(
                rect,
                22f * unit,
                22f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RAISED },
            )
            canvas.drawText(
                value,
                left + 28f * unit,
                top + 76f * unit,
                textPaint(CHALK, 54f * unit, black),
            )
            canvas.drawText(
                caption,
                left + 28f * unit,
                top + 116f * unit,
                textPaint(SMOKE, 30f * unit, regular),
            )
        }
        return top + boxHeight
    }

    /**
     * The dare, the name and the link.
     *
     * Pinned to the bottom rather than flowed after the content, so it lands in
     * the same place on both shapes and never collides with a long number.
     */
    private fun drawFooter(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        shape: CardShape,
        unit: Float,
        avatar: Bitmap?,
    ) {
        val bottom = shape.height - x
        val linkPaint = textPaint(ASH, 30f * unit, regular)
        canvas.drawText(CardCopy.LINK, x, bottom, linkPaint)

        val dare = textPaint(ACID, 52f * unit, black)
        canvas.drawText(CardCopy.challengeFor(stats.reelsToday), x, bottom - 64f * unit, dare)

        // The person, above their dare.
        val avatarSize = 72f * unit
        val avatarTop = bottom - 64f * unit - 46f * unit - avatarSize
        drawAvatar(canvas, stats.username, avatar, x, avatarTop, avatarSize, unit)

        canvas.drawText(
            stats.username,
            x + avatarSize + 22f * unit,
            avatarTop + avatarSize * 0.66f,
            textPaint(CHALK, 40f * unit, black),
        )
    }

    private fun drawAvatar(
        canvas: Canvas,
        username: String,
        avatar: Bitmap?,
        x: Float,
        y: Float,
        size: Float,
        unit: Float,
    ) {
        val rect = RectF(x, y, x + size, y + size)

        if (avatar != null) {
            // Rounded by drawing the photo through a circular mask, so a square
            // upload does not appear as a square among circles.
            val layer = canvas.saveLayer(rect, null)
            canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawBitmap(
                avatar,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                },
            )
            canvas.restoreToCount(layer)
            return
        }

        canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE })
        val initial = textPaint(CHALK, size * 0.42f, black).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            Avatar.initialOf(username),
            rect.centerX(),
            rect.centerY() + size * 0.15f,
            initial,
        )
    }

    /** White at rest, drifting to red once the number is frankly embarrassing. */
    private fun heatFor(count: Int): Int {
        val t = when {
            count <= 50 -> 0f
            count >= 400 -> 1f
            else -> (count - 50) / 350f
        }
        fun mix(from: Int, to: Int) = (from + (to - from) * t).toInt()
        return Color.rgb(
            mix(Color.red(CHALK), Color.red(HEAT)),
            mix(Color.green(CHALK), Color.green(HEAT)),
            mix(Color.blue(CHALK), Color.blue(HEAT)),
        )
    }

    private fun textPaint(colour: Int, size: Float, face: Typeface): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            textSize = size
            typeface = face
        }
}
