package com.ekaur.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ekaur.android.detect.TrackedApp

/**
 * Which look the app wears: the person's own app, or both.
 *
 * One design in three colour sets. Layout, type, shapes and motion never
 * change; only these colours do.
 */
enum class AppLook { Instagram, Shorts, Both }

/** The colours that change with the look. Everything else is fixed. */
class Palette(
    /** Page background. */
    val canvas: Color,
    /** Soft chip and track fill. */
    val soft: Color,
    /** A warmer soft tint, for highlights and "you" rows. */
    val blush: Color,
    val peach: Color,
    /** The flat accent: small marks, links, the dare line. */
    val accent: Color,
    /** The accent's quiet step: non-peak chart bars. */
    val accentDim: Color,
    val hairline: Color,
    /** The signature sweep, five stops, for the hero number and wordmark. */
    val stops: List<Color>,
    /** The sweep without its palest end, four stops, for fills under white text. */
    val buttonStops: List<Color>,
) {
    /** Size-relative, so one instance paints anything. Built once per palette. */
    val gradient: Brush by lazy { Brush.linearGradient(stops, Offset.Zero, Offset.Infinite) }
    val buttonGradient: Brush by lazy { Brush.linearGradient(buttonStops, Offset.Zero, Offset.Infinite) }
    val softGradient: Brush by lazy { Brush.linearGradient(listOf(soft, blush, peach)) }

    companion object {
        val Instagram = Palette(
            canvas = Color(0xFFFBF7FB),
            soft = Color(0xFFF3EEFB),
            blush = Color(0xFFFDEEF4),
            peach = Color(0xFFFFF2E9),
            accent = Color(0xFFC13584),
            accentDim = Color(0xFFE29AC6),
            hairline = Color(0xFFECE7F2),
            stops = listOf(Color(0xFF515BD4), Color(0xFF8134AF), Color(0xFFDD2A7B), Color(0xFFF58529), Color(0xFFFEDA77)),
            buttonStops = listOf(Color(0xFF515BD4), Color(0xFF8134AF), Color(0xFFDD2A7B), Color(0xFFF58529)),
        )

        /**
         * Inspired by Shorts, not a copy of YouTube: colour only, no logo, no
         * play button. Fills under white text use a deeper red than YouTube's
         * own, because white on #FF0000 is hard to read.
         */
        val Shorts = Palette(
            canvas = Color(0xFFFFF8F7),
            soft = Color(0xFFFDECEC),
            blush = Color(0xFFFFEDEA),
            peach = Color(0xFFFFF3EC),
            accent = Color(0xFFC4001A),
            accentDim = Color(0xFFF4A7A7),
            hairline = Color(0xFFF2E4E3),
            stops = listOf(Color(0xFFA80018), Color(0xFFD0001A), Color(0xFFE0001B), Color(0xFFFF3B30), Color(0xFFFF7A45)),
            buttonStops = listOf(Color(0xFFA80018), Color(0xFFC0001A), Color(0xFFC8001B), Color(0xFFE0141E)),
        )

        /** The Instagram sweep carried on into Shorts red: one gradient, both apps. */
        val Both = Palette(
            canvas = Color(0xFFFCF7F9),
            soft = Color(0xFFF6EEF7),
            blush = Color(0xFFFDEEF1),
            peach = Color(0xFFFFF2EC),
            accent = Color(0xFFC21860),
            accentDim = Color(0xFFEDA6C2),
            hairline = Color(0xFFEFE6EE),
            stops = listOf(Color(0xFF5A4FD6), Color(0xFF8E2FAA), Color(0xFFDD2A7B), Color(0xFFF0142E), Color(0xFFFF7A3D)),
            buttonStops = listOf(Color(0xFF5A4FD6), Color(0xFF8E2FAA), Color(0xFFD0205F), Color(0xFFD80F26)),
        )

        fun of(look: AppLook): Palette = when (look) {
            AppLook.Instagram -> Instagram
            AppLook.Shorts -> Shorts
            AppLook.Both -> Both
        }

        /** Part-way between two palettes, for the cross-fade when the look changes. */
        fun lerp(from: Palette, to: Palette, t: Float): Palette = Palette(
            canvas = lerp(from.canvas, to.canvas, t),
            soft = lerp(from.soft, to.soft, t),
            blush = lerp(from.blush, to.blush, t),
            peach = lerp(from.peach, to.peach, t),
            accent = lerp(from.accent, to.accent, t),
            accentDim = lerp(from.accentDim, to.accentDim, t),
            hairline = lerp(from.hairline, to.hairline, t),
            stops = from.stops.zip(to.stops) { a, b -> lerp(a, b, t) },
            buttonStops = from.buttonStops.zip(to.buttonStops) { a, b -> lerp(a, b, t) },
        )
    }
}

/**
 * The live palette.
 *
 * Snapshot state rather than a composition local, so the existing colour names
 * (`Acid`, `SurfaceLav`, `instaGradient()`...) can simply read it -- from
 * composition or from a draw lambda alike -- and every screen follows a change
 * without its call sites being rewritten. Reads register like any state read,
 * so a change recomposes or redraws exactly what used the colour.
 */
object Looks {
    var palette: Palette by mutableStateOf(Palette.Instagram)

    /**
     * The look for the apps being counted, unless the person picked one.
     * Instagram only -> Instagram, YouTube only -> Shorts, both -> Both.
     */
    fun lookFor(apps: Set<TrackedApp>, override: AppLook? = null): AppLook {
        if (override != null) return override
        val insta = TrackedApp.Instagram in apps
        val yt = TrackedApp.YouTube in apps
        return when {
            insta && yt -> AppLook.Both
            yt -> AppLook.Shorts
            else -> AppLook.Instagram
        }
    }
}
