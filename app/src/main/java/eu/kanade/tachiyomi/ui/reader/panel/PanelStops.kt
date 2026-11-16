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
 * in steps: along its balloons after showing it whole, or else along its longer side.
 */
object PanelStops {

    private const val FILL_FACTOR = 0.96f
    private const val MIN_ZOOM = 1.5f
    private const val STEP_FACTOR = 0.85f
    private const val MIN_PAN_GAIN = 1.5f
    private const val BALLOON_ZOOM = 1.8f

    // Two steps at most, so a wide panel doesn't turn into a series of taps
    private const val MAX_PAN_ZOOM = 1f + STEP_FACTOR

    fun compute(
        panels: List<Panel>,
        balloons: List<Panel>,
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
        // A page without panels is read along its balloons like a single panel
        val pagePanels = if (panels.isEmpty() && balloons.isNotEmpty()) {
            listOf(Panel(0, 0, pageWidth, pageHeight))
        } else {
            panels
        }
        // The smallest one is an inset over a larger panel
        val panelBalloons = balloons.groupBy { balloon ->
            pagePanels.filter { balloon.isCenterIn(it) }.minByOrNull { it.width.toLong() * it.height }
        }
        val result = ArrayList<PanelStop>(pagePanels.size)
        for (panel in pagePanels) {
            val scaleX = FILL_FACTOR * viewWidth / panel.width
            val scaleY = FILL_FACTOR * viewHeight / panel.height
            val fitScale = minOf(scaleX, scaleY)
            val centerX = (panel.left + panel.right) / 2f
            val centerY = (panel.top + panel.bottom) / 2f
            val fitStop = PanelStop(fitScale, centerX, centerY)
            if (!isStepsEnabled || fitScale >= pageScale * MIN_ZOOM) {
                result.add(fitStop)
                continue
            }
            val ownBalloons = panelBalloons[panel]
            if (ownBalloons != null) {
                result.add(fitStop)
                addBalloonStops(result, panel, PanelOrder.sort(ownBalloons, isRtl), fitScale, viewWidth, viewHeight)
                continue
            }
            val panScale = minOf(maxOf(scaleX, scaleY), fitScale * MAX_PAN_ZOOM)
            if (panScale < fitScale * MIN_PAN_GAIN) {
                result.add(fitStop)
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

    /**
     * Whether the panel is read in steps on some screen.
     */
    fun isLarge(panel: Panel, pageWidth: Int, pageHeight: Int): Boolean {
        // The zoom on a screen shaped like the page, which is the lowest one a screen gets
        val zoom = minOf(pageWidth / panel.width.toFloat(), pageHeight / panel.height.toFloat())
        return FILL_FACTOR * zoom < MIN_ZOOM
    }

    /**
     * Consecutive balloons that are visible together share a stop.
     */
    private fun addBalloonStops(
        output: MutableList<PanelStop>,
        panel: Panel,
        balloons: List<Panel>,
        fitScale: Float,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        val maxScale = fitScale * BALLOON_ZOOM
        var index = 0
        while (index < balloons.size) {
            var group = balloons[index++]
            while (index < balloons.size) {
                val merged = group.union(balloons[index])
                if (merged.fitScale(viewWidth, viewHeight) < maxScale) {
                    break
                }
                group = merged
                index++
            }
            val scale = group.fitScale(viewWidth, viewHeight).coerceIn(fitScale, maxScale)
            output.add(
                PanelStop(
                    scale = scale,
                    centerX = centerIn(group.left, group.right, panel.left, panel.right, viewWidth / scale),
                    centerY = centerIn(group.top, group.bottom, panel.top, panel.bottom, viewHeight / scale),
                ),
            )
        }
    }

    /**
     * Center of the range, moved to keep the viewport within the panel.
     */
    private fun centerIn(start: Int, end: Int, panelStart: Int, panelEnd: Int, visible: Float): Float {
        if (panelEnd - panelStart <= visible) {
            return (panelStart + panelEnd) / 2f
        }
        return ((start + end) / 2f).coerceIn(panelStart + visible / 2f, panelEnd - visible / 2f)
    }

    private fun stepsCount(size: Float, visible: Float): Int {
        return (ceil((size - visible) / (visible * STEP_FACTOR)).toInt() + 1).coerceAtLeast(2)
    }

    private fun Panel.fitScale(viewWidth: Int, viewHeight: Int): Float {
        return FILL_FACTOR * minOf(viewWidth / width.toFloat(), viewHeight / height.toFloat())
    }

    private fun Panel.isCenterIn(panel: Panel): Boolean {
        return (left + right) / 2 in panel.left until panel.right && (top + bottom) / 2 in panel.top until panel.bottom
    }

    private fun Panel.union(other: Panel) = Panel(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )
}
