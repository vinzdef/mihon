package eu.kanade.tachiyomi.ui.reader.panel

/**
 * Panel bounds in page pixels; [right] and [bottom] are exclusive.
 */
data class Panel(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {

    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}
