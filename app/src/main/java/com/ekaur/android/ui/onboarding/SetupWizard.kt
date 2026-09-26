package com.ekaur.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.service.KeepAlive
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.setup.RestrictedSetting
import com.ekaur.android.setup.SetupFlow
import com.ekaur.android.setup.SetupStep
import com.ekaur.android.setup.Verdict
import com.ekaur.android.ui.common.AppBadge
import com.ekaur.android.ui.common.AppWords
import com.ekaur.android.ui.common.Celebration
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.RoundIconButton
import com.ekaur.android.ui.common.mark
import com.ekaur.android.ui.common.markSoft
import com.ekaur.android.ui.common.pressScale
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.GoodSoft
import com.ekaur.android.ui.theme.Ink
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.delay

/**
 * The guided setup: one screen per step, each one a short film of the exact
 * taps, a headline, one line, and one button.
 *
 * Friends were installing the app and giving up in Settings: the steps were
 * paragraphs, the instructions vanished the moment they left the app, and on
 * Xiaomi the phone quietly refused to start the service even with the switch
 * on. So now:
 * - Every step **shows** what to do ([SetupSims]) instead of describing it.
 * - Overlay comes first, so a small guide can **float over Settings**
 *   ([SetupGuide]) and the app **comes back by itself** once a step works.
 * - The phone's own limits (battery, Autostart) are cleared **before** the
 *   accessibility switch, so it starts the first time.
 *
 * The step is never stored. It is derived from [permissions] on every pass
 * ([SetupFlow.nextStep]), so a switch flipped in Settings moves the flow
 * forward with nothing to fall out of sync.
 */
@Composable
fun SetupWizard(
    permissions: PermissionState,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onAutostartVisited: () -> Unit,
    onReturnAfterConnect: () -> Unit,
    startInRecovery: Boolean = false,
    onClose: (() -> Unit)? = null,
    apps: Set<TrackedApp> = setOf(TrackedApp.Instagram),
    onChooseApps: (Set<TrackedApp>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var welcomed by rememberSaveable { mutableStateOf(startInRecovery || permissions.service) }
    var keepAliveSkipped by rememberSaveable { mutableStateOf(false) }
    val step = SetupFlow.nextStep(
        welcomed = welcomed,
        overlay = permissions.overlay,
        keepAlive = permissions.keepAliveDone || keepAliveSkipped,
        service = permissions.service,
        running = permissions.running,
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != SetupStep.Welcome && step != SetupStep.Done) {
                ProgressDots(SetupFlow.dotIndex(step))
            }
            Spacer(Modifier.weight(1f))
            if (onClose != null) {
                RoundIconButton(icon = EkIcons.Close, description = "Close setup", onClick = onClose)
            }
        }
        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 4 } +
                    fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(220)) { -dir * it / 4 } + fadeOut(tween(180)))
            },
            label = "setup-step",
        ) { current ->
            when (current) {
                SetupStep.Welcome -> WelcomeStep(
                    apps = apps,
                    onToggle = { app ->
                        val next = if (app in apps) apps - app else apps + app
                        if (next.isNotEmpty()) onChooseApps(next)
                    },
                    onStart = {
                        onChooseApps(apps)
                        welcomed = true
                    },
                    onSkip = onSkip,
                )
                SetupStep.Overlay -> OverlayStep(onSkip = onSkip)
                SetupStep.KeepAlive -> KeepAliveStep(
                    permissions = permissions,
                    onAutostartVisited = onAutostartVisited,
                    onSkip = { keepAliveSkipped = true },
                )
                SetupStep.Accessibility -> AccessibilityStep(
                    startInRecovery = startInRecovery,
                    onReturnAfterConnect = onReturnAfterConnect,
                    onSkip = onSkip,
                )
                SetupStep.Restart -> RestartStep(
                    keepAliveDone = permissions.keepAliveDone,
                    onFixKeepAlive = { keepAliveSkipped = false },
                    onReturnAfterConnect = onReturnAfterConnect,
                    onSkip = onSkip,
                )
                SetupStep.Done -> DoneStep(apps = apps, onDone = onDone)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

/**
 * The shape every step shares: the film, a headline, a line, one button, and
 * a quiet way out. Kept identical so each new step reads at a glance.
 */
@Composable
private fun StepLayout(
    film: @Composable () -> Unit,
    title: String,
    line: String,
    action: String,
    onAction: () -> Unit,
    quiet: String? = null,
    onQuiet: () -> Unit = {},
    extra: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        film()
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
            textAlign = TextAlign.Center,
        )
        if (extra != null) {
            Spacer(Modifier.height(14.dp))
            extra()
        }
        Spacer(Modifier.height(20.dp))
        FlatButton(action, emphasised = true, modifier = Modifier.fillMaxWidth(), onClick = onAction)
        if (quiet != null) {
            Spacer(Modifier.height(4.dp))
            FlatButton(quiet, quiet = true, modifier = Modifier.fillMaxWidth(), onClick = onQuiet)
        }
    }
}

