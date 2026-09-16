package com.ahogek.codetimetracker.util

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger

/**
 * Normalizes raw IDE language identifiers to the canonical vocabulary used by the sync
 * server, so language statistics group one language once even when the same language is
 * reported under different spellings (`Kotlin` vs `kotlin`, `JAVA` vs `java`).
 *
 * <p>The vocabulary is data, not code: `language/vocabulary.json`, copied verbatim from
 * the ctt-server repository (`src/main/resources/language/vocabulary.json`, version 1,
 * GitHub Linguist names). The server owns the file - when its vocabulary version changes,
 * refresh this copy and re-run the vocabulary tests, which assert the shipped resource
 * loads and resolves aliases.
 *
 * <p>Matching mirrors the server's `LanguageVocabulary`: values are stripped and
 * case-folded before lookup; a value the vocabulary knows as a non-language collapses to
 * [OTHER]; an unrecognized value is preserved **verbatim** (not case-folded) so it stays
 * visible for later classification instead of disappearing into `Other`.
 *
 * <p>Normalization is display-only: the value stored in the local database and the value
 * pushed to the server are never rewritten - the canonical name is derived at read time.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-09-17 00:23:50
 */
object LanguageVocabulary {

    private val log = Logger.getInstance(LanguageVocabulary::class.java)

    /** Canonical target for values the vocabulary recognizes as non-languages. */
    const val OTHER = "Other"

    /** Shape of `language/vocabulary.json`; defaults keep a partially readable file loadable. */
    private data class VocabularyFile(
        /** File version, used to diagnose which copy is bundled. */
        val version: Int = 0,
        /** Canonical language name to GitHub Linguist category. */
        val canonical: Map<String, String> = emptyMap(),
        /** Raw spelling to canonical name; both sides are case-folded at index build. */
        val aliases: Map<String, String> = emptyMap(),
        /** Raw spellings recognized as non-languages (IDE internals, archives). */
        val nonLanguages: List<String> = emptyList(),
    )

    /** Token (stripped, case-folded) to canonical name: canonical names plus aliases. */
    private val byToken: Map<String, String>

    /** Tokens recognized as non-languages (IDE internals, archives, unclassified). */
    private val nonLanguages: Set<String>

    /** Vocabulary file version, for diagnosing which copy is bundled. */
    val version: Int

    init {
        val loaded = load()
        byToken = loaded.first
        nonLanguages = loaded.second
        version = loaded.third
    }

    /**
     * Maps [raw] to its canonical name. Blank input maps to an empty name (mirroring the
     * server), non-language values map to [OTHER], and anything the vocabulary does not
     * recognize is returned unchanged.
     */
    fun normalize(raw: String?): String {
        if (raw == null || raw.all { Character.isWhitespace(it) }) return ""
        val token = key(raw)
        if (token in nonLanguages) return OTHER
        return byToken[token] ?: raw
    }

    /**
     * Strip + case-fold, mirroring the server's `value.strip().toLowerCase(Locale.ROOT)`
     * exactly: `Character.isWhitespace` matches Java `strip()` (Kotlin's `trim()` also
     * strips space characters such as NBSP that Java keeps, so it is deliberately not
     * used), and `lowercase()` is locale-invariant. The mirroring is the point - a value
     * the two sides fold differently would still bucket differently from the web UI.
     */
    private fun key(value: String): String = value.trim { Character.isWhitespace(it) }.lowercase()

    /**
     * Reads and indexes the bundled vocabulary. Loaded once at object construction; a
     * missing or broken file degrades to pass-through (empty vocabulary, every value
     * returned unchanged) instead of throwing, so statistics keep working.
     */
    private fun load(): Triple<Map<String, String>, Set<String>, Int> {
        return try {
            val json = LanguageVocabulary::class.java.classLoader
                .getResourceAsStream(RESOURCE)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("classpath resource $RESOURCE is missing")
            val file = Gson().fromJson(json, VocabularyFile::class.java)

            val index = mutableMapOf<String, String>()
            for (name in file.canonical.keys) {
                index.putIfAbsent(key(name), name)
            }
            for ((alias, target) in file.aliases) {
                val canonical = index[key(target)]
                if (canonical == null) {
                    log.warn("Vocabulary alias '$alias' points at unknown language '$target'; ignored")
                    continue
                }
                index.putIfAbsent(key(alias), canonical)
            }
            log.info(
                "Language vocabulary v${file.version} loaded: ${file.canonical.size} languages, " +
                    "${file.aliases.size} aliases, ${file.nonLanguages.size} non-language values",
            )
            Triple(index, file.nonLanguages.map(::key).toSet(), file.version)
        } catch (e: Exception) {
            // Statistics must keep working even if the resource is unreadable; without a
            // vocabulary every value passes through unchanged (the pre-normalization behavior).
            log.error("Failed to load $RESOURCE; language names will not be normalized", e)
            Triple(emptyMap(), emptySet(), 0)
        }
    }

    private const val RESOURCE = "language/vocabulary.json"
}
