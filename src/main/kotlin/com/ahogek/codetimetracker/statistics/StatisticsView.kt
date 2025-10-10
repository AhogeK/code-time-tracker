package com.ahogek.codetimetracker.statistics

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.icons.AllIcons
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
import javax.swing.JPanel

/**
 * The main UI panel for the statistics tool window.
 * It contains a JCEF browser component to display web-based charts.*
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
        DailyHourDataProvider(),
        OverallHourlyDataProvider()
    )

    init {
        val actionGroup = DefaultActionGroup()
        val refreshAction = object : AnAction(
            "Refresh",
            "Reload statistics data",
            AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                loadAndRenderCharts()
            }
        }
        actionGroup.add(refreshAction)

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("StatisticsToolbar", actionGroup, true)
        actionToolbar.targetComponent = this

        add(actionToolbar.component, BorderLayout.NORTH)

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
        }

        browser = JBCefBrowser.createBuilder()
            .setClient(jbCefClient)
            .setUrl(virtualDomain + "index.html")
            .build()

        // Add handler AFTER browser creation
        jbCefClient.addRequestHandler(requestHandler, browser.cefBrowser)

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

    fun loadAndRenderCharts() {
        executeJavaScriptWhenLoaded {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusYears(1)

            val payload = buildMap {
                put("theme", getThemeColors())
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
     */
    private fun getThemeColors(): Map<String, Any> {
        return mapOf(
            "isDark" to !JBColor.isBright(),
            "foreground" to UIUtil.getLabelForeground().toHex(),
            "secondary" to UIUtil.getLabelDisabledForeground().toHex()
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