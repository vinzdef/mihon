package eu.kanade.tachiyomi.ui.reader.panel

import kotlin.math.abs

/**
 * Recursive XY-cut along blank gutters. Rows are cut before columns, so the result is in reading order.
 */
object PanelSplitter {

    private const val MAX_DEPTH = 4
    private const val COLOR_TOLERANCE = 40
    private const val MIN_PAGE_SIZE = 32
    private const val GUTTER_DIVIDER = 128
    private const val PANEL_DIVIDER = 16
    private const val BORDER_DIVIDER = 50
    private const val EDGE_DIVIDER = 100
    private const val MIN_EDGE_FILL = 0.75f
    private const val MIN_FRAMED_SIDES = 3
    private const val MIN_FRAMED_SHARE = 0.5f
    private const val MIN_COVERAGE = 0.7f

    fun split(pixels: IntArray, width: Int, height: Int, isRtl: Boolean): List<Panel> {
        require(pixels.size >= width * height) { "Pixels array is too small" }
        if (width < MIN_PAGE_SIZE || height < MIN_PAGE_SIZE) {
            return emptyList()
        }
        val result = ArrayList<Panel>()
        val splitter = Splitter(buildInkTable(pixels, width, height), width, height, isRtl, result)
        splitter.split(0, 0, width, height, 0)
        return if (result.size > 1 && splitter.isPanelLayout(result)) result else emptyList()
    }