@Composable
private fun WelcomeStep(
    apps: Set<TrackedApp>,
    onToggle: (TrackedApp) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        WelcomeSim(scale = 0.62f)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "What do you scroll?",
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text("Pick one or both.", style = MaterialTheme.typography.bodyLarge, color = Smoke)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackedApp.entries.forEach { app ->
                AppChoice(
                    app = app,
                    selected = app in apps,
                    installed = remember(app) { ServiceControl.isInstalled(context, app) },
                    onClick = { onToggle(app) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StepsPreview()
        Spacer(Modifier.height(18.dp))
        FlatButton("Let's go", emphasised = true, modifier = Modifier.fillMaxWidth(), onClick = onStart)
        Spacer(Modifier.height(4.dp))
        FlatButton("Skip for now", quiet = true, modifier = Modifier.fillMaxWidth(), onClick = onSkip)
    }
}

/** "3 quick steps", as three icons -- what's coming, without a paragraph. */
@Composable
private fun StepsPreview() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceLav)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewItem(EkIcons.Layers, "Overlay")
        PreviewArrow()
        PreviewItem(EkIcons.Battery, "Keep alive")
        PreviewArrow()
        PreviewItem(EkIcons.Person, "Accessibility")
    }
}

@Composable
private fun PreviewItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { EkIcon(icon, tint = Chalk, size = 18.dp) }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Chalk)
    }
}

@Composable
private fun PreviewArrow() {
    Text("›", color = Ash, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun OverlayStep(onSkip: () -> Unit) {
    val context = LocalContext.current
    StepLayout(
        film = { OverlaySim() },
        title = "Let the counter float",
        line = "One switch. We'll bring you right back.",
        action = "Turn it on",
        onAction = {
            ServiceControl.openOverlaySettings(context)
            SetupGuide.returnWhen(context) { ServiceControl.canDrawOverlay(it) }
        },
        quiet = "Skip setup",
        onQuiet = onSkip,
    )
}

/**
 * Battery first (a one-tap system popup), then Autostart on the phones that
 * have it. Shows only whichever is still to do.
 */
@Composable
private fun KeepAliveStep(
    permissions: PermissionState,
    onAutostartVisited: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val brand = remember { ServiceControl.oemHint().brand }
    // Pressed "Open Autostart": coming back afterwards counts as done where
    // the phone can't tell us (most can't).
    var autostartOpened by rememberSaveable { mutableStateOf(false) }
    // Counted on coming *back*, not on the press: marking it on the press
    // skipped the step before the user had even seen the Autostart screen.
    // Keyed on nothing: a key change would re-run it at once, while the app
    // is still in front. It reads the latest values when it does run.
    val latest by rememberUpdatedState(permissions)
    val visited by rememberUpdatedState(onAutostartVisited)
    LifecycleResumeEffect(Unit) {
        if (autostartOpened && latest.autostart == null) visited()
        onPauseOrDispose { }
    }

    val checklist: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Battery", permissions.battery)
            if (permissions.autostartScreen) Chip("Autostart", permissions.autostartDone)
        }
    }

    if (!permissions.battery) {
        StepLayout(
            film = { BatterySim() },
            title = "Keep it running",
            line = "Phones stop apps they don't know. Allow this one.",
            action = "Allow",
            onAction = { ServiceControl.openBatterySettings(context) },
            quiet = "Skip",
            onQuiet = onSkip,
            extra = checklist,
        )
    } else {
        StepLayout(
            film = { AutostartSim() },
            title = "Turn on Autostart",
            line = brand?.let { "$it phones need this, or counting never starts." }
                ?: "Your phone needs this, or counting never starts.",
            action = "Open Autostart",
            onAction = {
                autostartOpened = true
                if (KeepAlive.openAutostart(context)) SetupGuide.start(context, SetupGuide.Kind.Autostart)
            },
            quiet = if (autostartOpened) "I turned it on" else "Skip",
            onQuiet = { if (autostartOpened) onAutostartVisited() else onSkip() },
            extra = checklist,
        )
    }
}

