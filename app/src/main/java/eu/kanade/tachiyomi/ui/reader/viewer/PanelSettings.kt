package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.panel.PanelModel

/**
 * Settings of reading panel by panel.
 */
data class PanelSettings(
    val page: Page,
    val wholePageFirst: Boolean,
    val center: Boolean,
) {

    /**
     * What the panels are made with, so a change remakes them.
     */
    data class Page(
        val enabled: Boolean,
        val steps: Boolean,
        val model: PanelModel,
    )
}
