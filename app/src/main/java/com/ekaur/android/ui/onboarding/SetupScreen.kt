package com.ekaur.android.ui.onboarding

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.diagnostics.BugReport
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.setup.SetupFlow
import com.ekaur.android.ui.common.AppBadge
import com.ekaur.android.ui.common.BannerTone
import com.ekaur.android.ui.common.Bullet
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Celebration
import com.ekaur.android.ui.common.ChipTone
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.GradientProgress
import com.ekaur.android.ui.common.IconTile
import com.ekaur.android.ui.common.InfoBanner
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.ScreenHeader
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.SegmentedToggle
import com.ekaur.android.ui.common.Spinner
import com.ekaur.android.ui.common.StatusChip
import com.ekaur.android.ui.common.ToggleRow
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.common.reveal
import com.ekaur.android.ui.feedback.rememberReporter
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.AppLook
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Canvas
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.GoodSoft
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.HeatSoft
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceBlush
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.SurfacePeach
import com.ekaur.android.ui.theme.instaGradient
import com.ekaur.android.update.UpdateState
import kotlinx.coroutines.delay

/**
 * Getting it working, and keeping it working.
 *
 * Built around one rule: one clear next action on the screen, everything else
 * quiet. While a required switch is off, that action is "Guided setup"; once
 * both are on, it's the first recommended one still missing; after that
 * nothing here shouts at all.
 *
 * Account settings (photo, name, recovery, leaderboard visibility) live on the
 * Account screen, reached from the avatar on Home.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    permissions: PermissionState,
    onGuidedSetup: (recovery: Boolean) -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenStatus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = "Setup",
            subtitle = "Get counting. Keep counting.",
            modifier = Modifier.reveal(0),
        )

        StatusHero(
            container = container,
            permissions = permissions,
            onGuidedSetup = { onGuidedSetup(false) },
            modifier = Modifier.reveal(1),
        )
        Spacer(Modifier.height(12.dp))
        // Switched on in Settings isn't the same as running: after a crash, or
        // some phones' handling of a self-switch-off, the flag stays on with
        // nothing behind it. Only said after a moment, since the service can
        // take a second to connect when the app starts.
        val connected by container.serviceStatus.connected.collectAsState()
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(3_000)
            settled = true
        }
        PermissionsCard(
            permissions = permissions,
            notRunning = permissions.service && !connected && settled,
            onFixRestricted = { onGuidedSetup(true) },
            modifier = Modifier.reveal(2),
        )
        Spacer(Modifier.height(12.dp))
        AppsCard(container, Modifier.reveal(3))
        Spacer(Modifier.height(12.dp))
        PaymentsCard(container, permissions, Modifier.reveal(3))
        Spacer(Modifier.height(12.dp))
        UpdatesCard(container, Modifier.reveal(4))
        Spacer(Modifier.height(12.dp))
        FeedbackCard(container, Modifier.reveal(5))
        Spacer(Modifier.height(12.dp))
        HelpCard(onOpenEvents, onOpenStatus, Modifier.reveal(5))

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ek Aur (One More) · ${container.updateManager.currentVersionLabel}\n" +
                "Only your name and daily total ever leave this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// 1 -- Status hero
// ---------------------------------------------------------------------------

@Composable
private fun StatusHero(
    container: AppContainer,
    permissions: PermissionState,
    onGuidedSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val required = permissions.requiredMissing
    val allDone = permissions.allGranted
    val recommendedOn = listOf(permissions.battery, permissions.usage).count { it }

    // Confetti once, on the moment both required switches come on -- seen
    // happen, not merely found true on opening the app.
    var celebrate by remember { mutableStateOf<Int?>(null) }
    var wasDone by remember { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !wasDone && !container.settings.setupCelebrated) {
            container.settings.setupCelebrated = true
            haptics.confirm()
            celebrate = (celebrate ?: 0) + 1
        }
        wasDone = allDone
    }

    Box(modifier) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = (SetupFlow.REQUIRED - required).toFloat() / SetupFlow.REQUIRED,
                    done = allDone,
                    label = "${SetupFlow.REQUIRED - required}/${SetupFlow.REQUIRED}",
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (allDone) "You're all set." else SetupFlow.statusLine(required),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            !allDone -> "Two switches and it starts counting. The guided setup walks you through both."
                            recommendedOn < 2 -> "Counting works. $recommendedOn of 2 extras on — they stop your phone killing it."
                            else -> "Counting, and nothing's going to stop it. Go scroll."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                }
            }
            if (!allDone) {
                Spacer(Modifier.height(16.dp))
                FlatButton(
                    text = "Guided setup",
                    icon = EkIcons.Sparkle,
                    emphasised = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGuidedSetup,
                )
            }
        }
        Celebration(key = celebrate, modifier = Modifier.matchParentSize())
    }
}

/** A gradient ring that eases to [fraction]; a check when [done]. */
@Composable
private fun ProgressRing(
    fraction: Float,
    done: Boolean,
    label: String,
    size: Dp = 72.dp,
) {
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.emphasised(), label = "ring")
    val pop by animateFloatAsState(if (done) 1f else 0f, Motion.bouncy(), label = "ring-check")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = SurfaceLav,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            if (sweep > 0f) {
                drawArc(
                    brush = instaGradient(),
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (done) {
            Box(
                Modifier
                    .size(size * 0.56f)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .clip(CircleShape)
                    .background(Good),
                contentAlignment = Alignment.Center,
            ) {
                EkIcon(EkIcons.Check, tint = Ink, size = 22.dp)
            }
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Chalk)
        }
    }
}

