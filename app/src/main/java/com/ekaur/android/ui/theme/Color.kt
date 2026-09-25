package com.ekaur.android.ui.theme

import androidx.compose.ui.graphics.Color

// A soft, light palette. Warm off-white rather than a wall of white, clean
// cards, and a gradient carrying the accent. The names are kept from the old
// dark theme so screens keep compiling.
//
// The tinted ones below are getters over the live palette (Palette.kt), so they
// follow the look -- Instagram, Shorts or both -- without any call site
// changing. Text, white and status colours are the same in every look.

// Surfaces
val Canvas: Color get() = Looks.palette.canvas        // page background
val Surface = Color(0xFFFFFFFF)                        // cards
val SurfaceLav: Color get() = Looks.palette.soft       // soft tint (highlights, chips)
val SurfaceBlush: Color get() = Looks.palette.blush    // warmer soft tint
val SurfacePeach: Color get() = Looks.palette.peach    // soft peach tint

// Kept-name aliases (old dark tokens, new light meaning)
val Ink = Color(0xFFFFFFFF)          // text/icon that sits ON an accent -> white
val InkRaised: Color get() = SurfaceLav                // a raised/selected row
val InkLine: Color get() = Looks.palette.hairline      // hairlines and dividers

val Chalk = Color(0xFF1C1C1E)        // primary text (near-black, faintly warm)
val Smoke = Color(0xFF6F6F80)        // secondary text (>= 4.5:1 on white)
val Ash = Color(0xFFB4B4C0)          // decoration and placeholders only -- never body copy

// A single flat accent, for marks and small text where a gradient is overkill.
val Acid: Color get() = Looks.palette.accent           // the look's accent
val AcidDim: Color get() = Looks.palette.accentDim     // its quiet, de-emphasised step

val Blue = Color(0xFF0095F6)         // Instagram blue, for plain links
val Good = Color(0xFF22C55E)         // "name is free" green
val Heat = Color(0xFFED4956)         // errors, and when the numbers get ugly

// The look's gradient, as ordered stops. The brush itself lives in Gradient.kt.
val Grad1: Color get() = Looks.palette.stops[0]
val Grad2: Color get() = Looks.palette.stops[1]
val Grad3: Color get() = Looks.palette.stops[2]
val Grad4: Color get() = Looks.palette.stops[3]
val Grad5: Color get() = Looks.palette.stops[4]

val InstaStops: List<Color> get() = Looks.palette.stops

/**
 * The gradient without its palest end, for anything carrying white text:
 * buttons, number badges, the active tab.
 */
val ButtonStops: List<Color> get() = Looks.palette.buttonStops

// Each app's own mark, the same in every look: the small dots and tags that
// say whose number is Reels and whose is Shorts.
val ReelsMark = Color(0xFFC13584)
val ReelsMarkSoft = Color(0xFFFDEEF4)
val ShortsMark = Color(0xFFD0001A)
val ShortsMarkSoft = Color(0xFFFDECEC)

// Soft status tints, for chips and banners.
val GoodSoft = Color(0xFFE8F8EE)
val HeatSoft = Color(0xFFFDECEE)
