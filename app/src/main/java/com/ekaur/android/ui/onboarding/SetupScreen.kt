package com.ekaur.android.ui.onboarding

import android.os.Build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.ui.graphics.SolidColor
import android.graphics.Bitmap
import android.net.Uri
import com.ekaur.android.photo.AvatarPhoto
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.Username
import com.ekaur.android.overlay.OverlayPrefs
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.avatar.AvatarCropScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Dot
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.UserAvatar
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
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
    onOpenEvents: () -> Unit = {},
    onOpenStatus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf(false) }
    val username by container.settings.username.collectAsState()
    val hidden by container.settings.hidden.collectAsState()
    var hideBusy by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var renameNote by remember { mutableStateOf<String?>(null) }
    var renameOk by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf(container.settings.recoveryCode) }
    var avatarVersion by remember { mutableStateOf(container.settings.avatarVersion) }
    var uploading by remember { mutableStateOf(false) }
    var avatarNote by remember { mutableStateOf<String?>(null) }
    var avatarOk by remember { mutableStateOf(false) }
    var paymentPaused by remember { mutableStateOf(false) }
    var usageOk by remember { mutableStateOf(ServiceControl.hasUsageAccess(context)) }
    var autoOff by remember { mutableStateOf(container.settings.autoOffOnLeave) }

    // Usage access is granted in system settings and comes back with no
    // callback, so it is re-read whenever the screen returns to the front.
    LifecycleResumeEffect(Unit) {
        usageOk = ServiceControl.hasUsageAccess(context)
        onPauseOrDispose { }
    }

    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // Decoded the moment a photo is chosen rather than at upload time, so a
    // file the phone cannot read says so immediately -- and so the user sees
    // what they are sending before anything is sent.
    fun openForCrop(uri: Uri?) {
        if (uri == null) return
        avatarNote = null
        avatarOk = false
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { AvatarPhoto.decode(context, uri) }
            }.onSuccess {
                pendingPhoto = it
            }.onFailure { thrown ->
                // Three different faults used to share one message, which said
                // nothing and cost a round trip to work out. There is no logcat
                // on this phone; the screen is the only instrument.
                avatarNote = when ((thrown as? AvatarPhoto.PhotoException)?.failure) {
                    AvatarPhoto.Failure.CannotOpen ->
                        "couldn't open that file. pick another from the gallery."
                    AvatarPhoto.Failure.NotAnImage ->
                        "your phone couldn't read this image format."
                    AvatarPhoto.Failure.TooBig ->
                        "photo too big, ran out of memory."
                    null -> "couldn't open that photo. try another."
                }
            }
        }
    }

    fun upload(photo: Bitmap, crop: Avatar.Crop) {
        uploading = true
        avatarNote = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = AvatarPhoto.encode(photo, crop)
                    container.supabase.uploadAvatar(bytes) to bytes.size
                }
            }
            uploading = false
            result.onSuccess { (version, bytes) ->
                pendingPhoto = null
                avatarVersion = version
                container.settings.saveAvatarVersion(version)
                avatarOk = true
                avatarNote = "done (${bytes / 1024} KB)"
            }.onFailure { thrown ->
                // The crop screen stays open on a failure, so pressing lagao
                // again retries without re-picking and re-framing the photo.
                avatarOk = false
                avatarNote = when (val cause = (thrown as? SyncException)?.error) {
                    SyncError.Offline -> "no internet."
                    is SyncError.Refused -> "server refused it (${cause.status})."
                    else -> "couldn't upload the photo."
                }
            }
        }
    }

    // The gallery picker: no storage permission is asked for, and the app only
    // ever receives the one image the user chose.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> openForCrop(uri) }

    // The way back for a photo the gallery does not list -- a jpg sitting in
    // Downloads, or anything a chat app saved where the media scanner never
    // looked. Same decoder, so it accepts whatever the phone can display.
    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> openForCrop(uri) }

    val cropping = pendingPhoto
    if (cropping != null) {
        AvatarCropScreen(
            photo = cropping,
            busy = uploading,
            error = if (avatarOk) null else avatarNote,
            onCancel = {
                pendingPhoto = null
                avatarNote = null
            },
            onConfirm = { crop -> upload(cropping, crop) },
            modifier = modifier,
        )
        return
    }

    // Fetched once if the device has a session but no stored code -- an account
    // made before recovery existed, or one restored onto a new phone.
    LaunchedEffect(Unit) {
        if (recoveryCode == null) {
            recoveryCode = withContext(Dispatchers.IO) {
                runCatching {
                    container.supabase.registerDevice(container.deviceKey)
                }.getOrNull()
            }
            recoveryCode?.let { container.settings.saveRecoveryCode(it) }
        }
    }
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
                    Dot(Good)
                    Text(
                        text = "all set. go scroll.",
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
            why = "counts your reels. nothing works without it.",
            done = serviceEnabled,
            actionLabel = "open accessibility",
            onAction = { ServiceControl.openAccessibilitySettings(context) },
            extra = {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "if a \"Restricted setting\" popup blocks it, that's Android being " +
                        "careful with sideloaded apps. Open app info → ⋮ (top right) → " +
                        "Allow restricted settings, then come back and turn it on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "open app info",
                    onClick = { ServiceControl.openAppInfo(context) },
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "02",
            title = "overlay",
            why = "shows the counter over Instagram.",
            done = canOverlay,
            actionLabel = "allow overlay",
            onAction = { ServiceControl.openOverlaySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "03",
            title = "battery",
            why = "realme/oppo/xiaomi kill background apps, and then counting " +
                "stops with no warning.",
            done = batteryExempt,
            actionLabel = "allow battery use",
            onAction = { ServiceControl.openBatterySettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        SetupStep(
            index = "04",
            title = "usage access",
            why = "lets the app tell when you've left Instagram, so the counter " +
                "hides and Ek Aur turns itself off before a payment. It reads no " +
                "screen, and banks don't mind it.",
            done = usageOk,
            actionLabel = "allow usage access",
            onAction = { ServiceControl.openUsageAccessSettings(context) },
        )

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Payment / UPI apps")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "a bank or UPI app may call this \"suspicious\" and block a payment. " +
                    "Don't worry — it happens to any app that isn't from the Play Store. " +
                    "Even ChatGPT reads your screen and gets a pass just for being a " +
                    "Store app. Nothing is wrong with this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "the fix: when you're not on Instagram, Ek Aur should be off. The " +
                    "switch below does that on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Turn off when I leave Instagram",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (autoOff) {
                    "on. Ek Aur turns off when you leave Instagram, so payments stay " +
                        "clean. Tap the floating button to turn it back on for scrolling " +
                        "(Android won't do that part for you)."
                } else {
                    "off. you'll have to turn it off yourself before each payment."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(10.dp))
            FlatButton(
                text = if (autoOff) "Auto-off: ON" else "Auto-off: OFF",
                emphasised = autoOff,
                onClick = {
                    autoOff = !autoOff
                    container.settings.autoOffOnLeave = autoOff
                },
            )
            if (autoOff && !usageOk) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "this needs \"usage access\" above, or the app can't tell " +
                        "you've left Instagram.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Heat,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "1. Floating button (fastest)",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "tap below to open Ek Aur's page → turn on \"shortcut\" or " +
                    "\"accessibility button\". A small button then floats on screen — " +
                    "tap it to turn Ek Aur on/off, even over a payment app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(10.dp))
            FlatButton(
                text = "set up shortcut",
                emphasised = true,
                onClick = { ServiceControl.openAccessibilityServiceDetails(context) },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "2. Quick Settings tile",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "add the \"Ek Aur\" tile to your notification shade, then one tap " +
                    "off, one tap on.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "add tile",
                    emphasised = false,
                    onClick = { ServiceControl.requestAddPauseTile(context) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "3. Turn off here",
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            if (paymentPaused) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "turned off. to turn it back on after paying, open accessibility " +
                        "settings (or tap the floating button).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Acid,
                )
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "turn back on",
                    emphasised = false,
                    onClick = { ServiceControl.openAccessibilitySettings(context) },
                )
            } else {
                Spacer(Modifier.height(10.dp))
                FlatButton(
                    text = "turn off for a payment",
                    emphasised = false,
                    onClick = {
                        // If the service is not actually running, treat it as
                        // already off rather than claiming a pause that did
                        // nothing -- either way, nothing is left for a UPI app
                        // to warn about.
                        ServiceControl.pauseForPayment()
                        paymentPaused = true
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Counter missing?")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "if you dragged the counter right to the edge and it vanished, " +
                    "bring it back to the middle here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (reset) "done \u2713" else "reset counter",
                onClick = {
                    OverlayPrefs(context).clearPosition()
                    reset = true
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Photo")
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UserAvatar(
                    username = username.orEmpty(),
                    url = Avatar.urlFor(
                        baseUrl = container.supabase.baseUrl,
                        userId = container.settings.userId.orEmpty(),
                        version = avatarVersion,
                    ),
                    size = 64.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (uploading) {
                            "uploading..."
                        } else {
                            "shows next to your name on the leaderboard. Sent small, not " +
                                "the full photo."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                    if (avatarNote != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = avatarNote!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (avatarOk) Acid else Heat,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (avatarVersion == null) "choose photo" else "change photo",
                emphasised = !uploading,
                onClick = {
                    if (!uploading) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            FlatButton(
                text = "pick from files",
                emphasised = false,
                onClick = { if (!uploading) files.launch("image/*") },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Change name")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "now: " + username.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "you can change your name once every 14 days. The old one is " +
                    "freed right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = newName,
                onValueChange = { newName = Username.normalise(it).take(Username.MAX) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Chalk),
                cursorBrush = SolidColor(Acid),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (newName.isEmpty()) {
                        Text("new name", style = MaterialTheme.typography.bodyLarge, color = Ash)
                    }
                    inner()
                },
            )
            if (renameNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = renameNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (renameOk) Acid else Heat,
                )
            }
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = if (renaming) "saving..." else "change name",
                emphasised = Username.isValid(newName) && !renaming,
                onClick = {
                    if (!Username.isValid(newName) || renaming) return@FlatButton
                    renaming = true
                    renameNote = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.changeUsername(newName) }
                        }
                        renaming = false
                        result.onSuccess { applied ->
                            container.settings.saveUsername(applied)
                            newName = ""
                            renameOk = true
                            renameNote = "done"
                        }.onFailure { thrown ->
                            renameOk = false
                            renameNote = when (val cause = (thrown as? SyncException)?.error) {
                                // Enforced by the server, so a reinstall does
                                // not reset it.
                                is SyncError.Cooldown ->
                                    "not yet \u2014 ${cause.daysLeft} days to go."
                                SyncError.NameTaken -> "that name is taken."
                                SyncError.Offline -> "no internet."
                                else -> "didn't work. try again."
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Recovery code")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "reinstall on this phone and your account comes back on its own. " +
                    "On a new phone you'll need this code \u2014 write it down.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = recoveryCode ?: "\u2014",
                style = MaterialTheme.typography.headlineMedium,
                color = Acid,
            )
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = "share code",
                onClick = {
                    val code = recoveryCode ?: return@FlatButton
                    ServiceControl.shareText(
                        context,
                        "Ek Aur recovery code: " + code + "\n(to get your account back on a new phone)",
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Leaderboard")
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hidden) {
                    "you're hidden right now. Your name and counts don't show on " +
                        "anyone's list."
                } else {
                    "you're on the list as \"" + username.orEmpty() + "\". Only your " +
                        "name and daily total show."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(14.dp))
            FlatButton(
                text = when {
                    hideBusy -> "saving..."
                    hidden -> "show me again"
                    else -> "hide me"
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
            SectionLabel("Installing")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "\u2022 If Play Protect says \"app blocked\": Play Store \u2192 profile " +
                    "\u2192 Play Protect \u2192 \u2699 \u2192 turn off scanning, install, then " +
                    "turn it back on. It always flags a sideloaded app that uses " +
                    "accessibility \u2014 nothing is wrong with the app.\n\n" +
                    "\u2022 \"App not installed\" means installing an older APK over a " +
                    "newer one. Android won't downgrade \u2014 install the newest file.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "only your name and daily total ever leave the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Developer")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "raw event log and live status, for when counting misbehaves.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatButton(text = "events", onClick = onOpenEvents)
                FlatButton(text = "status", onClick = onOpenStatus)
            }
        }

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
            Dot(if (done) Good else Ash)
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
