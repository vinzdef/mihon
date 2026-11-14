package eu.kanade.tachiyomi.ui.reader.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelStopsTest {

    @Test
    fun smallPanelIsShownAtOnce() {
        val panel = Panel(0, 0, 500, 700)
        val stops = compute(panel, isRtl = false)

        assertEquals(1, stops.size)
        assertEquals(250f, stops[0].centerX, 0.01f)
        assertEquals(350f, stops[0].centerY, 0.01f)
        assertTrue(stops[0].scale * panel.width <= VIEW_WIDTH)
        assertTrue(stops[0].scale * panel.height <= VIEW_HEIGHT)
    }

    @Test
    fun widePanelIsReadInSteps() {
        val panel = Panel(0, 0, 1000, 400)
        val pageScale = VIEW_WIDTH / PAGE_WIDTH.toFloat()

        val ltr = compute(panel, isRtl = false)
        assertEquals(2, ltr.size)
        assertTrue(ltr.all { it.scale > pageScale * 1.5f })
        assertTrue(ltr.all { it.scale * panel.height <= VIEW_HEIGHT })
        assertTrue(ltr.zipWithNext().all { (a, b) -> a.centerX < b.centerX })
        // the whole panel is covered, from edge to edge
        val visible = VIEW_WIDTH / ltr[0].scale
        assertEquals(0f, ltr.first().centerX - visible / 2f, 0.01f)
        assertEquals(1000f, ltr.last().centerX + visible / 2f, 0.01f)
        assertTrue(ltr.zipWithNext().all { (a, b) -> b.centerX - a.centerX < visible })

        val rtl = compute(panel, isRtl = true)
        assertEquals(ltr.reversed(), rtl)
    }

    @Test
    fun fullPagePanelIsNotPanned() {
        assertEquals(1, compute(Panel(0, 0, PAGE_WIDTH, PAGE_HEIGHT), isRtl = false).size)
    }

    @Test
    fun widePanelIsShownAtOnceIfStepsDisabled() {
        val stops = compute(Panel(0, 0, 1000, 400), isRtl = false, isStepsEnabled = false)
        assertEquals(1, stops.size)
        assertEquals(500f, stops[0].centerX, 0.01f)
    }

    private fun compute(panel: Panel, isRtl: Boolean, isStepsEnabled: Boolean = true) = PanelStops.compute(
        panels = listOf(panel),
        viewWidth = VIEW_WIDTH,
        viewHeight = VIEW_HEIGHT,
        pageWidth = PAGE_WIDTH,
        pageHeight = PAGE_HEIGHT,
        isRtl = isRtl,
        isStepsEnabled = isStepsEnabled,
    )

    private companion object {

        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2000
        const val PAGE_WIDTH = 1000
        const val PAGE_HEIGHT = 1500
    }
}
