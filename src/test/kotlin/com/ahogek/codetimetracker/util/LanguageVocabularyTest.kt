package com.ahogek.codetimetracker.util

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LanguageVocabularyTest {

    @Test
    fun `should merge spelling variants of the same language`() {
        assertThat(LanguageVocabulary.normalize("Kotlin")).isEqualTo("Kotlin")
        assertThat(LanguageVocabulary.normalize("kotlin")).isEqualTo("Kotlin")
        assertThat(LanguageVocabulary.normalize("KOTLIN")).isEqualTo("Kotlin")
        assertThat(LanguageVocabulary.normalize("  Kotlin  ")).isEqualTo("Kotlin")
    }

    @Test
    fun `should resolve an alias to its canonical name`() {
        assertThat(LanguageVocabulary.normalize("kotlin/native def")).isEqualTo("Kotlin")
    }

    @Test
    fun `should collapse a known non-language to Other`() {
        assertThat(LanguageVocabulary.normalize("textmate")).isEqualTo(LanguageVocabulary.OTHER)
        assertThat(LanguageVocabulary.normalize("TEXTMATE")).isEqualTo(LanguageVocabulary.OTHER)
    }

    @Test
    fun `should preserve an unrecognized value verbatim`() {
        // Not case-folded on purpose: the raw value stays visible so it can be classified
        // into the vocabulary later instead of silently disappearing into Other.
        assertThat(LanguageVocabulary.normalize("MyUnknownLang")).isEqualTo("MyUnknownLang")
    }

    @Test
    fun `should map blank input to an empty name`() {
        assertThat(LanguageVocabulary.normalize(null)).isEmpty()
        assertThat(LanguageVocabulary.normalize("")).isEmpty()
        assertThat(LanguageVocabulary.normalize("   ")).isEmpty()
    }

    @Test
    fun `should mirror the server strip semantics for special whitespace`() {
        // Java's strip() (server side) does not remove NBSP while Kotlin's trim() would,
        // so a padded value must stay unrecognized here too - both sides bucket it alike.
        assertThat(LanguageVocabulary.normalize("\u00A0Kotlin\u00A0")).isEqualTo("\u00A0Kotlin\u00A0")
    }

    @Test
    fun `should load the shipped vocabulary resource`() {
        // Guards the bundled copy: if the resource is missing or unparsable the object
        // degrades to pass-through and the version stays 0. Pinning the expected version
        // also catches a copy of the wrong revision - refresh both sides together.
        assertThat(LanguageVocabulary.version).isEqualTo(2)
    }

    @Test
    fun `should resolve languages that vocabulary v1 was missing`() {
        // v1 was reverse-engineered from the file types one machine could enumerate, so 750
        // real languages (Elixir, Zig, Astro, ...) were absent and resolved as unknown -
        // reading like "the server does not know it" when the vocabulary never had it. v2
        // takes the standard itself (GitHub Linguist) as the source, so these must resolve.
        assertThat(LanguageVocabulary.normalize("astro")).isEqualTo("Astro")
        assertThat(LanguageVocabulary.normalize("Elixir")).isEqualTo("Elixir")
        assertThat(LanguageVocabulary.normalize("ZIG")).isEqualTo("Zig")
        assertThat(LanguageVocabulary.normalize("Svelte")).isEqualTo("Svelte")
    }

    @Test
    fun `every alias should resolve to a canonical language`() {
        // Data-integrity guard mirroring the server's fail-fast constructor check. At
        // runtime a dangling alias is skipped with a warning, which degrades as "the
        // variant that should merge does not" - harder to notice than a crash. The
        // vocabulary is a byte-identical copy of the server's file, so this test is the
        // CI equivalent of the server's startup validation.
        val json = requireNotNull(javaClass.classLoader.getResourceAsStream("language/vocabulary.json")) {
            "vocabulary resource language/vocabulary.json is missing"
        }.bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(json).asJsonObject
        val canonicalTokens = root.getAsJsonObject("canonical").keySet()
            .map { it.trim().lowercase() }
            .toSet()
        assertThat(canonicalTokens).isNotEmpty()

        val dangling = root.getAsJsonObject("aliases").entrySet()
            .filter { it.value.asString.trim().lowercase() !in canonicalTokens }
            .map { "${it.key} -> ${it.value.asString}" }

        assertThat(dangling).isEmpty()
    }
}
