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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    serviceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
