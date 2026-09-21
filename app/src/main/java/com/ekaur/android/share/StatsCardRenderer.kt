package com.ekaur.android.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ekaur.android.sync.Avatar
import com.ekaur.android.ui.stats.formatDuration

/**
 * Draws the card that gets shared.
 *
 * Deliberately plain Android Canvas at a fixed pixel size rather than a capture
 * of the live UI. A screenshot of a composable comes out at whatever size and
 * density the phone happens to be, and this is the one thing in the app whose
 * output lands in front of people who have never seen it -- so it renders the
 * same 1080px card on every device, and can be reasoned about without a screen.
 *
 * The design follows the app's new look: a soft light card, dark ink text, and
 * the Instagram gradient on the wordmark and the hero number, which is the
 * loudest thing on it because the number is the joke.
 */
object StatsCardRenderer {

    private const val CANVAS = 0xFFFBF7FB.toInt()
    private const val SURFACE = 0xFFFFFFFF.toInt()
    private const val CHIP = 0xFFF3EEFB.toInt()
    private const val LINE = 0xFFECE7F2.toInt()
    private const val ACID = 0xFFDD2A7B.toInt()
    private const val ACID_DIM = 0xFFE7A6CC.toInt()
    private const val INK = 0xFF1C1C1E.toInt()
    private const val SMOKE = 0xFF8A8A99.toInt()
    private const val ASH = 0xFFB4B4C0.toInt()

    // The Instagram gradient, used as a shader on the wordmark and hero number.
    private val GRADIENT = intArrayOf(
        0xFF515BD4.toInt(), 0xFF8134AF.toInt(), 0xFFDD2A7B.toInt(),
        0xFFF58529.toInt(), 0xFFFEDA77.toInt(),
    )

    private val systemHeavy = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val systemRegular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    // Set per render from the optional Poppins faces, so the card matches the app
    // when a context can supply them and still renders in a test that cannot.
    private var faceHeavy: Typeface = systemHeavy
    private var faceRegular: Typeface = systemRegular

    fun render(
        stats: CardStats,
        shape: CardShape,
        avatar: Bitmap? = null,
        heavy: Typeface? = null,
        regular: Typeface? = null,
    ): Bitmap {
        faceHeavy = heavy ?: systemHeavy
        faceRegular = regular ?: systemRegular

        val bitmap = Bitmap.createBitmap(shape.width, shape.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(CANVAS)

        val inset = shape.width * 0.037f
        val unit = shape.width / 1080f
        // A soft white card the content sits on, matching the app's surfaces.
        canvas.drawRoundRect(
            RectF(inset, inset, shape.width - inset, shape.height - inset),
            48f * unit, 48f * unit,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE },
        )

        val margin = shape.width * 0.1f
        val tall = shape == CardShape.Story

        var y = if (tall) shape.height * 0.13f else shape.height * 0.12f

        // Top row: the wordmark on the left, the person on the right at the same
        // height, like a header.
        val headerBaseline = y
        y = drawWordmark(canvas, margin, y, unit)
        drawIdentity(canvas, stats, shape.width - margin, headerBaseline, unit, avatar)
        y += if (tall) 70f * unit else 40f * unit

        y = drawHero(canvas, stats, margin, y, shape, unit)
        y += if (tall) 64f * unit else 46f * unit

        val weekBottom = drawWeek(canvas, stats, margin, y, shape, unit, tall)

        if (tall) {
            drawFacts(canvas, stats, margin, weekBottom + 84f * unit, shape, unit)
        }

        drawFooter(canvas, stats, margin, shape, unit)
        return bitmap
    }

    /**
     * The person, top-right, sized to sit level with the wordmark: the username
     * right-aligned to the margin with their avatar just past it.
     */
    private fun drawIdentity(
        canvas: Canvas,
        stats: CardStats,
        right: Float,
        baseline: Float,
        unit: Float,
        avatar: Bitmap?,
    ) {
        if (stats.username.isBlank()) return

        val avatarSize = 58f * unit
        val avatarLeft = right - avatarSize
        // Centred on the wordmark's cap rather than its baseline, so the two
        // read as one row.
        val avatarTop = baseline - 46f * unit
        drawAvatar(canvas, stats.username, avatar, avatarLeft, avatarTop, avatarSize, unit)

        val name = textPaint(INK, 34f * unit, faceHeavy).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(stats.username, avatarLeft - 20f * unit, baseline, name)
    }

    /** A horizontal Instagram-gradient shader spanning [x]..[x]+[width]. */
    private fun gradientShader(x: Float, width: Float): Shader =
        LinearGradient(x, 0f, x + width, 0f, GRADIENT, null, Shader.TileMode.CLAMP)

