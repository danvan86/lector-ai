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

    // Elements that leave tests out, in the configuration of surefire or failsafe.
    private val leavingOut = Regex("""<(exclude\w*|skip)>""")
    private val artifactId = Regex("""<artifactId>\s*([^<\s]+)\s*</artifactId>""")
    private val testPlugin = Regex("""maven-(surefire|failsafe)-plugin""")

    /** Why [line] (1-based) of the POM [content] disables tests, or null. The plugin of a line is the nearest `artifactId` above it. */
    fun reason(
        content: String,
        line: Int,
    ): String? {
        val lines = content.lines()
        val text = lines[line - 1]
        val skip = skipping.find(text)?.value
        val element = leavingOut.find(text)?.value
        val plugin = lines.take(line - 1).asReversed().firstNotNullOfOrNull { artifactId.find(it)?.groupValues[1] }
        return when {
            skip != null -> "`$skip` skips tests or ignores their failures"
            element != null && plugin != null && testPlugin.matches(plugin) -> "`$element` in $plugin leaves tests out"
            else -> null
        }
    }
}