@Composable
private fun AccessibilityStep(
    startInRecovery: Boolean,
    onReturnAfterConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val hint = remember { ServiceControl.oemHint() }
    val verdict = remember { ServiceControl.restrictedVerdict(context) }
    val gate = RestrictedSetting.applies(verdict) && verdict != Verdict.Cleared
    // Pressed "Open Accessibility" once: coming back without it means the
    // "Restricted setting" wall is the likeliest reason, so its film takes over.
    var attempted by rememberSaveable { mutableStateOf(startInRecovery) }
    var showWall by rememberSaveable { mutableStateOf(startInRecovery) }
    val wall = showWall || (gate && attempted)
    BackHandler(enabled = wall && !startInRecovery) {
        showWall = false
        attempted = false
    }

    fun openSwitch() {
        attempted = true
        onReturnAfterConnect()
        ServiceControl.openAccessibilityServiceDetails(context)
        SetupGuide.start(context, SetupGuide.Kind.Accessibility)
    }

    AnimatedContent(targetState = wall, label = "wall") { blocked ->
        if (!blocked) {
            StepLayout(
                film = { AccessibilitySim(hint.listSection) },
                title = "Switch on Ek Aur",
                line = "It only sees the swipe to the next video.",
                action = "Open Accessibility",
                onAction = ::openSwitch,
                quiet = if (gate) "Saw “Restricted setting”?" else "Skip setup",
                onQuiet = { if (gate) showWall = true else onSkip() },
            )
        } else {
            StepLayout(
                film = { RestrictedSim() },
                title = "Android blocked it. Normal!",
                line = "Every app outside Play Store gets this once.",
                action = "Open App info",
                onAction = {
                    onReturnAfterConnect()
                    ServiceControl.openAppInfo(context)
                    SetupGuide.start(context, SetupGuide.Kind.Restricted)
                },
                quiet = "Open Accessibility",
                onQuiet = ::openSwitch,
                extra = { StuckHelp(verdict) },
            )
        }
    }
}

@Composable
private fun RestartStep(
    keepAliveDone: Boolean,
    onFixKeepAlive: () -> Unit,
    onReturnAfterConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    StepLayout(
        film = { RestartSim() },
        title = "Almost! Restart it once",
        line = "It's on, but your phone didn't start it.",
        action = "Open Accessibility",
        onAction = {
            onReturnAfterConnect()
            ServiceControl.openAccessibilityServiceDetails(context)
            SetupGuide.start(context, SetupGuide.Kind.Restart)
        },
        quiet = if (!keepAliveDone) "Fix battery & Autostart first" else "Skip setup",
        onQuiet = { if (!keepAliveDone) onFixKeepAlive() else onSkip() },
        extra = { StuckHelp(null) },
    )
}