    /**
     * Summed-area table of non-background pixels.
     */
    private fun buildInkTable(pixels: IntArray, width: Int, height: Int): IntArray {
        val background = if (isBackgroundLight(pixels, width, height)) 255 else 0
        val stride = width + 1
        val table = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0
            val offset = y * width
            val row = (y + 1) * stride
            for (x in 0 until width) {
                if (pixels[offset + x].distanceTo(background) > COLOR_TOLERANCE) {
                    rowSum++
                }
                table[row + x + 1] = table[row - stride + x + 1] + rowSum
            }
        }
        return table
    }

    private fun isBackgroundLight(pixels: IntArray, width: Int, height: Int): Boolean {
        val border = (minOf(width, height) / BORDER_DIVIDER).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        for (y in 0 until height) {
            val isEdgeRow = y < border || y >= height - border
            val offset = y * width
            var x = 0
            while (x < width) {
                sum += pixels[offset + x].luminance()
                count++
                x = if (!isEdgeRow && x == border - 1) width - border else x + 1
            }
        }
        return sum / count >= 128
    }

    // Per channel, so bright colors aren't taken for a white background
    private fun Int.distanceTo(gray: Int): Int {
        return maxOf(
            abs(((this shr 16) and 0xFF) - gray),
            abs(((this shr 8) and 0xFF) - gray),
            abs((this and 0xFF) - gray),
        )
    }

    private fun Int.luminance(): Int {
        return (((this shr 16) and 0xFF) * 77 + ((this shr 8) and 0xFF) * 150 + (this and 0xFF) * 29) shr 8
    }

    private class Splitter(
        private val table: IntArray,
        width: Int,
        height: Int,
        private val isRtl: Boolean,
        private val output: MutableList<Panel>,
    ) {

        private val stride = width + 1
        private val minGutter = (minOf(width, height) / GUTTER_DIVIDER).coerceAtLeast(2)
        private val minPanelWidth = width / PANEL_DIVIDER
        private val minPanelHeight = height / PANEL_DIVIDER
        private val edgeSize = (minOf(width, height) / EDGE_DIVIDER).coerceAtLeast(2)

        fun split(left: Int, top: Int, right: Int, bottom: Int, depth: Int) {
            var l = left
            var t = top
            var r = right
            var b = bottom
            while (t < b && ink(l, t, r, t + 1) == 0) t++
            while (b > t && ink(l, b - 1, r, b) == 0) b--
            while (l < r && ink(l, t, l + 1, b) == 0) l++
            while (r > l && ink(r - 1, t, r, b) == 0) r--
            if (r - l < minPanelWidth || b - t < minPanelHeight) {
                return
            }
            if (depth < MAX_DEPTH) {
                val rows = findBands(l, t, r, b, isHorizontal = true)
                if (rows.size > 2) {
                    for (i in rows.indices step 2) {
                        split(l, rows[i], r, rows[i + 1], depth + 1)
                    }
                    return
                }
                val columns = findBands(l, t, r, b, isHorizontal = false)
                if (columns.size > 2) {
                    val indices = if (isRtl) {
                        columns.size - 2 downTo 0 step 2
                    } else {
                        columns.indices step 2
                    }
                    for (i in indices) {
                        split(columns[i], t, columns[i + 1], b, depth + 1)
                    }
                    return
                }
            }
            output.add(Panel(l, t, r, b))
        }

        /**
         * Tells a comic page from a cover or title page, whose content is also separated by blank space.
         */
        fun isPanelLayout(panels: List<Panel>): Boolean {
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = 0
            var bottom = 0
            var area = 0L
            var framedArea = 0L
            for (panel in panels) {
                left = minOf(left, panel.left)
                top = minOf(top, panel.top)
                right = maxOf(right, panel.right)
                bottom = maxOf(bottom, panel.bottom)
                val panelArea = panel.width.toLong() * panel.height
                area += panelArea
                if (isFramed(panel)) {
                    framedArea += panelArea
                }
            }
            val totalArea = (right - left).toLong() * (bottom - top)
            return area >= totalArea * MIN_COVERAGE && framedArea >= area * MIN_FRAMED_SHARE
        }

        private fun isFramed(panel: Panel): Boolean {
            var sides = 0
            if (isEdgeFilled(panel, isHorizontal = true, isStart = true)) sides++
            if (isEdgeFilled(panel, isHorizontal = true, isStart = false)) sides++
            if (isEdgeFilled(panel, isHorizontal = false, isStart = true)) sides++
            if (isEdgeFilled(panel, isHorizontal = false, isStart = false)) sides++
            return sides >= MIN_FRAMED_SIDES
        }

        private fun isEdgeFilled(panel: Panel, isHorizontal: Boolean, isStart: Boolean): Boolean {
            var filled = 0
            if (isHorizontal) {
                val size = minOf(edgeSize, panel.height)
                val top = if (isStart) panel.top else panel.bottom - size
                for (x in panel.left until panel.right) {
                    if (ink(x, top, x + 1, top + size) != 0) filled++
                }
                return filled >= panel.width * MIN_EDGE_FILL
            } else {
                val size = minOf(edgeSize, panel.width)
                val left = if (isStart) panel.left else panel.right - size
                for (y in panel.top until panel.bottom) {
                    if (ink(left, y, left + size, y + 1) != 0) filled++
                }
                return filled >= panel.height * MIN_EDGE_FILL
            }
        }

        /**
         * @return start (inclusive) and end (exclusive) pairs of the bands between gutters
         */
        private fun findBands(left: Int, top: Int, right: Int, bottom: Int, isHorizontal: Boolean): List<Int> {
            val from = if (isHorizontal) top else left
            val to = if (isHorizontal) bottom else right
            val bands = ArrayList<Int>(4)
            var bandStart = from
            var blankStart = -1
            for (i in from until to) {
                val isBlank = if (isHorizontal) {
                    ink(left, i, right, i + 1) == 0
                } else {
                    ink(i, top, i + 1, bottom) == 0
                }
                if (isBlank) {
                    if (blankStart < 0) {
                        blankStart = i
                    }
                } else {
                    if (blankStart >= 0 && i - blankStart >= minGutter) {
                        bands.add(bandStart)
                        bands.add(blankStart)
                        bandStart = i
                    }
                    blankStart = -1
                }
            }
            bands.add(bandStart)
            bands.add(to)
            return bands
        }

        private fun ink(left: Int, top: Int, right: Int, bottom: Int): Int {
            return table[bottom * stride + right] - table[top * stride + right] -
                table[bottom * stride + left] + table[top * stride + left]
        }
    }
}
