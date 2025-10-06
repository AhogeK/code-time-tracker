package com.ahogek.codetimetracker.statistics

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.BorderLayout
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

    init {
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
        jbCefClient.addRequestHandler(requestHandler, browser.getCefBrowser())

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
            // Get data from a database or other service (mock data used here)
            val data = """
            {
                "title": "Daily Coding Time (Mock Data)",
                "categories": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
                "values": [120, 200, 150, 80, 70, 110, 130]
            }
            """.trimIndent()
            val escapedData = data.replace("\\", "\\\\").replace("'", "\\'")
            val jsCode = """
                if (window.renderCharts) { 
                  window.renderCharts('$escapedData'); 
                }
            """.trimIndent()
            browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
        }
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