/** The last resort, folded away: restart the phone, or email with the model filled in. */
@Composable
private fun StuckHelp(verdict: Verdict?) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (open) "Still stuck?" else "Still stuck? Tap here",
            style = MaterialTheme.typography.labelLarge,
            color = Smoke,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (open) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceLav)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HelpLine("1", "Restart your phone, then try again.")
                HelpLine("2", "Close Settings from Recents, then retry.")
                HelpLine("3", "Email me. Your phone model is filled in.")
                FlatButton(
                    text = "Email me",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        ServiceControl.emailDeveloper(
                            context,
                            subject = "Ek Aur setup help",
                            body = "Hi Hakkan,\n\nI'm stuck on setup.\n\n" +
                                "Phone: ${ServiceControl.deviceLine()}\n" +
                                (verdict?.let { "Gate: $it\n" } ?: "") +
                                "\nWhat I see: ",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HelpLine(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Text(n, style = MaterialTheme.typography.labelMedium, color = Chalk) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Chalk)
    }
}

@Composable
private fun DoneStep(apps: Set<TrackedApp>, onDone: () -> Unit) {
    val context = LocalContext.current
    val openable = remember(apps) {
        TrackedApp.entries.filter { it in apps && ServiceControl.isInstalled(context, it) }
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = Motion.bouncy(),
        label = "pop",
    )
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { haptics.confirm() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(110.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop }
                    .clip(CircleShape)
                    .background(brush = buttonGradient()),
                contentAlignment = Alignment.Center,
            ) { EkIcon(EkIcons.Check, tint = Ink, size = 54.dp) }
            Celebration(key = Unit, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "You're counting.",
            style = MaterialTheme.typography.headlineMedium,
            color = Chalk,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Go scroll. The counter shows up on its own.",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        openable.forEachIndexed { index, app ->
            FlatButton(
                text = "Open ${app.appName}",
                emphasised = index == 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onDone()
                    ServiceControl.openApp(context, app)
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        FlatButton(
            "Go to Home",
            emphasised = openable.isEmpty(),
            quiet = openable.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone,
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/** A done / to-do chip for the keep-alive checklist. */
@Composable
private fun Chip(label: String, done: Boolean) {
    val bg by animateColorAsState(if (done) GoodSoft else SurfaceLav, label = "chip")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(if (done) Good else Color.White)
                .border(1.5.dp, if (done) Good else Ash, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (done) EkIcon(EkIcons.Check, tint = Color.White, size = 10.dp) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Chalk)
    }
}

/** One dot per step, the current one stretched. */
@Composable
private fun ProgressDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(SetupFlow.DOTTED.size) { i ->
            val w by animateFloatAsState(
                targetValue = if (i == current) 26f else 8f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "dot",
            )
            Box(
                Modifier
                    .size(width = w.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .then(if (i <= current) Modifier.background(brush = instaGradient()) else Modifier.background(InkLine)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Step ${current + 1} of ${SetupFlow.DOTTED.size}",
            style = MaterialTheme.typography.labelMedium,
            color = Smoke,
        )
    }
}

/**
 * One app to pick. Selected cards take the app's own mark (pink for Reels, red
 * for Shorts), so the choice reads before the words do.
 */
@Composable
private fun AppChoice(
    app: TrackedApp,
    selected: Boolean,
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mark = app.mark
    val border by animateColorAsState(if (selected) mark else InkLine, Motion.quick(), label = "choice")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) app.markSoft else Color.White)
            .border(2.dp, border, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(app, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(app.items, style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(
                text = if (installed) app.appName else "Not installed",
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
            )
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) mark else Color.Transparent)
                .border(2.dp, if (selected) mark else Ash, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) EkIcon(EkIcons.Check, tint = Color.White, size = 12.dp)
        }
    }
}
