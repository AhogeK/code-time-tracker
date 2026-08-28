package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationInfo
import java.net.InetAddress

/**
 * Builds the device metadata sent to `POST /api/v1/devices` when the plugin binds an
 * API key. The device id is the stable installation id ([UserManager.getUserId]); the
 * remaining fields identify the host machine and the running IDE so the server's
 * device list stays readable. Field lengths are bounded by the server contract
 * (name <= 255, platform/ideVersion/appVersion <= 50, ideName <= 100).
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-27
 */
object SyncDeviceMetadata {

    fun deviceId(): String = UserManager.getUserId()

    fun registrationRequest(): RegisterDeviceRequest = RegisterDeviceRequest(
        deviceId = deviceId(),
        deviceName = runCatching { InetAddress.getLocalHost().hostName }.getOrNull(),
        platform = System.getProperty("os.name"),
        ideName = ApplicationInfo.getInstance().versionName,
        ideVersion = ApplicationInfo.getInstance().fullVersion,
        appVersion = SyncWebConfig.APP_VERSION,
    )
}
