package com.ekaur.android.diagnostics

import androidx.core.content.FileProvider

/**
 * Serves the diagnostic exports and downloaded updates (`@xml/file_paths`).
 *
 * It has to be its own class. Android keeps one running instance per provider
 * class in a process, so when this and the share-card provider were both
 * declared as plain `FileProvider`, every request for an export went to the
 * card provider, which only knows the `cards/` folder. Reading an export then
 * threw "Failed to find configured root": the app crashed on Share events,
 * Share diagnostics and Report a bug, and WhatsApp could never read the file.
 */
class ExportsProvider : FileProvider()
