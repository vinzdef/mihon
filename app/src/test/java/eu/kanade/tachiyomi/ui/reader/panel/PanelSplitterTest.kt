package eu.kanade.tachiyomi.ui.reader.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelSplitterTest {

    @Test
    fun gridIsReadRowByRow() {
        val page = Page()
        page.fill(10, 10, 95, 140)
        page.fill(105, 10, 190, 140)
        page.fill(10, 160, 95, 290)
        page.fill(105, 160, 190, 290)

        assertEquals(
            listOf(
                Panel(10, 10, 95, 140),
                Panel(105, 10, 190, 140),
                Panel(10, 160, 95, 290),
                Panel(105, 160, 190, 290),
            ),
            page.split(isRtl = false),
        )
        assertEquals(
            listOf(
                Panel(105, 10, 190, 140),
                Panel(10, 10, 95, 140),
                Panel(105, 160, 190, 290),
                Panel(10, 160, 95, 290),
            ),
            page.split(isRtl = true),
        )
    }

    @Test
    fun nestedLayout() {
        val page = Page()
        // a tall panel on the right and two stacked panels on the left
        page.fill(110, 10, 190, 290)
        page.fill(10, 10, 100, 140)
        page.fill(10, 150, 100, 290)

        assertEquals(
            listOf(
                Panel(110, 10, 190, 290),
                Panel(10, 10, 100, 140),
                Panel(10, 150, 100, 290),
            ),
            page.split(isRtl = true),
        )
    }

    @Test
    fun darkBackground() {
        val page = Page(background = BLACK)
        page.fill(10, 10, 190, 140, WHITE)
        page.fill(10, 160, 190, 290, WHITE)

        assertEquals(
            listOf(Panel(10, 10, 190, 140), Panel(10, 160, 190, 290)),
            page.split(isRtl = false),
        )
    }

    @Test
    fun brightColorsAreNotBackground() {
        val page = Page()
        page.fill(10, 10, 190, 140, YELLOW)
        page.fill(10, 160, 190, 290, YELLOW)

        assertEquals(
            listOf(Panel(10, 10, 190, 140), Panel(10, 160, 190, 290)),
            page.split(isRtl = false),
        )
    }

    @Test
    fun emptyFramedPanelIsNotSplit() {
        val page = Page()
        page.fill(10, 10, 190, 140)
        page.fill(12, 12, 188, 138, WHITE)
        page.fill(10, 160, 190, 290)

        assertEquals(
            listOf(Panel(10, 10, 190, 140), Panel(10, 160, 190, 290)),
            page.split(isRtl = false),
        )
    }

    @Test
    fun smallFragmentsAreIgnored() {
        val page = Page()
        page.fill(10, 10, 190, 130)
        page.fill(10, 140, 190, 270)
        page.fill(95, 285, 105, 292) // page number

        assertEquals(
            listOf(Panel(10, 10, 190, 130), Panel(10, 140, 190, 270)),
            page.split(isRtl = false),
        )
    }

    @Test
    fun pageWithoutGuttersHasNoPanels() {
        val page = Page()
        page.fill(0, 0, WIDTH, HEIGHT)
        assertTrue(page.split(isRtl = false).isEmpty())

        val single = Page()
        single.fill(10, 10, 190, 290)
        assertTrue(single.split(isRtl = false).isEmpty())
    }

    @Test
    fun scatteredPicturesAreNotPanels() {
        // cover-like page: a picture, a title and a logo with a lot of blank space around
        val page = Page()
        page.fill(60, 20, 140, 60)
        page.fill(30, 120, 90, 180)
        page.fill(130, 250, 180, 280)
        assertTrue(page.split(isRtl = false).isEmpty())
    }

    @Test
    fun shapesWithoutFramesAreNotPanels() {
        val page = Page()
        page.fillTriangle(10, 10, 190, 140)
        page.fillTriangle(10, 160, 190, 290)
        assertTrue(page.split(isRtl = false).isEmpty())
    }

    private class Page(background: Int = WHITE) {

        private val pixels = IntArray(WIDTH * HEIGHT) { background }

        fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int = if (pixels[0] == WHITE) BLACK else WHITE) {
            for (y in top until bottom) {
                pixels.fill(color, y * WIDTH + left, y * WIDTH + right)
            }
        }

        fun fillTriangle(left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top until bottom) {
                val half = (right - left) * (y - top + 1) / (bottom - top) / 2
                val center = (left + right) / 2
                pixels.fill(BLACK, y * WIDTH + center - half, y * WIDTH + center + half)
            }
        }

        fun split(isRtl: Boolean) = PanelSplitter.split(pixels, WIDTH, HEIGHT, isRtl)
    }

    private companion object {

        const val WIDTH = 200
        const val HEIGHT = 300
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
    }
}
