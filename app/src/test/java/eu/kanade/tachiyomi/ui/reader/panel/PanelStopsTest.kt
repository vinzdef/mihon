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

    @Test
    fun largePanelIsReadAlongBalloonsAfterShownWhole() {
        val panel = Panel(0, 0, PAGE_WIDTH, PAGE_HEIGHT)
        val first = Panel(700, 100, 900, 300)
        val second = Panel(100, 1100, 300, 1300)
        val stops = compute(listOf(panel), balloons = listOf(second, first))

        assertEquals(3, stops.size)
        assertEquals(compute(panel, isRtl = false), stops.take(1))
        for ((stop, balloon) in stops.drop(1).zip(listOf(first, second))) {
            assertTrue(stop.scale > stops[0].scale * 1.5f)
            val halfWidth = VIEW_WIDTH / stop.scale / 2f
            val halfHeight = VIEW_HEIGHT / stop.scale / 2f
            assertTrue(stop.centerX - halfWidth <= balloon.left && stop.centerX + halfWidth >= balloon.right)
            assertTrue(stop.centerY - halfHeight <= balloon.top && stop.centerY + halfHeight >= balloon.bottom)
            // the viewport stays in the panel
            assertTrue(stop.centerX - halfWidth >= -0.01f && stop.centerX + halfWidth <= PAGE_WIDTH + 0.01f)
            assertTrue(stop.centerY - halfHeight >= -0.01f && stop.centerY + halfHeight <= PAGE_HEIGHT + 0.01f)
        }
    }

    @Test
    fun nearbyBalloonsShareStop() {
        val balloons = listOf(Panel(100, 100, 300, 300), Panel(350, 150, 500, 400))
        assertEquals(2, compute(listOf(Panel(0, 0, PAGE_WIDTH, PAGE_HEIGHT)), balloons).size)
    }

    @Test
    fun smallPanelIgnoresBalloons() {
        assertEquals(1, compute(listOf(Panel(0, 0, 500, 700)), balloons = listOf(Panel(100, 100, 200, 200))).size)
    }

    @Test
    fun pageWithoutPanelsIsReadAlongBalloons() {
        assertEquals(2, compute(emptyList(), balloons = listOf(Panel(100, 100, 300, 300))).size)
        assertTrue(compute(emptyList(), balloons = emptyList()).isEmpty())
    }

    @Test
    fun pagePanelIsNotShownAgainAfterWholePage() {
        val page = listOf(Panel(0, 0, PAGE_WIDTH, PAGE_HEIGHT))
        val balloons = listOf(Panel(100, 100, 300, 300))

        assertTrue(compute(page, emptyList(), isWholePageShown = true).isEmpty())
        assertEquals(compute(page, balloons).drop(1), compute(page, balloons, isWholePageShown = true))
        assertEquals(1, compute(listOf(Panel(0, 0, 500, 700)), emptyList(), isWholePageShown = true).size)
    }

    private fun compute(panel: Panel, isRtl: Boolean, isStepsEnabled: Boolean = true) =
        compute(listOf(panel), emptyList(), isRtl, isStepsEnabled)

    private fun compute(
        panels: List<Panel>,
        balloons: List<Panel>,
        isRtl: Boolean = false,
        isStepsEnabled: Boolean = true,
        isWholePageShown: Boolean = false,
    ) = PanelStops.compute(
        panels = panels,
        balloons = balloons,
        viewWidth = VIEW_WIDTH,
        viewHeight = VIEW_HEIGHT,
        pageWidth = PAGE_WIDTH,
        pageHeight = PAGE_HEIGHT,
        isRtl = isRtl,
        isStepsEnabled = isStepsEnabled,
        isWholePageShown = isWholePageShown,
    )

    private companion object {

        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2000
        const val PAGE_WIDTH = 1000
        const val PAGE_HEIGHT = 1500
    }
}