    private fun drawWordmark(canvas: Canvas, x: Float, y: Float, unit: Float): Float {
        val paint = textPaint(ACID, 40f * unit, faceHeavy).apply { letterSpacing = 0.3f }
        val width = paint.measureText("EK AUR")
        paint.shader = gradientShader(x, width)
        canvas.drawText("EK AUR", x, y, paint)

        val tag = textPaint(SMOKE, 24f * unit, faceRegular).apply { letterSpacing = 0.25f }
        canvas.drawText("one more", x, y + 40f * unit, tag)
        return y + 40f * unit
    }

    /** The number, as big as the card can bear, gradient-filled. */
    private fun drawHero(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        top: Float,
        shape: CardShape,
        unit: Float,
    ): Float {
        val size = if (shape == CardShape.Story) 330f * unit else 250f * unit
        val paint = textPaint(INK, size, faceHeavy).apply { letterSpacing = -0.05f }

        val bounds = Rect()
        val text = stats.reelsToday.toString()
        paint.getTextBounds(text, 0, text.length, bounds)
        paint.shader = gradientShader(x, bounds.width().toFloat().coerceAtLeast(size))

        val baseline = top + bounds.height()
        canvas.drawText(text, x, baseline, paint)

        val label = textPaint(SMOKE, 42f * unit, faceRegular)
        canvas.drawText(CardCopy.subtitleFor(stats.reelsToday), x, baseline + 58f * unit, label)

        if (stats.activeMsToday > 0) {
            val time = textPaint(ASH, 34f * unit, faceRegular)
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
     * by value twice over. Returns the baseline y so the caller can flow on.
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
        val height = if (tall) 400f * unit else 190f * unit
        val slot = width / week.size
        val barWidth = slot * 0.52f
        val peak = week.max().coerceAtLeast(1)

        val label = textPaint(SMOKE, 28f * unit, faceRegular)
        val labelBaseline = top + 28f * unit
        canvas.drawText("Last 7 days", x, labelBaseline, label)

        val base = labelBaseline + 34f * unit + height
        week.forEachIndexed { index, value ->
            val barHeight = (height * value / peak).coerceAtLeast(if (value > 0) 4f * unit else 0f)
            if (barHeight <= 0f) return@forEachIndexed

            val left = x + slot * index + (slot - barWidth) / 2
            val rect = RectF(left, base - barHeight, left + barWidth, base)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // The peak wears the solid accent; the rest a soft magenta.
                color = if (value == peak) ACID else ACID_DIM
            }
            canvas.drawRoundRect(rect, 6f * unit, 6f * unit, paint)
        }

        val rule = Paint().apply { color = LINE }
        canvas.drawRect(x, base, x + width, base + 2f * unit, rule)
        return base + 2f * unit
    }

    /** Two facts, in soft chips, so the tall card has something after the number. */
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
            "Best ever" to stats.bestEver.toString(),
            "Peak" to (stats.peakHour ?: "—"),
        )

        facts.forEachIndexed { index, (caption, value) ->
            val left = x + (boxWidth + gap) * index
            val rect = RectF(left, top, left + boxWidth, top + boxHeight)
            canvas.drawRoundRect(
                rect,
                24f * unit,
                24f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHIP },
            )
            canvas.drawText(
                value,
                left + 30f * unit,
                top + 80f * unit,
                textPaint(INK, 54f * unit, faceHeavy),
            )
            canvas.drawText(
                caption,
                left + 30f * unit,
                top + 122f * unit,
                textPaint(SMOKE, 30f * unit, faceRegular),
            )
        }
        return top + boxHeight
    }

    /**
     * The dare and the link.
     *
     * Pinned to the bottom rather than flowed after the content, so it lands in
     * the same place on both shapes and never collides with a long number. The
     * person now sits top-right, so the footer is just the challenge and the link.
     */
    private fun drawFooter(
        canvas: Canvas,
        stats: CardStats,
        x: Float,
        shape: CardShape,
        unit: Float,
    ) {
        val bottom = shape.height - x
        val linkPaint = textPaint(ASH, 30f * unit, faceRegular)
        canvas.drawText(CardCopy.LINK, x, bottom, linkPaint)

        val dare = textPaint(ACID, 50f * unit, faceHeavy)
        canvas.drawText(CardCopy.challengeFor(stats.reelsToday), x, bottom - 64f * unit, dare)
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

        // A soft gradient disc with the initial, matching the app's ringed avatars.
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x, y, x + size, y + size, GRADIENT, null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawOval(rect, disc)
        val initial = textPaint(Color.WHITE, size * 0.42f, faceHeavy).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            Avatar.initialOf(username),
            rect.centerX(),
            rect.centerY() + size * 0.15f,
            initial,
        )
    }

    private fun textPaint(colour: Int, size: Float, face: Typeface): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            textSize = size
            typeface = face
        }
}
