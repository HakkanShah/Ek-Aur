package com.ekaur.android.ui.theme

import androidx.compose.ui.graphics.Color

// A soft, light, Instagram-flavoured palette. Warm off-white rather than a wall
// of white, clean cards, and the Instagram gradient carrying the accent. The
// names are kept from the old dark theme so screens keep compiling; only the
// values change.

// Surfaces
val Canvas = Color(0xFFFBF7FB)       // page background: barely-lavender off-white
val Surface = Color(0xFFFFFFFF)      // cards
val SurfaceLav = Color(0xFFF3EEFB)   // soft lavender tint (highlights, chips)
val SurfaceBlush = Color(0xFFFDEEF4) // soft pink tint
val SurfacePeach = Color(0xFFFFF2E9) // soft peach tint

// Kept-name aliases (old dark tokens, new light meaning)
val Ink = Color(0xFFFFFFFF)          // text/icon that sits ON an accent -> white
val InkRaised = SurfaceLav           // a raised/selected row on the canvas
val InkLine = Color(0xFFECE7F2)      // hairlines and dividers

val Chalk = Color(0xFF1C1C1E)        // primary text (near-black, faintly warm)
val Smoke = Color(0xFF6F6F80)        // secondary text (>= 4.5:1 on white)
val Ash = Color(0xFFB4B4C0)          // decoration and placeholders only -- never body copy

// A single flat accent, for marks and small text where a gradient is overkill.
val Acid = Color(0xFFC13584)         // Instagram magenta
val AcidDim = Color(0xFFE29AC6)      // its quiet, de-emphasised step

val Blue = Color(0xFF0095F6)         // Instagram blue, for plain links
val Good = Color(0xFF22C55E)         // "name is free" green
val Heat = Color(0xFFED4956)         // errors, and when the numbers get ugly

// The Instagram gradient, as ordered stops. The brush itself lives in
// Gradient.kt so callers get one sized to whatever they are painting.
val Grad1 = Color(0xFF515BD4)        // blue-violet
val Grad2 = Color(0xFF8134AF)        // purple
val Grad3 = Color(0xFFDD2A7B)        // magenta
val Grad4 = Color(0xFFF58529)        // orange
val Grad5 = Color(0xFFFEDA77)        // warm yellow

val InstaStops = listOf(Grad1, Grad2, Grad3, Grad4, Grad5)

/**
 * The gradient without its pale yellow end, for anything carrying white text:
 * buttons, number badges, the active tab. White on #FEDA77 is barely legible.
 */
val ButtonStops = listOf(Grad1, Grad2, Grad3, Grad4)

// Soft status tints, for chips and banners.
val GoodSoft = Color(0xFFE8F8EE)
val HeatSoft = Color(0xFFFDECEE)
