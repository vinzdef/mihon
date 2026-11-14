package eu.kanade.tachiyomi.ui.reader.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelOrderTest {

    @Test
    fun rowsThenColumns() {
        val top = Panel(0, 0, 500, 300)
        val left = Panel(0, 310, 240, 800)
        val right = Panel(250, 310, 500, 800)
        val panels = listOf(right, top, left)

        assertEquals(listOf(top, left, right), PanelOrder.sort(panels, isRtl = false))
        assertEquals(listOf(top, right, left), PanelOrder.sort(panels, isRtl = true))
    }

    @Test
    fun columnIsReadBeforeTheNextOne() {
        val tall = Panel(260, 0, 500, 800)
        val upper = Panel(0, 0, 250, 390)
        val lower = Panel(0, 400, 250, 800)
        val panels = listOf(lower, tall, upper)

        assertEquals(listOf(upper, lower, tall), PanelOrder.sort(panels, isRtl = false))
        assertEquals(listOf(tall, upper, lower), PanelOrder.sort(panels, isRtl = true))
    }

    @Test
    fun slightlyOverlappingPanelsAreSeparated() {
        // tilted panels, as detected on a real page
        val a = Panel(0, 7, 320, 318)
        val b = Panel(279, 7, 503, 383)
        val c = Panel(0, 320, 503, 509)
        val d = Panel(7, 496, 143, 783)
        val e = Panel(155, 506, 340, 784)
        val f = Panel(344, 517, 503, 805)

        assertEquals(listOf(a, b, c, d, e, f), PanelOrder.sort(listOf(e, a, d, f, c, b), isRtl = false))
    }

    @Test
    fun insetGoesAfterThePanelBehindIt() {
        val splash = Panel(0, 6, 502, 384)
        val inset = Panel(328, 106, 481, 383)
        val next = Panel(19, 367, 486, 539)

        assertEquals(listOf(splash, inset, next), PanelOrder.sort(listOf(next, inset, splash), isRtl = false))
    }
}
