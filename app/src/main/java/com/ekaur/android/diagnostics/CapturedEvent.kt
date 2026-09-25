package com.ekaur.android.diagnostics

/**
 * A raw accessibility event, recorded verbatim before any interpretation.
 *
 * This exists so real Instagram identifiers can be read off a device dump and
 * written into `DetectorRules`. Every field the detector might plausibly key on
 * is captured, even the ones currently unused -- the whole point is to decide
 * from real data rather than guesses.
 */
data class CapturedEvent(
    val timestampMs: Long,
    val packageName: String,
    val eventType: String,
    val className: String?,
    val viewId: String?,
    val contentDescription: String?,
    val scrollDeltaY: Int,
    val scrollY: Int,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    /** Sideways movement; defaulted so older call sites still build. */
    val scrollDeltaX: Int = 0,
    /** Whether the detector turned this event into a count. */
    val counted: Boolean = false,
    /** Detector state immediately after this event. */
    val state: String = "",
) {
    fun toLine(): String = if (eventType == VERDICT) {
        "${timestampMs % 1_000_000}  ${contentDescription.orEmpty()}\n"
    } else buildString {
        append(timestampMs % 1_000_000)          // relative-ish, keeps lines short
        append("  ").append(eventType.padEnd(22))
        append(" state=").append(state.padEnd(8))
        if (counted) append(" [COUNT]")
        append('\n')
        append("    pkg=").append(packageName).append('\n')
        className?.let { append("    class=").append(it).append('\n') }
        viewId?.let { append("    viewId=").append(it).append('\n') }
        contentDescription?.let { append("    desc=").append(it).append('\n') }
        if (scrollDeltaY != 0 || scrollY != 0 || scrollDeltaX != 0) {
            append("    scrollDeltaY=").append(scrollDeltaY)
            append(" scrollY=").append(scrollY)
            if (scrollDeltaX != 0) append(" scrollDeltaX=").append(scrollDeltaX)
            append('\n')
        }
        if (fromIndex >= 0 || toIndex >= 0 || itemCount >= 0) {
            append("    fromIndex=").append(fromIndex)
            append(" toIndex=").append(toIndex)
            append(" itemCount=").append(itemCount).append('\n')
        }
    }

    companion object {
        /** A detector verdict rather than an accessibility event. */
        const val VERDICT = "Verdict"
    }
}
