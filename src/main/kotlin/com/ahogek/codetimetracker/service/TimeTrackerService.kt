package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.model.CodingSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val IDLE_THRESHOLD_SECONDS = 120L
private const val IDLE_CHECK_INTERVAL_SECONDS = 5L

/**
 * 管理所有计时逻辑
 *
 * @author AhogeK ahogek@gmail.com
 * @date 9/23/25
 */
@Service(Service.Level.APP)
class TimeTrackerService : Disposable {
    private val log = Logger.getInstance(TimeTrackerService::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val activeSessions: MutableMap<String, CodingSession> = ConcurrentHashMap()
    private val lastActivityTime: AtomicReference<LocalDateTime> = AtomicReference(LocalDateTime.now())

    init {
        // 启动一个唯一的、周期性的任务来检查闲置状态
        scheduler.scheduleAtFixedRate(
            ::checkIdleStatus,
            IDLE_CHECK_INTERVAL_SECONDS,
            IDLE_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    /**
     * 当检测到用户活动时调用此方法。
     *
     * @param editor 当前活动的编辑器实例
     */
    fun onActivity(editor: Editor) {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val currentLanguage = file.fileType.name
        val now = LocalDateTime.now()

        // 更新最后活动时间
        lastActivityTime.set(now)

        // 检查语言是否切换
        val activeLanguage = activeSessions.keys.firstOrNull()
        if (activeLanguage != null && activeLanguage != currentLanguage) {
            log.info("Language switch detected from $activeLanguage to $currentLanguage. Pausing previous session.")
            // 立即暂停并保存上一个语言的会话
            pauseAndPersistSessions()
        }

        // 启动或更新当前语言会话
        val session = activeSessions.computeIfAbsent(currentLanguage) {
            log.info("Starting new coding session for $currentLanguage")
            CodingSession(currentLanguage, now, now)
        }
        session.endTime = now
    }

    /**
     * 由调度器定期调用，以检查用户是否闲置。
     */
    private fun checkIdleStatus() {
        val now = LocalDateTime.now()
        val lastActivity = lastActivityTime.get()
        val idleSeconds = ChronoUnit.SECONDS.between(lastActivity, now)

        if (idleSeconds >= IDLE_THRESHOLD_SECONDS && activeSessions.isNotEmpty()) {
            log.info("User has been idle for over $IDLE_THRESHOLD_SECONDS seconds. Pausing tracking.")
            ApplicationManager.getApplication().invokeLater {
                pauseAndPersistSessions()
            }
        }
    }

    /**
     * 暂停并持久化当前所有的活动会话。
     */
    private fun pauseAndPersistSessions() {
        if (activeSessions.isNotEmpty()) {
            val sessionsToStore = activeSessions.values.toList()
            log.info("Pausing tracking for: ${sessionsToStore.joinToString { it.language }}")
            activeSessions.clear()

            // TODO: 在这里添加数据持久化逻辑
            log.info("Persisted sessions: $sessionsToStore")
        }
    }

    fun stopTracking() {
        log.info("Stopping all tracking sessions immediately.")
        scheduler.shutdown()
        pauseAndPersistSessions()
    }

    override fun dispose() {
        stopTracking()
        log.info("TimeTrackerService disposed.")
    }
}