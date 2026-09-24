package com.ekaur.android.ui.friends

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.sync.Username
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.Spinner
import com.ekaur.android.ui.theme.Good
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the app knows about the name currently typed. */
sealed interface NameState {
    data object Idle : NameState
    data class Invalid(val problem: Username.Problem) : NameState
    data object Checking : NameState
    data object Free : NameState
    data object Taken : NameState
    data object Unreachable : NameState
    /** The name the account already has -- nothing to change. */
    data object Current : NameState
}

/** Long enough that a normal typist triggers one check, not one per letter. */
private const val DEBOUNCE_MS = 350L

/**
 * Live availability for a typed name, shared by first-run and rename.
 *
 * Three layers so it feels instant and costs almost nothing: the format rules
 * run on the device on every keystroke; a name that passes them is asked about
 * only after typing stops (a new keystroke cancels the check in flight); and
 * every answer is remembered, so retyping a name never asks twice.
 *
 * [current] is the account's existing name, reported as [NameState.Current]
 * rather than "taken".
 */
class NameCheck internal constructor() {
    var state by mutableStateOf<NameState>(NameState.Idle)
        internal set

    internal val known = mutableStateMapOf<String, Boolean>()

    /** Records a server verdict learned elsewhere (e.g. a claim that lost a race). */
    fun markTaken(name: String) {
        known[name] = false
        state = NameState.Taken
    }
}

@Composable
fun rememberNameCheck(
    container: AppContainer,
    typed: String,
    current: String? = null,
): NameCheck {
    val check = remember { NameCheck() }
    val name = Username.normalise(typed)
    val problem = Username.problemWith(typed)

    LaunchedEffect(name, current) {
        check.state = when {
            name.isEmpty() -> NameState.Idle
            problem != null -> NameState.Invalid(problem)
            current != null && name == current -> NameState.Current
            check.known.containsKey(name) ->
                if (check.known[name] == true) NameState.Free else NameState.Taken
            else -> {
                check.state = NameState.Checking
                delay(DEBOUNCE_MS)
                runCatching {
                    withContext(Dispatchers.IO) {
                        container.supabase.ensureSignedIn()
                        container.supabase.isUsernameAvailable(name)
                    }
                }.fold(
                    onSuccess = { free ->
                        check.known[name] = free
                        if (free) NameState.Free else NameState.Taken
                    },
                    onFailure = { NameState.Unreachable },
                )
            }
        }
    }
    return check
}

/** The line under a name field: an icon and a short status, animated. */
@Composable
fun NameStatusLine(state: NameState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(Motion.quick()) + slideInVertically(Motion.quick()) { it / 3 }) togetherWith
                fadeOut(Motion.quick())
        },
        contentKey = { it::class },
        label = "name-status",
        modifier = modifier,
    ) { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (s) {
                NameState.Checking -> Spinner(size = 14.dp, stroke = 1.5.dp)
                NameState.Free -> Text("✓", style = MaterialTheme.typography.labelLarge, color = Good)
                NameState.Taken, is NameState.Invalid -> Text("✕", style = MaterialTheme.typography.labelLarge, color = Heat)
                else -> {}
            }
            if (s != NameState.Idle && s != NameState.Current && s != NameState.Unreachable) {
                Spacer(Modifier.width(6.dp))
            }
            val (text, colour) = when (s) {
                NameState.Idle -> "${Username.MIN}–${Username.MAX} characters: a–z, 0–9, . and _" to Smoke
                is NameState.Invalid -> Username.message(s.problem) to Heat
                NameState.Checking -> "Checking…" to Smoke
                NameState.Free -> "Available" to Good
                NameState.Taken -> "Already taken" to Heat
                NameState.Unreachable -> "Couldn't check. Are you online?" to Smoke
                NameState.Current -> "That's your name now." to Smoke
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = colour)
        }
    }
}
