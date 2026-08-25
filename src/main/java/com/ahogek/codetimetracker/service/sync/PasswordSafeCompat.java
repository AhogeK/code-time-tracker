package com.ahogek.codetimetracker.service.sync;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java bridge to the IDE credential store.
 *
 * <p>The Kotlin K2 compiler fails to resolve the {@code @JvmStatic} bridge
 * {@code PasswordSafe.getInstance()} on the 2025.3 platform classes (the companion
 * member and its static bridge exist in bytecode and are callable from Java), so the
 * credential-store access is kept in Java where the call compiles and behaves
 * correctly. The IDE provides {@link PasswordSafe} at runtime; it is never bundled.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
final class PasswordSafeCompat {

    private PasswordSafeCompat() {
    }

    static void save(@NotNull String service, @NotNull String user, @NotNull String rawKey) {
        PasswordSafe.getInstance().setPassword(new CredentialAttributes(service, user), rawKey);
    }

    @Nullable
    static String load(@NotNull String service, @NotNull String user) {
        return PasswordSafe.getInstance().getPassword(new CredentialAttributes(service, user));
    }

    static void clear(@NotNull String service, @NotNull String user) {
        PasswordSafe.getInstance().setPassword(new CredentialAttributes(service, user), null);
    }
}
