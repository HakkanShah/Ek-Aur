package com.ekaur.android.ui.feedback

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ekaur.android.diagnostics.BugReport
import com.ekaur.android.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sends a report of some [BugReport.Kind]; [busy] while the attachments are written. */
class Reporter internal constructor(
    private val start: (BugReport.Kind) -> Unit,
    private val isBusy: () -> BugReport.Kind?,
) {
    val busy: BugReport.Kind? get() = isBusy()
    fun send(kind: BugReport.Kind) = start(kind)
}

@Composable
fun rememberReporter(container: AppContainer): Reporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<BugReport.Kind?>(null) }
    return remember(container) {
        Reporter(
            start = { kind ->
                if (busy == null) {
                    busy = kind
                    scope.launch {
                        val opened = withContext(Dispatchers.IO) {
                            runCatching { BugReport.send(context.applicationContext, container, kind) }.getOrDefault(false)
                        }
                        busy = null
                        if (!opened) {
                            Toast.makeText(
                                context,
                                "No mail app found. Email ${com.ekaur.android.service.ServiceControl.DEVELOPER_EMAIL}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            isBusy = { busy },
        )
    }
}
