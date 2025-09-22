package com.ahogek.codetimetracker.activity

import com.ahogek.codetimetracker.service.GlobalEventMonitorService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 一个稳定的、公开的API入口点，在项目打开时运行。
 * 它的唯一职责就是去调用我们 application-level 服务的初始化方法。
 */
class StartupTriggerActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 获取我们的单例服务
        val service = ApplicationManager.getApplication().getService(GlobalEventMonitorService::class.java)
        // 调用初始化方法。即使这个Activity被多次调用，服务内部的逻辑门也会确保监听器只被添加一次。
        service.initializeListener()
    }
}