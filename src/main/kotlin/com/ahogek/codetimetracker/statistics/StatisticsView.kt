package com.ahogek.codetimetracker.statistics

import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * The main UI panel for the statistics tool window.
 * It contains a JCEF browser component to display web-based charts.*
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-05 20:32:16
 */
class StatisticsView : JPanel(BorderLayout()) {

    // The JCEF browser instance where we will render our charts
    private val browser: JBCefBrowser = JBCefBrowser()

    init {
        // Add the browser's component to the center of our panel.
        // The BorderLayout will make it fill all available space.
        add(browser.component, BorderLayout.CENTER)

        // For now, we will load a simple HTML string to verify it works.
        // Later, we will load our actual chart page from the plugin's resources.
        browser.loadHTML("<h1>Loading Statistics...</h1>")
    }
}