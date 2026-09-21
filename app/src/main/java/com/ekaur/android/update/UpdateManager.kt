package com.ekaur.android.update

import android.content.Context
import com.ekaur.android.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the update flow is right now, for the UI to render off. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState

    /** A newer release exists but is not downloaded yet (auto-download is off). */
    data class Available(val release: Release) : UpdateState

    data class Downloading(val release: Release, val progress: Float) : UpdateState

    /** An APK for a newer version is on disk and ready to install. */
    data class Ready(val release: Release, val apk: File) : UpdateState

    data class Failed(val reason: String) : UpdateState
}

/**
 * Checks GitHub for a newer build, downloads it in the background, and drives the
 * install prompt.
 *
 * The honest ceiling for a sideloaded app: it can check and download on its own,
 * but the actual install is one tap the user has to make (Android forbids a
 * non-privileged app from replacing itself silently). So "background update"
 * means the APK is fetched ahead of time and offered for one-tap install, now and
 * again on the next launch until it's taken.
 */
class UpdateManager(
    private val appContext: Context,
    private val client: UpdateClient = UpdateClient(),
    private val prefs: UpdatePrefs = UpdatePrefs(appContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val currentCode = BuildConfig.VERSION_CODE
    private val currentName = BuildConfig.VERSION_NAME
    private val updatesDir = File(appContext.cacheDir, "updates")

    val autoDownload: Boolean get() = prefs.autoDownload
    val currentVersionLabel: String get() = "$currentName ($currentCode)"

    fun setAutoDownload(enabled: Boolean) {
        prefs.autoDownload = enabled
    }

    private fun apkFor(code: Int) = File(updatesDir, "ekaur-build$code.apk")

    /**
     * Runs on app open. If an update was already downloaded it offers it at once
     * (even offline); then, unless a recent check already ran, it looks for a
     * newer one. [force] (the manual button) ignores the throttle.
     */
    fun checkOnLaunch(force: Boolean = false) {
        // A build downloaded on a previous run, still newer than what's installed:
        // offer it immediately so it "reflects on next launch".
        val readyCode = prefs.downloadedVersionCode
        val readyApk = apkFor(readyCode)
        if (readyCode > currentCode && readyApk.exists() && !UpdateClient.looksLikeApk(readyApk)) {
            // A leftover half-written file from an older, unverified download.
            // Drop it and forget it so a fresh, verified copy replaces it.
            readyApk.delete()
            prefs.downloadedVersionCode = 0
            prefs.downloadedVersionName = null
        }
        if (readyCode > currentCode && readyApk.exists() && UpdateClient.looksLikeApk(readyApk)) {
            _state.value = UpdateState.Ready(
                Release(
                    versionName = prefs.downloadedVersionName ?: "",
                    versionCode = readyCode,
                    apkUrl = null,
                    notes = null,
                    pageUrl = "https://github.com/${BuildConfig.UPDATE_REPO}/releases",
                ),
                readyApk,
            )
        }

        // Checked recently and not a manual check: leave the state as is (a ready
        // update stays offered, otherwise nothing new to say).
        val fresh = System.currentTimeMillis() - prefs.lastCheckMs < THROTTLE_MS
        if (fresh && !force) return

        scope.launch {
            if (_state.value !is UpdateState.Ready) _state.value = UpdateState.Checking
            val release = withContext(Dispatchers.IO) { client.latest() }
            prefs.lastCheckMs = System.currentTimeMillis()

            if (release == null || !UpdateResolver.isNewer(currentCode, currentName, release)) {
                // Nothing newer. Keep a ready state if we somehow have one, else
                // report up to date and clear any stale download.
                if (_state.value !is UpdateState.Ready) {
                    _state.value = UpdateState.UpToDate
                    clearStaleDownloads(keepCode = -1)
                }
                return@launch
            }

            val code = release.versionCode
            val downloaded = code != null && code == prefs.downloadedVersionCode &&
                apkFor(code).exists() && UpdateClient.looksLikeApk(apkFor(code))
            when {
                downloaded && code != null -> _state.value = UpdateState.Ready(release, apkFor(code))
                prefs.autoDownload && release.apkUrl != null -> startDownload(release)
                else -> _state.value = UpdateState.Available(release)
            }
        }
    }

    /** Downloads [release]'s APK, then moves to [UpdateState.Ready]. */
    fun startDownload(release: Release) {
        val url = release.apkUrl ?: return
        val code = release.versionCode ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val dest = apkFor(code)
            clearStaleDownloads(keepCode = code)
            val file = withContext(Dispatchers.IO) {
                client.download(url, dest, release.apkSize) { pct ->
                    _state.value = UpdateState.Downloading(release, pct)
                }
            }
            if (file != null) {
                prefs.downloadedVersionCode = code
                prefs.downloadedVersionName = release.versionName
                _state.value = UpdateState.Ready(release, file)
            } else {
                _state.value = UpdateState.Failed("Download failed. Check your connection.")
            }
        }
    }

    /** From a [UpdateState.Ready], launches the one-tap system installer. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (UpdateInstaller.canInstall(appContext)) {
            UpdateInstaller.install(appContext, ready.apk)
        } else {
            UpdateInstaller.promptUnknownSources(appContext)
        }
    }

    /** The release page, for the manual "open on GitHub" escape hatch. */
    fun pageUrl(): String = when (val s = _state.value) {
        is UpdateState.Available -> s.release.pageUrl
        is UpdateState.Ready -> s.release.pageUrl
        else -> "https://github.com/${BuildConfig.UPDATE_REPO}/releases"
    }

    /** Hides the popup for this launch; it returns next launch if still pending. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** Deletes downloaded APKs except the one we still want, so the cache is bounded. */
    private fun clearStaleDownloads(keepCode: Int) {
        runCatching {
            updatesDir.listFiles()?.forEach { file ->
                val keep = file.name == "ekaur-build$keepCode.apk"
                if (!keep) file.delete()
            }
        }
    }

    private companion object {
        const val THROTTLE_MS = 6 * 60 * 60 * 1000L
    }
}
