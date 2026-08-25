package com.ahogek.codetimetracker.service.sync

/**
 * Storage boundary for the raw sync API key. The production implementation keeps the
 * secret in the IDE credential store; tests substitute an in-memory fake.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
interface SyncKeyVault {
    fun save(rawKey: String)
    fun load(): String?
    fun clear()
}

/**
 * Credential-store backed vault (IntelliJ PasswordSafe via [PasswordSafeCompat]).
 * The key never touches the plugin config file or the local database.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
object PasswordSyncKeyVault : SyncKeyVault {

    private const val SERVICE_NAME = "com.ahogek.codetimetracker.sync"
    private const val USER_NAME = "api-key"

    override fun save(rawKey: String) {
        PasswordSafeCompat.save(SERVICE_NAME, USER_NAME, rawKey)
    }

    override fun load(): String? = PasswordSafeCompat.load(SERVICE_NAME, USER_NAME)

    override fun clear() {
        PasswordSafeCompat.clear(SERVICE_NAME, USER_NAME)
    }
}
