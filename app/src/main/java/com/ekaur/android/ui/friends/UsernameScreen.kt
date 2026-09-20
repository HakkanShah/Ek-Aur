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
            text = "naam chuno",
            style = MaterialTheme.typography.displayMedium,
            color = Chalk,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "leaderboard pe isi naam se dikhoge. sab ek hi list me hain.",
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
                            text = "tumhara naam",
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
            text = if (claiming) "ruko..." else "chalo shuru karein",
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
                        com.ekaur.android.data.work.SyncWorker.schedule(appContext)
                    }.onFailure { thrown ->
                        val cause = (thrown as? SyncException)?.error
                        claimError = when (cause) {
                            // The check is advisory; the unique index decides.
                            // Two people can pass the check in the same second.
                            SyncError.NameTaken -> {
                                known[name] = false
                                state = NameState.Taken
                                "abhi abhi kisi ne le liya. dusra chuno."
                            }
                            SyncError.SignupDisabled ->
                                "server pe anonymous sign-in band hai."
                            SyncError.Offline ->
                                "internet nahi mila."
                            is SyncError.Refused ->
                                "nahi hua (${cause.status}). dobara try karo."
                            null -> "nahi hua. dobara try karo."
                        }
                    }
                }
            },
        )

        if (claimError != null) {
            Spacer(Modifier.height(10.dp))
            Text(claimError!!, style = MaterialTheme.typography.bodyMedium, color = Heat)
        }

        Spacer(Modifier.height(24.dp))

        Card {
            SectionLabel("kya share hota hai")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "sirf tumhara naam aur har din ka total. kaunsi reel dekhi, " +
                    "kab scroll kiya — wo phone se bahar jaata hi nahi.\n\n" +
                    "list se hatna ho to setup me \"chhup jao\" hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        }
    }
}

@Composable
private fun StatusLine(state: NameState) {
    val (text, colour) = when (state) {
        NameState.Idle -> "${Username.MIN}-${Username.MAX} akshar, a-z 0-9 . _" to Ash
        is NameState.Invalid -> Username.message(state.problem) to Heat
        NameState.Checking -> "dekh raha hoon..." to Smoke
        NameState.Free -> "✓ mil gaya, ye free hai" to Acid
        NameState.Taken -> "ye naam le liya gaya hai" to Heat
        NameState.Unreachable -> "check nahi kar paaya, internet dekho" to Smoke
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colour)
}