// ---------------------------------------------------------------------------
// 2 -- Permissions
// ---------------------------------------------------------------------------

@Composable
private fun PermissionsCard(
    permissions: PermissionState,
    notRunning: Boolean,
    onFixRestricted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Exactly one gradient button: the hero owns it while a required switch
    // is off; after that it passes to the first recommended one still missing.
    val primaryRecommended = when {
        !permissions.allGranted -> null
        !permissions.battery -> "battery"
        !permissions.usage -> "usage"
        else -> null
    }

    Card(modifier) {
        SectionLabel("Permissions")
        Spacer(Modifier.height(12.dp))
        GroupLabel("Required")
        PermRow(
            icon = EkIcons.Person,
            title = "Accessibility",
            why = if (notRunning) {
                "Switched on, but not running. Turn it off and on again."
            } else {
                "Counts your Reels and Shorts. Sees the swipe, nothing else."
            },
            done = permissions.service && !notRunning,
            required = true,
            actionLabel = if (notRunning) "Restart it" else "Turn on",
            onAction = { ServiceControl.openAccessibilityServiceDetails(context) },
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notRunning) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = "Blocked by \"Restricted setting\"? That's normal.",
                    icon = EkIcons.Lock,
                    action = "Fix it",
                    onAction = onFixRestricted,
                )
            }
        }
        PermRow(
            icon = EkIcons.FloatingButton,
            title = "Overlay",
            why = "Floats the counter while you scroll.",
            done = permissions.overlay,
            required = true,
            actionLabel = "Allow",
            onAction = { ServiceControl.openOverlaySettings(context) },
        )

        Spacer(Modifier.height(8.dp))
        GroupLabel("Recommended")
        PermRow(
            icon = EkIcons.Battery,
            title = "Battery",
            why = "Stops Realme, Xiaomi, Oppo and Vivo killing it in the background.",
            done = permissions.battery,
            required = false,
            primary = primaryRecommended == "battery",
            actionLabel = "Allow",
            onAction = { ServiceControl.openBatterySettings(context) },
        )
        PermRow(
            icon = EkIcons.Activity,
            title = "Usage access",
            why = "Lets it notice you left Instagram and YouTube, so it can switch off for payments.",
            done = permissions.usage,
            required = false,
            primary = primaryRecommended == "usage",
            actionLabel = "Allow",
            onAction = { ServiceControl.openUsageAccessSettings(context) },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Smoke,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/**
 * One permission. Granted, it folds to a single tidy line with a green check
 * that pops in; pending, it opens to one line of why and its button.
 */
@Composable
private fun PermRow(
    icon: ImageVector,
    title: String,
    why: String,
    done: Boolean,
    required: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    primary: Boolean = false,
    extra: @Composable (() -> Unit)? = null,
) {
    val check by animateFloatAsState(if (done) 1f else 0f, Motion.bouncy(), label = "perm-check")
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon,
                tint = if (done) GoodSoft else if (required) SurfaceBlush else SurfaceLav,
                iconTint = if (done) Good else if (required) Acid else Smoke,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Chalk)
                if (done) {
                    Text("On", style = MaterialTheme.typography.bodySmall, color = Good)
                }
            }
            if (done) {
                Box(
                    Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = check; scaleY = check; alpha = check }
                        .clip(CircleShape)
                        .background(Good),
                    contentAlignment = Alignment.Center,
                ) {
                    EkIcon(EkIcons.Check, tint = Ink, size = 15.dp)
                }
            } else {
                StatusChip(
                    text = if (required) "Needed" else "Optional",
                    tone = if (required) ChipTone.Warn else ChipTone.Neutral,
                )
            }
        }
        AnimatedVisibility(
            visible = !done,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
            exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
        ) {
            Column(Modifier.padding(start = 52.dp)) {
                Spacer(Modifier.height(4.dp))
                Text(why, style = MaterialTheme.typography.bodyMedium, color = Smoke)
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = actionLabel,
                    emphasised = primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAction,
                )
                extra?.invoke()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2b -- Apps and look
// ---------------------------------------------------------------------------

@Composable
private fun AppsCard(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps by container.settings.countedApps.collectAsState()
    val override by container.settings.lookOverride.collectAsState()

    Card(modifier) {
        SectionLabel("Apps")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "What gets counted. Both go into the same number.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        Spacer(Modifier.height(10.dp))
        TrackedApp.entries.forEach { app ->
            val on = app in apps
            val installed = remember(app) { ServiceControl.isInstalled(context, app) }
            // The last app on can't be switched off: counting nothing is what
            // the accessibility switch is for.
            val onlyOne = on && apps.size == 1
            Row(
                Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(app, modifier = Modifier.alpha(if (installed) 1f else 0.4f))
                Spacer(Modifier.width(12.dp))
                ToggleRow(
                    title = "${app.appName} ${app.items}",
                    subtitle = when {
                        !installed -> "Not installed on this phone"
                        onlyOne -> "Always on while it's the only one"
                        on -> "Counting"
                        else -> "Not counted"
                    },
                    checked = on,
                    onCheckedChange = { want ->
                        if (!onlyOne || want) {
                            container.settings.setCounting(app, want)
                            container.settings.appsChosen = true
                            container.settings.shortsAsked = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Look", style = MaterialTheme.typography.titleMedium, color = Chalk)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Auto matches the apps above. Or pick one you like.",
            style = MaterialTheme.typography.bodySmall,
            color = Smoke,
        )
        Spacer(Modifier.height(10.dp))
        val options = listOf(null, AppLook.Instagram.name, AppLook.Shorts.name, AppLook.Both.name)
        SegmentedToggle(
            options = listOf("Auto", "Reels", "Shorts", "Both"),
            selectedIndex = options.indexOf(override).coerceAtLeast(0),
            onSelect = { container.settings.setLookOverride(options[it]) },
            segmentWidth = 66.dp,
        )
    }
}

// ---------------------------------------------------------------------------
// 3 -- Payments
// ---------------------------------------------------------------------------

@Composable
private fun PaymentsCard(
    container: AppContainer,
    permissions: PermissionState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var autoOff by remember { mutableStateOf(container.settings.autoOffOnLeave) }
    var paused by remember { mutableStateOf(false) }

    Card(modifier) {
        SectionLabel("Payments")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Bank and UPI apps may refuse to pay while any accessibility service is on. " +
                "It happens to every app not from the Play Store.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        Spacer(Modifier.height(14.dp))
        ToggleRow(
            title = "Turn off when I stop scrolling",
            subtitle = if (autoOff) {
                "Leave Instagram and YouTube and it switches itself off, so payments just work."
            } else {
                "You'll pause it yourself before paying."
            },
            checked = autoOff,
            onCheckedChange = {
                autoOff = it
                container.settings.autoOffOnLeave = it
            },
        )
        AnimatedVisibility(visible = autoOff && !permissions.usage) {
            Column {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = "Auto-off needs Usage access.",
                    tone = BannerTone.Warn,
                    icon = EkIcons.Warning,
                    action = "Allow",
                    onAction = { ServiceControl.openUsageAccessSettings(context) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Expandable("Pause it yourself") {
            OptionRow(
                icon = EkIcons.FloatingButton,
                title = "Floating button",
                body = "The fastest. A small button on every screen, even the bank app.",
                action = "Set up",
                onAction = { ServiceControl.openAccessibilityServiceDetails(context) },
            )
            OptionRow(
                icon = EkIcons.Tiles,
                title = "Quick Settings tile",
                body = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Pull down the shade, tap Ek Aur. One tap off, one tap on."
                } else {
                    "Add the Ek Aur tile from the shade's edit (pencil) screen."
                },
                action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Add tile" else null,
                onAction = { ServiceControl.requestAddPauseTile(context) },
            )
            OptionRow(
                icon = if (paused) EkIcons.CheckCircle else EkIcons.Pause,
                title = if (paused) "Paused" else "Pause now",
                body = if (paused) {
                    "Counting is off. Turn it back on in Accessibility after paying."
                } else {
                    "Switches counting off right now, for one payment."
                },
                action = if (paused) "Turn back on" else "Pause",
                onAction = {
                    if (paused) {
                        ServiceControl.openAccessibilitySettings(context)
                    } else {
                        ServiceControl.pauseForPayment()
                        paused = true
                    }
                },
            )
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    loading: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Smoke)
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            FlatButton(action, onClick = onAction, loading = loading)
        }
    }
}

// ---------------------------------------------------------------------------
// 4 -- Updates
// ---------------------------------------------------------------------------

@Composable
private fun UpdatesCard(container: AppContainer, modifier: Modifier = Modifier) {
    val manager = container.updateManager
    val state by manager.state.collectAsState()
    var autoUpdate by remember { mutableStateOf(manager.autoDownload) }

    Card(modifier) {
        SectionLabel("Updates")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (state is UpdateState.Checking) {
                    Spinner(size = 24.dp)
                } else {
                    IconTile(
                        icon = when (state) {
                            is UpdateState.UpToDate -> EkIcons.CheckCircle
                            is UpdateState.Available, is UpdateState.Ready -> EkIcons.Gift
                            is UpdateState.Downloading -> EkIcons.Download
                            is UpdateState.Failed -> EkIcons.Alert
                            else -> EkIcons.Box
                        },
                        tint = when (state) {
                            is UpdateState.UpToDate -> GoodSoft
                            is UpdateState.Failed -> HeatSoft
                            else -> SurfaceLav
                        },
                        iconTint = when (state) {
                            is UpdateState.UpToDate -> Good
                            is UpdateState.Failed -> Heat
                            else -> Acid
                        },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Version ${manager.currentVersionLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk,
                )
                val (note, color) = when (val s = state) {
                    is UpdateState.Checking -> "Checking GitHub…" to Smoke
                    is UpdateState.UpToDate -> "You're on the latest version." to Good
                    is UpdateState.Available -> "Version ${s.release.versionName} is out." to Chalk
                    is UpdateState.Downloading -> "Downloading ${s.release.versionName}… ${(s.progress * 100).toInt()}%" to Smoke
                    is UpdateState.Ready -> "Version ${s.release.versionName} is ready to install." to Chalk
                    is UpdateState.Failed -> s.reason to Heat
                    is UpdateState.Idle -> "Checks on its own every few hours." to Smoke
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        (state as? UpdateState.Downloading)?.let {
            Spacer(Modifier.height(12.dp))
            GradientProgress(fraction = it.progress, height = 6.dp, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(14.dp))
        when (val s = state) {
            is UpdateState.Ready -> FlatButton(
                text = "Install ${s.release.versionName}",
                emphasised = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { manager.install() },
            )
            is UpdateState.Available -> FlatButton(
                text = "Download ${s.release.versionName}",
                emphasised = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { manager.startDownload(s.release) },
            )
            else -> FlatButton(
                text = "Check for updates",
                loading = state is UpdateState.Checking,
                enabled = state !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth(),
                onClick = { manager.checkOnLaunch(force = true) },
            )
        }
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "Auto-download updates",
            subtitle = "Fetch new versions in the background, then one tap to install.",
            checked = autoUpdate,
            onCheckedChange = {
                autoUpdate = it
                manager.setAutoDownload(it)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 5 -- Feedback
// ---------------------------------------------------------------------------

/**
 * The website's contact section, inside the app: each row opens the mail app
 * addressed to the developer. A bug report attaches the event log and a
 * status snapshot on its own, so "it stopped counting" arrives with the
 * evidence to fix it.
 */
@Composable
private fun FeedbackCard(container: AppContainer, modifier: Modifier = Modifier) {
    val reporter = rememberReporter(container)
    Card(modifier) {
        SectionLabel("Feedback")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Something broken, something missing, or just something to say? It all lands in my inbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackRow(
                icon = EkIcons.Bug,
                tint = HeatSoft,
                iconTint = Heat,
                title = "Report a bug",
                body = "Opens Gmail with the event log and a status snapshot attached.",
                loading = reporter.busy == BugReport.Kind.Bug,
                onClick = { reporter.send(BugReport.Kind.Bug) },
            )
            FeedbackRow(
                icon = EkIcons.Lightbulb,
                tint = SurfacePeach,
                iconTint = Color(0xFFE08600),
                title = "Suggest a feature",
                body = "What should Ek Aur do next?",
                loading = reporter.busy == BugReport.Kind.Feature,
                onClick = { reporter.send(BugReport.Kind.Feature) },
            )
            FeedbackRow(
                icon = EkIcons.Mail,
                tint = SurfaceLav,
                iconTint = Acid,
                title = "Send feedback",
                body = "Love it, hate it, got roasted too hard. Say it.",
                loading = reporter.busy == BugReport.Kind.Feedback,
                onClick = { reporter.send(BugReport.Kind.Feedback) },
            )
        }
    }
}

/**
 * One way to reach the developer: the whole row is the button. A tinted icon
 * says what kind of message it is; the chevron turns into a spinner while a
 * bug report gathers its attachments.
 */
@Composable
private fun FeedbackRow(
    icon: ImageVector,
    tint: Color,
    iconTint: Color,
    title: String,
    body: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(Canvas)
            .border(1.dp, InkLine, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = tint, iconTint = iconTint, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Chalk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Smoke)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                Spinner(size = 18.dp)
            } else {
                EkIcon(EkIcons.ChevronDown, tint = Ash, size = 18.dp, modifier = Modifier.rotate(-90f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6 -- Help
// ---------------------------------------------------------------------------

@Composable
private fun HelpCard(
    onOpenEvents: () -> Unit,
    onOpenStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reset by remember { mutableStateOf(false) }
    LaunchedEffect(reset) {
        if (reset) {
            delay(2_000)
            reset = false
        }
    }

    Card(modifier) {
        SectionLabel("Help")
        Spacer(Modifier.height(8.dp))
        Expandable("Counter missing?") {
            Text(
                text = "Dragged it off to the edge and lost it? This puts it back at the top middle.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(10.dp))
            FlatButton(
                text = if (reset) "Done" else "Reset counter position",
                icon = if (reset) EkIcons.Check else null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    OverlayPrefs(context).clearPosition()
                    reset = true
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Expandable("Trouble installing?") {
            Bullet("\"App blocked\" by Play Protect: Play Store → profile → Play Protect → settings (gear) → turn off scanning, install, then turn it back on.")
            Bullet("\"App not installed\" means an older file over a newer one. Install the newest.")
            Bullet("\"Restricted setting\" on the switch: tap the switch once and press OK, then App info → ⋮ → Allow restricted settings, then switch it on. The ⋮ item only appears after that first tap.")
        }
        Spacer(Modifier.height(6.dp))
        Expandable("Developer") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatButton("Events", onClick = onOpenEvents, modifier = Modifier.weight(1f))
                FlatButton("Status", onClick = onOpenStatus, modifier = Modifier.weight(1f))
            }
        }
    }
}
