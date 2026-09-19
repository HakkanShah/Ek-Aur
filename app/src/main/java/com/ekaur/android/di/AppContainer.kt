package com.ekaur.android.di

import android.content.Context

/**
 * Manual dependency container, built once in [com.ekaur.android.EkAurApp].
 *
 * Deliberately not Hilt: this app wires an AccessibilityService and an overlay
 * that lives outside any Activity, which is exactly where Hilt's scoping gets
 * awkward, and Room already costs one KSP pass without adding a second.
 *
 * Grows as phases land -- database, repositories, detector and Supabase client
 * all hang off here.
 */
class AppContainer(@Suppress("unused") private val appContext: Context)
