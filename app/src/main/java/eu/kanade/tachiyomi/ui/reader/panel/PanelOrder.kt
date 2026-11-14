package eu.kanade.tachiyomi.ui.reader.panel

/**
 * Sorts unordered, slightly overlapping panels into rows, then columns within a row, recursively.
 */
object PanelOrder {

    // Detected bounds aren't exact, so panels are shrunk before looking for gaps
    private const val SHRINK_FACTOR = 0.15f

    fun sort(panels: List<Panel>, isRtl: Boolean): List<Panel> {
        val result = ArrayList<Panel>(panels.size)
        sortTo(result, panels, isRtl)
        return result
    }

    private fun sortTo(output: MutableList<Panel>, panels: List<Panel>, isRtl: Boolean) {
        if (panels.size <= 1) {
            output.addAll(panels)
            return
        }
        val rows = group(panels, isVertical = true)
        if (rows.size > 1) {
            rows.forEach { sortTo(output, it, isRtl) }
            return
        }
        val columns = group(panels, isVertical = false)
        if (columns.size > 1) {
            (if (isRtl) columns.asReversed() else columns).forEach { sortTo(output, it, isRtl) }
            return
        }
        // An inset over a larger panel
        output.addAll(
            panels.sortedWith(
                compareBy<Panel> { it.top }.thenBy { if (isRtl) -it.right else it.left },
            ),
        )
    }

    private fun group(panels: List<Panel>, isVertical: Boolean): List<List<Panel>> {
        val sorted = panels.sortedBy { it.start(isVertical) }
        val result = ArrayList<MutableList<Panel>>()
        var groupEnd = Float.NEGATIVE_INFINITY
        for (panel in sorted) {
            val inset = panel.size(isVertical) * SHRINK_FACTOR
            val start = panel.start(isVertical) + inset
            val end = panel.start(isVertical) + panel.size(isVertical) - inset
            if (result.isEmpty() || start >= groupEnd) {
                result.add(ArrayList())
                groupEnd = end
            } else {
                groupEnd = maxOf(groupEnd, end)
            }
            result.last().add(panel)
        }
        return result
    }

    private fun Panel.start(isVertical: Boolean) = if (isVertical) top else left

    private fun Panel.size(isVertical: Boolean) = if (isVertical) height else width
}
