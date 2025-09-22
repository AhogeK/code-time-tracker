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
        // 获取服务单例并调用其初始化方法
        val service = ApplicationManager.getApplication().getService(GlobalEventMonitorService::class.java)
        service.initializeListeners()
    }
}