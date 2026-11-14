package eu.kanade.tachiyomi.ui.reader.panel

import kotlin.math.ceil

/**
 * Viewport position in page coordinates.
 */
data class PanelStop(
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
)

/**
 * With steps enabled, a panel that wouldn't get noticeably larger than on the whole page is zoomed further and read
 * in steps.
 */
object PanelStops {

    private const val FILL_FACTOR = 0.96f
    private const val MIN_ZOOM = 1.5f
    private const val STEP_FACTOR = 0.85f
    private const val MIN_PAN_GAIN = 1.5f

    // Two steps at most, so a wide panel doesn't turn into a series of taps
    private const val MAX_PAN_ZOOM = 1f + STEP_FACTOR

    fun compute(
        panels: List<Panel>,
        viewWidth: Int,
        viewHeight: Int,
        pageWidth: Int,
        pageHeight: Int,
        isRtl: Boolean,
        isStepsEnabled: Boolean,
    ): List<PanelStop> {
        if (viewWidth <= 0 || viewHeight <= 0 || pageWidth <= 0 || pageHeight <= 0) {
            return emptyList()
        }
        val pageScale = minOf(viewWidth / pageWidth.toFloat(), viewHeight / pageHeight.toFloat())
        val result = ArrayList<PanelStop>(panels.size)
        for (panel in panels) {
            val scaleX = FILL_FACTOR * viewWidth / panel.width
            val scaleY = FILL_FACTOR * viewHeight / panel.height
            val fitScale = minOf(scaleX, scaleY)
            val centerX = (panel.left + panel.right) / 2f
            val centerY = (panel.top + panel.bottom) / 2f
            val panScale = minOf(maxOf(scaleX, scaleY), fitScale * MAX_PAN_ZOOM)
            if (!isStepsEnabled || fitScale >= pageScale * MIN_ZOOM || panScale < fitScale * MIN_PAN_GAIN) {
                result.add(PanelStop(fitScale, centerX, centerY))
                continue
            }
            if (scaleX < scaleY) {
                val visible = viewWidth / panScale
                val first = panel.left + visible / 2f
                val last = panel.right - visible / 2f
                val count = stepsCount(panel.width.toFloat(), visible)
                for (i in 0 until count) {
                    val step = if (isRtl) count - i - 1 else i
                    result.add(PanelStop(panScale, first + (last - first) * step / (count - 1), centerY))
                }
            } else {
                val visible = viewHeight / panScale
                val first = panel.top + visible / 2f
                val last = panel.bottom - visible / 2f
                val count = stepsCount(panel.height.toFloat(), visible)
                for (i in 0 until count) {
                    result.add(PanelStop(panScale, centerX, first + (last - first) * i / (count - 1)))
                }
            }
        }
        return result
    }

    private fun stepsCount(size: Float, visible: Float): Int {
        return (ceil((size - visible) / (visible * STEP_FACTOR)).toInt() + 1).coerceAtLeast(2)
    }
}
