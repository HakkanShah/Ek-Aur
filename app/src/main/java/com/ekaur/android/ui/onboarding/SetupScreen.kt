package com.ekaur.android.ui.onboarding

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.di.AppContainer
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.update.UpdateState
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.ToggleRow
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient

/**
 * Getting it working: permissions, payments, and help.
 *
 * Account settings (photo, name, recovery, leaderboard visibility) live on their
 * own Account screen now, reached from the avatar in the home header, so this
 * screen is only the setup steps.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    serviceEnabled: Boolean,
    onOpenEvents: () -> Unit = {},
    onOpenStatus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reset by remember { mutableStateOf(false) }
    var paymentPaused by remember { mutableStateOf(false) }
    var usageOk by remember { mutableStateOf(ServiceControl.hasUsageAccess(context)) }
    var autoOff by remember { mutableStateOf(container.settings.autoOffOnLeave) }
    var autoUpdate by remember { mutableStateOf(container.updateManager.autoDownload) }
    val updateState by container.updateManager.state.collectAsState()

    LifecycleResumeEffect(Unit) {
        usageOk = ServiceControl.hasUsageAccess(context)
        onPauseOrDispose { }
    }

    val canOverlay = ServiceControl.canDrawOverlay(context)
    val batteryExempt = ServiceControl.isIgnoringBatteryOptimisations(context)
    val allDone = serviceEnabled && canOverlay && batteryExempt && usageOk
    val stepsLeft = listOf(serviceEnabled, canOverlay, batteryExempt, usageOk).count { !it }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // 1 — Status + permissions, one card
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(if (allDone) Good else Heat)
                Text(
                    text = if (allDone) "You're all set. Go scroll." else "$stepsLeft steps left.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                )
            }
            Spacer(Modifier.height(14.dp))
            ProgressBar(done = 4 - stepsLeft, total = 4)
            Spacer(Modifier.height(18.dp))
            SectionLabel("Permissions")
            Spacer(Modifier.height(6.dp))
            PermRow("Accessibility", "Counts your reels. Nothing works without it.", serviceEnabled,
                "Open", { ServiceControl.openAccessibilitySettings(context) }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Blocked by a \"Restricted setting\" popup? App info → ⋮ " +
                        "→ Allow restricted settings, then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(8.dp))
                FlatButton("Open app info", onClick = { ServiceControl.openAppInfo(context) })
            }
            PermRow("Overlay", "Shows the counter over Instagram.", canOverlay,
                "Allow", { ServiceControl.openOverlaySettings(context) })
            PermRow("Battery", "Some phones kill background apps and counting stops.", batteryExempt,
                "Allow", { ServiceControl.openBatterySettings(context) })
            PermRow("Usage access", "Lets the app tell when you've left Instagram.", usageOk,
                "Allow", { ServiceControl.openUsageAccessSettings(context) })
        }

        Spacer(Modifier.height(12.dp))

        // 2 — Payments
        Card {
            SectionLabel("Payments")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "A bank or UPI app may block a payment while any accessibility service " +
                    "is on. It happens to any app not from the Play Store — nothing's wrong here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                title = "Turn off when I leave Instagram",
                subtitle = if (autoOff) {
                    "Ek Aur turns itself off when you leave, so payments stay clean. " +
                        "Tap it on to scroll."
                } else {
                    "You'll turn it off yourself before each payment."
                },
                checked = autoOff,
                onCheckedChange = {
                    autoOff = it
                    container.settings.autoOffOnLeave = it
                },
            )
            if (autoOff && !usageOk) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Needs \"Usage access\" above to work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Heat,
                )
            }

            Spacer(Modifier.height(16.dp))
            Expandable("Pause it yourself") {
                Text(
                    text = "Floating button — the fastest. Set up a shortcut and a small " +
                        "button floats on any screen, even over a payment app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton("Set up shortcut", emphasised = true,
                    onClick = { ServiceControl.openAccessibilityServiceDetails(context) })

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Quick Settings tile — add the \"Ek Aur\" tile to your shade, " +
                        "then one tap off, one tap on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(10.dp))
                    FlatButton("Add tile", onClick = { ServiceControl.requestAddPauseTile(context) })
                }

                Spacer(Modifier.height(16.dp))
                if (paymentPaused) {
                    Text(
                        text = "Turned off. Turn it back on in accessibility settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Acid,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlatButton("Turn back on",
                        onClick = { ServiceControl.openAccessibilitySettings(context) })
                } else {
                    FlatButton("Turn off for a payment", onClick = {
                        ServiceControl.pauseForPayment()
                        paymentPaused = true
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 3 — Updates
        Card {
            SectionLabel("Updates")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "You're on " + container.updateManager.currentVersionLabel + ".",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            val updateNote = when (updateState) {
                is UpdateState.Checking -> "Checking…"
                is UpdateState.UpToDate -> "You're on the latest version."
                is UpdateState.Available -> "An update is available."
                is UpdateState.Downloading -> "Downloading the update…"
                is UpdateState.Ready -> "An update is downloaded and ready to install."
                is UpdateState.Failed -> "Couldn't check right now."
                is UpdateState.Idle -> null
            }
            if (updateNote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = updateNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (updateState is UpdateState.Failed) Heat else Acid,
                )
            }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Auto-download updates",
                subtitle = "Grab new versions in the background, then tap to install.",
                checked = autoUpdate,
                onCheckedChange = {
                    autoUpdate = it
                    container.updateManager.setAutoDownload(it)
                },
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = "Check for updates",
                emphasised = true,
                onClick = { container.updateManager.checkOnLaunch(force = true) },
            )
        }

        Spacer(Modifier.height(12.dp))

        // 4 — Counter reset + install help + developer, all tucked away
        Card {
            SectionLabel("More")
            Spacer(Modifier.height(12.dp))
            Expandable("Counter missing?") {
                Text(
                    text = "If you dragged it to the edge and it vanished, bring it back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(12.dp))
                FlatButton(
                    text = if (reset) "Done ✓" else "Reset counter",
                    onClick = { OverlayPrefs(context).clearPosition(); reset = true },
                )
            }
            Spacer(Modifier.height(16.dp))
            Expandable("Trouble installing?") {
                Text(
                    text = "• \"App blocked\" by Play Protect: Play Store → profile " +
                        "→ Play Protect → ⚙ → turn off scanning, install, turn it " +
                        "back on.\n\n• \"App not installed\" means an older APK over a newer " +
                        "one — install the newest file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
            Spacer(Modifier.height(16.dp))
            Expandable("Developer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatButton("Events", onClick = onOpenEvents)
                    FlatButton("Status", onClick = onOpenStatus)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** One permission: a status dot, a title, and -- only if it's off -- a line and a button. */
@Composable
private fun PermRow(
    title: String,
    why: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(if (done) Good else Ash)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
            Spacer(Modifier.weight(1f))
            if (done) Text("✓", style = MaterialTheme.typography.titleLarge, color = Good)
        }
        if (!done) {
            Spacer(Modifier.height(6.dp))
            Text(why, style = MaterialTheme.typography.bodyMedium, color = Smoke)
            Spacer(Modifier.height(10.dp))
            FlatButton(actionLabel, emphasised = true, onClick = onAction)
            extra?.invoke()
        }
    }
}

/** A slim gradient progress bar: how many of the setup steps are done. */
@Composable
private fun ProgressBar(done: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(color = SurfaceLav),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(brush = instaGradient()),
            )
        }
    }
}
