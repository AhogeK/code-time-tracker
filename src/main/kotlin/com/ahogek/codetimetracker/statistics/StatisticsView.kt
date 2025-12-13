package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.action.ExportDataAction
import com.ahogek.codetimetracker.action.ImportDataAction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Color
import java.time.Duration
import java.time.LocalDateTime
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The main UI panel for the statistics tool window.
 * It contains a JCEF browser component to display web-based charts.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-05 20:32:16
 */
class StatisticsView : JPanel(BorderLayout()), Disposable {

    private val jbCefClient: JBCefClient = JBCefApp.getInstance().createClient()
    private val browser: JBCefBrowser
    private val virtualDomain = "http://myapp.local/"

    @Volatile
    private var isBrowserLoaded = false
    private val pendingCalls = mutableListOf<() -> Unit>()

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Duration::class.java, DurationAdapter())
        .create()

    private val dataProvides: List<ChartDataProvider> = listOf(
        YearlyActivityDataProvider(),
        RecentActivityDataProvider(),
        DailyHourDataProvider(),
        OverallHourlyDataProvider(),
        LanguageDistributionDataProvider(),
        ProjectDistributionDataProvider(),
        TimeOfDayDistributionDataProvider()
    )

    private val summaryProvider = SummaryDataProvider()

    init {
        // Setup Toolbar (Updated with Import/Export)
        val actionToolbar = createToolBar(this)
        add(actionToolbar, BorderLayout.NORTH)

        // Setup Browser Request Handler
        val requestHandler = object : CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String,
                disableDefaultHandling: BoolRef
            ): CefResourceRequestHandler? {
                if (request.url?.startsWith(virtualDomain) == true) {
                    return resourceRequestHandler
                }
                return null
            }

            // Intercept external links and open in system browser
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean
            ): Boolean {
                val url = request?.url
                // Check if the URL is external (http/https) and not our internal virtual domain
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))
                    && !url.startsWith(virtualDomain)
                ) {
                    // Open in the user's default system browser (Chrome, Edge, Safari, etc.)
                    BrowserUtil.browse(url)
                    // Return true to cancel the navigation inside the IDE plugin window
                    return true
                }
                return false
            }
        }

        // Initialize Browser
        browser = JBCefBrowser.createBuilder()
            .setClient(jbCefClient)
            .setUrl(virtualDomain + "index.html")
            .build()

        // Add handler AFTER browser creation
        jbCefClient.addRequestHandler(requestHandler, browser.cefBrowser)

        jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?
            ): Boolean {
                if (targetUrl != null && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    BrowserUtil.browse(targetUrl)
                    return true
                }
                return false
            }
        }, browser.cefBrowser)

        jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(
                browser: CefBrowser?,
                frame: CefFrame?,
                httpStatusCode: Int
            ) {
                if (frame != null && frame.isMain) {
                    isBrowserLoaded = true
                    synchronized(pendingCalls) {
                        pendingCalls.forEach { it.invoke() }
                        pendingCalls.clear()
                    }
                }
            }
        }, browser.cefBrowser)

        add(browser.component, BorderLayout.CENTER)
    }

    /**
     * Creates the toolbar with Refresh, Import, and Export actions.
     */
    private fun createToolBar(content: JComponent): JComponent {
        val actionGroup = DefaultActionGroup()

        // Refresh Action
        actionGroup.add(object : AnAction("Refresh", "Reload statistics data", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                loadAndRenderCharts()
            }
        })

        actionGroup.addSeparator()

        // Import Action
        actionGroup.add(ImportDataAction())

        // Export Action
        actionGroup.add(ExportDataAction())

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("StatisticsToolbar", actionGroup, true)
        actionToolbar.targetComponent = content
        return actionToolbar.component
    }

    fun loadAndRenderCharts() {
        executeJavaScriptWhenLoaded {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusYears(1)

            // Compute summary statistics for header display
            val summaryData = summaryProvider.computeSummary()

            val payload = buildMap {
                put("theme", getThemeColors())

                // Add summary data (convert Duration to seconds for JSON)
                put(
                    "summaryData", mapOf(
                        "today" to summaryData.today.toSeconds(),
                        "dailyAverage" to summaryData.dailyAverage.toSeconds(),
                        "thisWeek" to summaryData.thisWeek.toSeconds(),
                        "thisMonth" to summaryData.thisMonth.toSeconds(),
                        "thisYear" to summaryData.thisYear.toSeconds(),
                        "total" to summaryData.total.toSeconds()
                    )
                )

                dataProvides.forEach { provider ->
                    val data = if (provider.requiresTimeRange()) {
                        provider.prepareData(startTime, endTime)
                    } else {
                        provider.prepareData()  // No time range - uses all data
                    }
                    put(provider.getChartKey(), data)
                }
            }

            executeJavaScript("renderCharts", payload)
        }
    }

    /**
     * Retrieves current theme colors from the IDE.
     * Uses brighter secondary color (#b0b0b0) for dark theme to improve readability.
     */
    private fun getThemeColors(): Map<String, Any> {
        val isDark = !JBColor.isBright()

        // For dark theme, use a brighter gray for better readability of axis labels,
        // legends, and subtitles. The default disabled foreground color is too dark.
        val secondaryColor = if (isDark) {
            "#b0b0b0"  // Bright gray (176, 176, 176) - good contrast on dark backgrounds
        } else {
            UIUtil.getLabelDisabledForeground().toHex()  // Keep IDE's default for light theme
        }

        return mapOf(
            "isDark" to isDark,
            "foreground" to UIUtil.getLabelForeground().toHex(),
            "secondary" to secondaryColor
        )
    }

    /**
     * Executes a JavaScript function with the given payload.
     */
    private fun executeJavaScript(@Suppress("SameParameterValue") functionName: String, payload: Map<String, Any>) {
        val data = gson.toJson(payload)
        val escapedData = data.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
        val jsCode = "if (window.$functionName) { window.$functionName('$escapedData'); }"
        browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
    }

    /**
     * Helper function to convert a Java Color object to a CSS-friendly hex string.
     */
    private fun Color.toHex(): String {
        return String.format("#%02x%02x%02x", this.red, this.green, this.blue)
    }

    /**
     * Safely execute JavaScript when the browser is loaded
     */
    private fun executeJavaScriptWhenLoaded(jsExecution: () -> Unit) {
        if (isBrowserLoaded) jsExecution.invoke()
        else synchronized(pendingCalls) {
            pendingCalls.add(jsExecution)
        }
    }

    private val resourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
        override fun getResourceHandler(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest
        ): CefResourceHandler? {
            val url = request.url ?: return null
            val resourcePath = "webview/" + url.substring(virtualDomain.length)
            val resourceStream = this.javaClass.classLoader.getResourceAsStream(resourcePath)

            if (resourceStream != null) {
                val mimeType = when {
                    resourcePath.endsWith(".html") -> "text/html"
                    resourcePath.endsWith(".js") -> "text/javascript"
                    resourcePath.endsWith(".css") -> "text/css"
                    else -> "application/octet-stream"
                }
                // Pass 'this@StatisticsView' as the parent Disposable
                return JBCefStreamResourceHandler(resourceStream, mimeType, this@StatisticsView)
            }
            return null
        }
    }

    override fun dispose() {
        Disposer.dispose(browser)
        Disposer.dispose(jbCefClient)
    }
}