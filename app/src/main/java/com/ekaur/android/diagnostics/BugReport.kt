package com.ekaur.android.diagnostics

import android.content.Context
import android.os.Build
import com.ekaur.android.BuildConfig
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.onboarding.PermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Bug reports, feature ideas and feedback, sent from inside the app to the
 * developer's inbox.
 *
 * A bug report brings its own evidence: the event log (with the detector's
 * reason for every Shorts swipe), a snapshot of everything the app can read
 * about its own state, and the last crash if there was one. Before this, the
 * only way to get a dump was the share sheet, and a friend whose counter had
 * stopped couldn't get one across at all.
 */
object BugReport {

    enum class Kind(val subject: String, val prompt: String, val attachLogs: Boolean) {
        Bug(
            subject = "Ek Aur bug",
            prompt = "What happened?\n\n\nWhat did you expect instead?\n\n\n",
            attachLogs = true,
        ),
        Feature(
            subject = "Ek Aur feature idea",
            prompt = "What should Ek Aur do?\n\n\nWhy would it help?\n\n\n",
            attachLogs = false,
        ),
        Feedback(
            subject = "Ek Aur feedback",
            prompt = "",
            attachLogs = false,
        ),
    }

    /**
     * Writes the attachments (for a bug) off the main thread, then opens the
     * mail app on it.
     */
    suspend fun send(context: Context, container: AppContainer, kind: Kind): ServiceControl.MailOutcome {
        val files = if (kind.attachLogs) withContext(Dispatchers.IO) { attachments(context, container) } else emptyList()
        val subject = "${kind.subject} · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}"
        val body = buildString {
            append(kind.prompt)
            append("—\n")
            append(ServiceControl.deviceLine()).append('\n')
            container.settings.username.value?.let { append("Username: ").append(it).append('\n') }
            if (files.isNotEmpty()) {
                append("Attached: ").append(files.joinToString { it.name }).append('\n')
            }
        }
        return withContext(Dispatchers.Main) {
            ServiceControl.emailWithAttachments(context, subject, body, files)
        }
    }

    /**
     * The files a bug report carries. Each is written on its own: one that
     * can't be put together never stops the report, it just says why inside.
     */
    suspend fun attachments(context: Context, container: AppContainer): List<File> = buildList {
        fun attempt(name: String, content: () -> String) {
            val text = runCatching(content).getOrElse { "Couldn't collect this: ${it::class.simpleName}: ${it.message}" }
            runCatching { TextExport.write(context, name, text) }.getOrNull()?.let { add(it) }
        }
        attempt("ekaur-events.txt") { container.eventLog.exportText() }
        val status = runCatching { statusText(context, container) }
            .getOrElse { "Couldn't collect the status: ${it::class.simpleName}: ${it.message}" }
        attempt("ekaur-status.txt") { status }
        container.crashReporter.pendingReport()?.let { crash -> attempt("ekaur-crash.txt") { crash } }
    }

    /** Everything the app can read about itself, one fact per line. */
    suspend fun statusText(context: Context, container: AppContainer): String {
        val status = container.serviceStatus
        val settings = container.settings
        val permissions = PermissionState.read(context)
        val byApp = runCatching { container.counterRepository.observeTodayByApp().first() }.getOrDefault(emptyMap())
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fun time(ms: Long) = if (ms <= 0) "never" else fmt.format(Date(ms))

        return buildString {
            fun line(k: String, v: Any?) = append(k).append(": ").append(v ?: "-").append('\n')
            append("EK AUR status · ").append(time(System.currentTimeMillis())).append("\n\n")

            line("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            line("device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
            line("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val metrics = context.resources.displayMetrics
            line("screen", "${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi")
            line("restricted", ServiceControl.restrictedVerdict(context))
            append('\n')

            line("accessibility on", permissions.service)
            line("service running", status.connected.value)
            line("overlay", permissions.overlay)
            line("battery exempt", permissions.battery)
            line("usage access", permissions.usage)
            line("auto-off", settings.autoOffOnLeave)
            line("last auto-off", settings.lastAutoOff?.let {
                val (at, pkg) = it.split('|', limit = 2).let { p -> p[0] to p.getOrNull(1) }
                "${time(at.toLongOrNull() ?: 0)} (in front: $pkg)"
            } ?: "never")
            append('\n')

            TrackedApp.entries.forEach { app ->
                line(
                    app.items.lowercase(),
                    (if (ServiceControl.isInstalled(context, app)) "installed" else "not visible") +
                        " · " + (if (app in settings.countedApps.value) "counted" else "off") +
                        " · today ${byApp[app] ?: 0}",
                )
            }
            line("look", settings.lookOverride.value ?: "auto")
            settings.reminderSettings.let { r ->
                line("reminder", if (r.enabled) "at ${r.at}, +${r.snooze} later" else "off")
                line("reminder today", settings.reminderDay?.let { "${it.date} next=${it.nextAt ?: "not today"}" })
            }
            line("shorts page size", status.pageSizes.value.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }.ifEmpty { "not learned" })
            append('\n')

            line("detector state", status.detectorState.value)
            line("events seen", status.eventsSeen.value)
            line("last event", "${time(status.lastEventAtMs.value)} from ${status.lastEventPackage.value}")
            line("foreground (usage)", status.lastForeground.value)
            line("db write failures", container.eventLog.writeFailures.value)
            container.eventLog.lastWriteError.value?.let { line("last write error", it) }
            line("crash on file", container.crashReporter.pendingReport() != null)
            line("signed in", settings.userId != null)
        }
    }
}
