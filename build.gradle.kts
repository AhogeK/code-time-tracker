import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

val intellijPlatformExtension = extensions.getByType(IntelliJPlatformExtension::class.java)

plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.intellij.platform)
    alias(libs.plugins.ben.manes)
}

group = "com.ahogek"
version = libs.versions.pluginVersion.get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IU", "2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.platform.images")

        implementation(libs.sqlite.jdbc)
        implementation(libs.gson)
        implementation(libs.lgooddatepicker)
    }

    // The credentialStore platform module (PasswordSafe) is bundled inside the IDE's app.jar,
    // which the IntelliJ Platform Gradle Plugin excludes from the compile classpath (no
    // independent module jar, no module alias — see JetBrains/intellij-platform-gradle-plugin#2144).
    // app.jar is provided by the IDE at runtime and is never bundled into the plugin.
    compileOnly(files({ intellijPlatformExtension.platformPath.resolve("lib/app.jar").toFile() }))

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.vintage.engine)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

java {
    toolchain {
        // Build with JDK 25 (LTS); the plugin targets the IDE's JBR 25 runtime.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    // Compile against the IDE's JBR 25 runtime: --release bounds both the
    // bytecode version and the Java API surface.
    withType<JavaCompile> {
        options.release.set(25)
    }
}

// Build-time sync configuration. Resolution order: -Pctt.* Gradle property, then
// CTT_* environment variable, then the project .env file (see .env.example), then
// the development defaults. A release build only changes these values, never the code.
fun loadDotEnv(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val idx = line.indexOf('=')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
}

val dotEnv: Map<String, String> = loadDotEnv(rootProject.file(".env"))

val syncWebUrl: String = providers.gradleProperty("ctt.webUrl").orNull
    ?: System.getenv("CTT_WEB_URL")
    ?: dotEnv["CTT_WEB_URL"]
    ?: "http://localhost:5173/"
val syncServerUrl: String = providers.gradleProperty("ctt.serverUrl").orNull
    ?: System.getenv("CTT_SERVER_URL")
    ?: dotEnv["CTT_SERVER_URL"]
    ?: "http://localhost:8080/ctt-server"
// Captured at configuration time: reading project.version inside doLast is
// unsupported with the configuration cache (execution-time Task.project access).
val syncAppVersion: String = project.version.toString()

/**
 * Generates SyncWebConfig.kt with the build-time sync URLs and app version.
 * Implemented as a task class with injected properties so the build stays
 * configuration-cache compatible (no execution-time access to script objects).
 */
abstract class GenerateSyncConfig : DefaultTask() {
    @get:Input
    abstract val webUrl: Property<String>

    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val content = """
            |package com.ahogek.codetimetracker.service.sync
            |
            |/**
            | * Generated at build time from -Pctt.*, CTT_* environment variables or the
            | * project .env file (see .env.example). The settings page can still override
            | * the server URL at runtime; the web console URL is fixed per build.
            | */
            |object SyncWebConfig {
            |    const val WEB_URL: String = "${webUrl.get()}"
            |    const val DEFAULT_SERVER_URL: String = "${serverUrl.get()}"
            |    const val APP_VERSION: String = "${appVersion.get()}"
            |}
            |""".trimMargin()
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(content)
    }
}

val generateSyncConfig by tasks.registering(GenerateSyncConfig::class) {
    webUrl.set(syncWebUrl)
    serverUrl.set(syncServerUrl)
    appVersion.set(syncAppVersion)
    outputFile.set(
        layout.buildDirectory.file("generated/syncConfig/kotlin/com/ahogek/codetimetracker/service/sync/SyncWebConfig.kt"),
    )
}

kotlin {
    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/syncConfig/kotlin"))
}
tasks.named("compileKotlin") { dependsOn(generateSyncConfig) }

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    processResources {
        from(project.layout.projectDirectory.file("LICENSE")) {
            into(".")
        }
    }
}

// Configure dependency updates task following official best practices
// https://github.com/ben-manes/gradle-versions-plugin
tasks.withType<DependencyUpdatesTask> {
    // Use 'release' revision to check only stable versions
    // This can also be set via: ./gradlew dependencyUpdates -Drevision=release
    revision = "release"

    // Check for Gradle updates (enabled by default, shown here for clarity)
    checkForGradleUpdate = true

    // Use stable 'current' release channel instead of 'release-candidate'
    gradleReleaseChannel = "current"

    // Output format: plain text to console (default)
    // Can be overridden via: -DoutputFormatter=json,xml,html
    outputFormatter = "plain"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"

    // Reject non-stable versions as upgradeable candidates
    // This prevents suggesting alpha/beta/rc versions when on stable releases
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

/**
 * Determines if a version string represents a non-stable release.
 *
 * Stability indicators:
 * - Keywords: RELEASE, FINAL, GA (General Availability)
 * - Pattern: Numeric versions like "1.2.3" or "1.2.3-r" are considered stable
 *
 * @param version The version string to evaluate
 * @return true if the version is non-stable (alpha, beta, rc, snapshot, etc.)
 */
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // Task-level assignment: IPGP applies jvmTarget.convention(...) per task, which would
    // override the kotlin {} extension default; a strong set() here wins.
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
}

