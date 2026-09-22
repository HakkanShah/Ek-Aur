package com.ekaur.android.ui.account

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.photo.AvatarPhoto
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.avatar.AvatarCropScreen
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.Expandable
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.ToggleRow
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
 * Everything about the person: their photo, name, recovery code, and whether they
 * show on the leaderboard.
 *
 * Split out of Setup and reached from the avatar in the home header, so Setup is
 * only the getting-it-working steps and the account lives on its own.
 */
@Composable
fun AccountScreen(
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }

    fun openForCrop(uri: Uri?) {
        if (uri == null) return
        avatarNote = null
        avatarOk = false
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { AvatarPhoto.decode(context, uri) } }
                .onSuccess { pendingPhoto = it }
                .onFailure { thrown ->
                    avatarNote = when ((thrown as? AvatarPhoto.PhotoException)?.failure) {
                        AvatarPhoto.Failure.CannotOpen -> "Couldn't open that file. Pick another."
                        AvatarPhoto.Failure.NotAnImage -> "Your phone couldn't read this format."
                        AvatarPhoto.Failure.TooBig -> "Photo too big, ran out of memory."
                        null -> "Couldn't open that photo. Try another."
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
                avatarNote = "Done (${bytes / 1024} KB)"
            }.onFailure { thrown ->
                avatarOk = false
                avatarNote = when (val cause = (thrown as? SyncException)?.error) {
                    SyncError.Offline -> "No internet."
                    is SyncError.Refused -> "Server refused it (${cause.status})."
                    else -> "Couldn't upload the photo."
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> openForCrop(uri) }

    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> openForCrop(uri) }

    val cropping = pendingPhoto
    if (cropping != null) {
        AvatarCropScreen(
            photo = cropping,
            busy = uploading,
            error = if (avatarOk) null else avatarNote,
            onCancel = { pendingPhoto = null; avatarNote = null },
            onConfirm = { crop -> upload(cropping, crop) },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(Unit) {
        if (recoveryCode == null) {
            recoveryCode = withContext(Dispatchers.IO) {
                runCatching { container.supabase.registerDevice(container.deviceKey) }.getOrNull()
            }
            recoveryCode?.let { container.settings.saveRecoveryCode(it) }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Account")
            FlatButton(text = "Close", onClick = onClose)
        }

        Spacer(Modifier.height(16.dp))

        // Who you are
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UserAvatar(
                    username = username.orEmpty(),
                    url = Avatar.urlFor(
                        baseUrl = container.supabase.baseUrl,
                        userId = container.settings.userId.orEmpty(),
                        version = avatarVersion,
                    ),
                    size = 68.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = username.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Chalk,
                    )
                    Text(
                        text = when {
                            uploading -> "Uploading photo…"
                            hidden -> "Hidden from the leaderboard"
                            else -> "On the leaderboard"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                    )
                }
            }
            if (avatarNote != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = avatarNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (avatarOk) Acid else Heat,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatButton(
                    text = if (avatarVersion == null) "Add photo" else "New photo",
                    emphasised = !uploading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!uploading) picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
                FlatButton(
                    text = "From files",
                    modifier = Modifier.weight(1f),
                    onClick = { if (!uploading) files.launch("image/*") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Name
        Card {
            SectionLabel("Name")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Now \"" + username.orEmpty() + "\". Change once every 14 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = newName,
                onValueChange = { newName = Username.normalise(it).take(Username.MAX) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Chalk),
                cursorBrush = SolidColor(Acid),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (newName.isEmpty()) {
                        Text("New name", style = MaterialTheme.typography.titleLarge, color = Ash)
                    }
                    inner()
                },
            )
            if (renameNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = renameNote!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (renameOk) Good else Heat,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = if (renaming) "Saving…" else "Change name",
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
                            renameNote = "Done."
                        }.onFailure { thrown ->
                            renameOk = false
                            renameNote = when (val cause = (thrown as? SyncException)?.error) {
                                is SyncError.Cooldown -> "Not yet — ${cause.daysLeft} days to go."
                                SyncError.NameTaken -> "That name is taken."
                                SyncError.Offline -> "No internet."
                                else -> "Didn't work. Try again."
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Leaderboard visibility
        Card {
            SectionLabel("Leaderboard")
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Hide me",
                subtitle = if (hidden) {
                    "You're hidden — your name and counts show on nobody's list."
                } else {
                    "You're on the list. Only your name and daily total show."
                },
                checked = hidden,
                onCheckedChange = { target ->
                    if (hideBusy) return@ToggleRow
                    hideBusy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { container.supabase.setHidden(target) }.isSuccess
                        }
                        if (ok) container.settings.setHidden(target)
                        hideBusy = false
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Recovery code
        Card {
            SectionLabel("Recovery code")
            Spacer(Modifier.height(12.dp))
            Expandable("Show my code") {
                Text(
                    text = "Reinstall on this phone and your account comes back on its own. " +
                        "On a new phone you'll need this code — write it down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = recoveryCode ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Acid,
                )
                Spacer(Modifier.height(12.dp))
                FlatButton("Share code", onClick = {
                    val code = recoveryCode ?: return@FlatButton
                    ServiceControl.shareText(
                        context,
                        "Ek Aur recovery code: " + code +
                            "\n(to get your account back on a new phone)",
                    )
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Only your name and daily total ever leave the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(24.dp))
    }
}
