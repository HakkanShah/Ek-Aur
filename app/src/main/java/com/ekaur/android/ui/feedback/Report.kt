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
import com.ekaur.android.service.ServiceControl
import kotlinx.coroutines.launch

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
                        val outcome = runCatching {
                            BugReport.send(context.applicationContext, container, kind)
                        }.getOrElse { ServiceControl.MailOutcome.Failed }
                        busy = null
                        when (outcome) {
                            ServiceControl.MailOutcome.Mail -> Unit
                            ServiceControl.MailOutcome.ShareSheet -> Toast.makeText(
                                context,
                                "No mail app set up. Pick any app to send it, or email ${ServiceControl.DEVELOPER_EMAIL}",
                                Toast.LENGTH_LONG,
                            ).show()
                            ServiceControl.MailOutcome.Failed -> Toast.makeText(
                                context,
                                "Couldn't open mail. Email ${ServiceControl.DEVELOPER_EMAIL}",
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
