package com.ekaur.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The three things that have to be granted before any of this works.
 *
 * Written out in order with live status, because two of the three fail in ways
 * that give the user nothing to act on: Android blocks the accessibility toggle
 * for sideloaded apps behind a dialog with only an OK button, and battery
 * optimisation kills the service silently.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    serviceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf(false) }
    val username by container.settings.username.collectAsState()
    val hidden by container.settings.hidden.collectAsState()
    var hideBusy by remember { mutableStateOf(false) }
    val canOverlay = ServiceControl.canDrawOverlay(context)
    val batteryExempt = ServiceControl.isIgnoringBatteryOptimisations(context)
    val allDone = serviceEnabled && canOverlay && batteryExempt

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (allDone) {
            Card {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(Acid)
                    Text(
                        text = "sab set hai. ab bas scroll karo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Chalk,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SetupStep(
            index = "01",
            title = "accessibility",
            why = "reels ginne ke liye. iske bina kuch nahi hoga.",
            done = serviceEnabled,
            actionLabel = "accessibility kholo",
            onAction = { ServiceControl.openAccessibilitySettings(context) },
            extra = {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"Restricted setting\" wala popup aaye to Android sideloaded app ko " +
                        "rok raha hai. app info → ⋮ (upar dayein) → Allow restricted settings, " +
                        "phir wapas yahan aake on karo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "app info kholo",
                    onClick = { ServiceControl.openAppInfo(context) },
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "02",
            title = "overlay",
            why = "instagram ke upar counter dikhane ke liye.",
            done = canOverlay,
            actionLabel = "overlay permission do",
            onAction = { ServiceControl.openOverlaySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "03",
            title = "battery",
            why = "realme/oppo/xiaomi background me app ko maar dete hain. " +
                "tab ginti chupchap band ho jaati hai.",
            done = batteryExempt,
            actionLabel = "battery se chhoot do",
            onAction = { ServiceControl.openBatterySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("counter kahin kho gaya?")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "counter ko screen ke bilkul kinare drag kiya ho aur wo " +
                    "dikh na raha ho, to yahan se wapas beech me le aao.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (reset) "ho gaya \u2713" else "counter wapas laao",
                onClick = {
                    OverlayPrefs(context).clearPosition()
                    reset = true
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("leaderboard")
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hidden) {
                    "abhi tum chhupe ho. doosron ki list me tumhara naam aur " +
                        "ginti nahi dikhti."
                } else {
                    "tum \"" + username.orEmpty() + "\" naam se list me ho. " +
                        "sirf naam aur har din ka total dikhta hai."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = when {
                    hideBusy -> "ruko..."
                    hidden -> "wapas list me aao"
                    else -> "chhup jao"
                },
                onClick = {
                    if (hideBusy) return@FlatButton
                    hideBusy = true
                    val target = !hidden
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.setHidden(target) }.isSuccess
                        }
                        // Only mirrored locally once the server agreed, so the
                        // switch never claims something the database did not do.
                        if (ok) container.settings.setHidden(target)
                        hideBusy = false
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("install karte waqt")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "\u2022 Play Protect \"app blocked\" bole to: Play Store \u2192 profile " +
                    "\u2192 Play Protect \u2192 \u2699 \u2192 scanning band karo, install karo, " +
                    "phir wapas chalu kar do. Sideloaded app jo accessibility maangta hai, " +
                    "usko wo hamesha flag karega \u2014 app me kuch galat nahi hai.\n\n" +
                    "\u2022 \"App not installed\" aaye to purani APK install karne ki koshish " +
                    "ho rahi hai. Android purane version ko naye ke upar nahi chadhne deta \u2014 " +
                    "sabse nayi wali file install karo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "sab kuch phone me hi rehta hai. koi account nahi, koi server nahi.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupStep(
    index: String,
    title: String,
    why: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
) {
    Card {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(if (done) Acid else Heat)
            SectionLabel("$index  $title")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = why,
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        if (!done) {
            Spacer(Modifier.height(14.dp))
            FlatButton(text = actionLabel, emphasised = true, onClick = onAction)
            extra?.invoke()
        }
    }
}
