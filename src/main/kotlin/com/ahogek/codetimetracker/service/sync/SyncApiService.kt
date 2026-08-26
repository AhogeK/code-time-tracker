package com.ahogek.codetimetracker.service.sync

/**
 * High-level ctt-server endpoints used by the plugin's sync feature.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
interface SyncApiService {

    /**
     * Checks server reachability without credentials. Any HTTP response (including
     * 401/403) counts as reachable; only transport failures are reported.
     */
    fun pingServer(): SyncResult<Unit>
}
