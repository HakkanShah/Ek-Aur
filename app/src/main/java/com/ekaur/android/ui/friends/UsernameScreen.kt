package com.ekaur.android.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the field knows about the name currently typed in it. */
private sealed interface NameState {
    data object Idle : NameState
    data class Invalid(val problem: Username.Problem) : NameState
    data object Checking : NameState
    data object Free : NameState
    data object Taken : NameState
    data object Unreachable : NameState
}

/** Long enough that a normal typist triggers one check, not one per letter. */
private const val DEBOUNCE_MS = 350L

/**
 * The first thing anyone sees, and the only gate in the app.
 *
 * Everyone who uses Ek Aur is on one leaderboard, so there is nothing to join
 * and no code to swap -- a name is the entire sign-up.
 *
 * Availability is checked in three layers so it feels instant and costs almost
 * nothing: the format rules run on the device on every keystroke, a name that
 * passes them is asked about only after typing stops, and every answer is
 * remembered so retyping a name never asks twice.
 */
@Composable
fun UsernameScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    var typed by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<NameState>(NameState.Idle) }
    var claiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }

    // Answers already paid for. Deleting a character and retyping it is free.
    val known = remember { mutableStateMapOf<String, Boolean>() }

    var restoring by remember { mutableStateOf(true) }
    var showCodeEntry by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    // Before asking for a name, see whether this phone has been here before.
    // An uninstall wipes the session but not the device key, so a reinstall
    // finds its own account and the user never sees this screen at all.
    LaunchedEffect(Unit) {
        val recovered = withContext(Dispatchers.IO) {
            runCatching {
                container.supabase.ensureSignedIn()
                container.supabase.recoverAccount(container.deviceKey, null)
            }.getOrNull()
        }
        if (recovered != null) {
            container.settings.saveUsername(recovered)
            withContext(Dispatchers.IO) {
                runCatching { container.supabase.registerDevice(container.deviceKey) }
                    .getOrNull()
                    ?.let { container.settings.saveRecoveryCode(it) }
            }
            com.ekaur.android.data.work.SyncWorker.schedule(appContext)
        }
        restoring = false
    }

    if (restoring) {
        Column(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("EK AUR", style = MaterialTheme.typography.labelLarge, color = Acid)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Checking if you've been here before...",
                style = MaterialTheme.typography.bodyLarge,
                color = Smoke,
            )
        }
        return
    }

    val name = Username.normalise(typed)
    val problem = Username.problemWith(typed)

    // Keyed on the name: a new keystroke cancels the check in flight, which is
    // the debounce and the cancellation in one.
    LaunchedEffect(name) {
        claimError = null
        when {
            name.isEmpty() -> state = NameState.Idle
            problem != null -> state = NameState.Invalid(problem)
            known.containsKey(name) ->
                state = if (known[name] == true) NameState.Free else NameState.Taken
            else -> {
                state = NameState.Checking
                delay(DEBOUNCE_MS)
                state = runCatching {
                    withContext(Dispatchers.IO) {
                        container.supabase.ensureSignedIn()
                        container.supabase.isUsernameAvailable(name)
                    }
                }.fold(
                    onSuccess = { free ->
                        known[name] = free
                        if (free) NameState.Free else NameState.Taken
                    },
                    onFailure = { NameState.Unreachable },
                )
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("EK AUR", style = MaterialTheme.typography.labelLarge, color = Acid)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Pick a name",
            style = MaterialTheme.typography.displayMedium,
            color = Chalk,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This is your name on the leaderboard. everyone's on one list.",
            style = MaterialTheme.typography.bodyLarge,
            color = Smoke,
        )

        Spacer(Modifier.height(28.dp))

        Card {
            BasicTextField(
                value = typed,
                // Folded as they type: the database rejects capitals outright,
                // so accepting one here would only be a lie to undo later.
                onValueChange = { typed = Username.normalise(it).take(Username.MAX) },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Chalk,
                ),
                cursorBrush = SolidColor(Acid),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (typed.isEmpty()) {
                        Text(
                            text = "Your name",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                color = Ash,
                            ),
                        )
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(10.dp))
            StatusLine(state)
        }

        Spacer(Modifier.height(16.dp))

        FlatButton(
            text = if (claiming) "wait..." else "let's go",
            emphasised = state is NameState.Free && !claiming,
            onClick = {
                if (state !is NameState.Free || claiming) return@FlatButton
                claiming = true
                claimError = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching {
                            container.supabase.ensureSignedIn()
                            container.supabase.claimUsername(name)
                        }
                    }
                    claiming = false
                    outcome.onSuccess {
                        container.settings.saveUsername(name)
                        // Registered straight away, so the very first uninstall
                        // is already recoverable.
                        withContext(Dispatchers.IO) {
                            runCatching { container.supabase.registerDevice(container.deviceKey) }
                                .getOrNull()
                                ?.let { container.settings.saveRecoveryCode(it) }
                        }
                        com.ekaur.android.data.work.SyncWorker.schedule(appContext)
                    }.onFailure { thrown ->
                        val cause = (thrown as? SyncException)?.error
                        claimError = when (cause) {
                            // The check is advisory; the unique index decides.
                            // Two people can pass the check in the same second.
                            SyncError.NameTaken -> {
                                known[name] = false
                                state = NameState.Taken
                                "someone just took it. pick another."
                            }
                            // Handled inside the client by starting a fresh
                            // account; if it still reaches here, both attempts
                            // failed and the network is the likelier cause.
                            // Cannot happen here -- nothing has been changed
                            // yet -- but the compiler is right to insist.
                            is SyncError.Cooldown ->
                                "wait ${cause.daysLeft} days."
                            SyncError.StaleSession ->
                                "couldn't find your old account. try again."
                            SyncError.SignupDisabled ->
                                "anonymous sign-in is off on the server."
                            SyncError.Offline ->
                                "no internet."
                            is SyncError.Refused ->
                                "didn't work (${cause.status}). try again."
                            null -> "Didn't work. try again."
                        }
                    }
                }
            },
        )

        if (claimError != null) {
            Spacer(Modifier.height(10.dp))
            Text(claimError!!, style = MaterialTheme.typography.bodyMedium, color = Heat)
        }

        Spacer(Modifier.height(16.dp))

        if (showCodeEntry) {
            Card {
                SectionLabel("Recover old account")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "On a new phone? enter the recovery code from your old " +
                        "phone's Setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Chalk,
                    ),
                    cursorBrush = SolidColor(Acid),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (code.isEmpty()) {
                            Text(
                                "XXXXXXXX",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 20.sp,
                                    color = Ash,
                                ),
                            )
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(14.dp))
                FlatButton(
                    text = "Recover",
                    emphasised = code.length == 8,
                    onClick = {
                        if (code.length != 8) return@FlatButton
                        scope.launch {
                            val recovered = withContext(Dispatchers.IO) {
                                runCatching {
                                    container.supabase.ensureSignedIn()
                                    container.supabase.recoverAccount(null, code)
                                }.getOrNull()
                            }
                            if (recovered == null) {
                                claimError = "that code didn't work."
                            } else {
                                container.settings.saveUsername(recovered)
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        container.supabase.registerDevice(container.deviceKey)
                                    }.getOrNull()?.let { container.settings.saveRecoveryCode(it) }
                                }
                                com.ekaur.android.data.work.SyncWorker.schedule(appContext)
                            }
                        }
                    },
                )
            }
        } else {
            FlatButton(
                text = "Have an old account? enter a code",
                onClick = { showCodeEntry = true },
            )
        }

        Spacer(Modifier.height(24.dp))

        Card {
            SectionLabel("What gets shared")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Just your name and daily total. which reels you watched and " +
                    "when never leave the phone.\n\n" +
                    "to get off the list, there's \"hide me\" in Setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }
    }
}

@Composable
private fun StatusLine(state: NameState) {
    val (text, colour) = when (state) {
        NameState.Idle -> "${Username.MIN}-${Username.MAX} chars, a-z 0-9 . _" to Ash
        is NameState.Invalid -> Username.message(state.problem) to Heat
        NameState.Checking -> "Checking..." to Smoke
        NameState.Free -> "✓ available" to Good
        NameState.Taken -> "That name is taken" to Heat
        NameState.Unreachable -> "Couldn't check, check your internet" to Smoke
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colour)
}
