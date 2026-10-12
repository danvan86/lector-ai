package gmbh.vanella.lector.ecosystems.jvm

/**
 * The Gradle editable scope: the JVM scope ([JvmScope]), without Gradle's caches (`.gradle/`), the wrapper, which decides
 * what code is downloaded and run, and Groovy sources, which no guardrail can read (M7 Q13, Q14). Build scripts end in
 * `.gradle` or `.gradle.kts`, not `.groovy`, so they stay editable.
 */
internal object GradleScope {
    fun isEditable(file: String): Boolean {
        val wrapper = file == "gradlew" || file == "gradlew.bat" || file.startsWith("gradle/wrapper/")
        val cache = ".gradle" in file.split('/').dropLast(1)
        return JvmScope.isEditable(file) && !wrapper && !cache && !file.endsWith(".groovy")
    }

    /**
     * Every file Gradle reads its configuration from: build and settings scripts, `gradle.properties`, everything under
     * `gradle/` (the version catalog, the wrapper, the daemon JVM), the wrapper script, and the build logic in `buildSrc`
     * and `build-logic`.
     */
    fun isBuildFile(file: String): Boolean {
        val name = file.substringAfterLast('/')
        return name.endsWith(".gradle") ||
            name.endsWith(".gradle.kts") ||
            name == "gradle.properties" ||
            file == "gradlew" ||
            file == "gradlew.bat" ||
            listOf("gradle/", "buildSrc/", "build-logic/").any { file.startsWith(it) }
    }
}

/** The Gradle build-file blocklist: configuration that disables, skips, or filters tests. */
internal object GradleTestSkipping {
    private val disabled = Regex("""\b(?:(?:is)?[eE]nabled\s*=\s*false|enabled\s+false|setEnabled\s*\(\s*false\s*\))""")
    private val onlyIf = Regex("""\bonlyIf\b""")
    private val ignoreFailures = Regex("""\bignoreFailures\b""")
    private val filter = Regex("""\b(?:exclude|include)(?:TestsMatching|Tags|Engines)\b""")

    // `exclude '**/SlowTest.class'`: a file pattern, on a Test task or a source set. A dependency exclude names a group
    // or a module instead.
    private val patternExclude = Regex("""\bexclude\s*\(?\s*["']""")

    /** Why [line] (1-based) of the build file [content] disables tests, or null. */
    fun reason(
        content: String,
        line: Int,
    ): String? {
        val text = content.lines()[line - 1]
        val disabledBy = disabled.find(text)?.value
        val filteredBy = filter.find(text)?.value
        return when {
            disabledBy != null -> "`$disabledBy` disables a task, so it can disable tests"
            onlyIf.containsMatchIn(text) -> "`onlyIf` makes a task conditional, so it can skip tests"
            ignoreFailures.containsMatchIn(text) -> "`ignoreFailures` ignores test failures"
            filteredBy != null -> "`$filteredBy` leaves tests out"
            patternExclude.containsMatchIn(text) -> "`exclude` with a file pattern leaves tests or sources out"
            else -> null
        }
    }
}
