package gmbh.vanella.lector.ecosystems.jvm

/**
 * The JVM editable scope: everything except build output (`target/` and `build/`, outside `src/`) and generated code
 * (any directory named `generated…`).
 */
internal object JvmScope {
    fun isEditable(file: String): Boolean {
        val dirs = file.split('/').dropLast(1)
        val outsideSources = dirs.takeWhile { it != "src" }
        return dirs.none { it.startsWith("generated") } && outsideSources.none { it == "target" || it == "build" }
    }
}

/** The Maven build-file blocklist: configuration that disables tests. */
internal object MavenTestSkipping {
    // Properties and parameters that skip tests or ignore their failures, wherever they are set.
    private val skipping = Regex("""skipTests|skipITs|maven\.test\.skip|testFailureIgnore|maven\.test\.failure\.ignore""")

    // Parameters that leave tests out or run only some, in the configuration of surefire or failsafe.
    private val leavingOut = Regex("""<(exclude\w*|include\w*|skip\w*|test|groups)>""")
    private val testPlugin = Regex("""<artifactId>\s*(maven-(?:surefire|failsafe)-plugin)\s*</artifactId>""")

    /** Why [line] (1-based) of the build file [content] disables tests, or null. */
    fun reason(
        content: String,
        line: Int,
    ): String? {
        val lines = content.lines()
        val text = lines[line - 1]
        val skip = skipping.find(text)?.value
        val element = leavingOut.find(text)?.value
        return when {
            skip != null -> "`$skip` skips tests or ignores their failures"
            element != null -> testPlugin(lines, line)?.let { "`$element` in $it leaves tests out" }
            else -> null
        }
    }

    /** The test plugin whose `<plugin>` element holds [line], or null. Its children may come in any order. */
    private fun testPlugin(
        lines: List<String>,
        line: Int,
    ): String? {
        val start = lines.take(line).indexOfLast { "<plugin>" in it }
        val end = lines.drop(line - 1).indexOfFirst { "</plugin>" in it }
        if (start < 0 || end < 0) return null
        return testPlugin.find(lines.subList(start, line + end).joinToString("\n"))?.groupValues[1]
    }
}
