package com.ahogek.codetimetracker.statistics

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
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
            .build()

        // Add handler BEFORE loading the URL
        jbCefClient.addRequestHandler(requestHandler, browser.cefBrowser)

        // Set background to match IntelliJ theme
        browser.component.background = background

        add(browser.component, BorderLayout.CENTER)

        // Load URL after handler is registered
        browser.loadURL(virtualDomain + "index.html")
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
