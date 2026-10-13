package gmbh.vanella.lector.ecosystems.typescript

// Directories of installed packages, the package managers' state, build output, and coverage.
private val GENERATED_DIRS = setOf("node_modules", ".yarn", "dist", "build", "out", "coverage", ".next", "generated", "__generated__")

// The lockfiles and the registry configuration: the package manager's, not the model's to edit (spec section 8).
private val PACKAGE_MANAGER_FILES =
    setOf("package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.yaml", "yarn.lock", ".npmrc", ".yarnrc", ".yarnrc.yml")

// Generated sources by name: `client.generated.ts`, `schema.gen.ts`, and Yarn's `.pnp.cjs` and `.pnp.loader.mjs`.
private val GENERATED_FILE = Regex("""^(?:.*\.generated\..*|.*\.gen\.ts|\.pnp\..*)$""")

// The configuration of the type check and of the test runners: `tsconfig.json`, `tsconfig.build.json`, `jest.config.ts`,
// `vitest.config.mts`, and Vite's config, which Vitest reads its `test` options from.
private val CONFIG_FILE =
    Regex("""^(?:tsconfig.*\.json|(?:jest|vitest|vite|vitest\.workspace)\.config\.[cm]?[jt]s|vitest\.workspace\.[cm]?[jt]s)$""")
private val TEST_CONFIG = Regex("""^(?:(?:jest|vitest|vite|vitest\.workspace)\.config\.[cm]?[jt]s|vitest\.workspace\.[cm]?[jt]s)$""")

/**
 * The TypeScript editable scope and build files (spec section 8). Every source is editable, `.js` and `.mjs` included,
 * and so are the build files, so that real fixes are possible. Never installed packages, build output, generated code,
 * lockfiles, or registry configuration.
 */
internal object TypeScriptScope {
    fun isEditable(file: String): Boolean {
        val parts = file.split('/')
        return parts.dropLast(1).none { it in GENERATED_DIRS } && parts.last() !in PACKAGE_MANAGER_FILES &&
            !GENERATED_FILE.matches(parts.last())
    }

    /** Every `package.json`, `tsconfig*.json`, `pnpm-workspace.yaml`, and the Jest, Vitest, and Vite configuration. */
    fun isBuildFile(file: String): Boolean {
        val name = file.substringAfterLast('/')
        return name == PACKAGE_JSON || name == "pnpm-workspace.yaml" || CONFIG_FILE.matches(name)
    }
}

// In tsconfig*.json: options that loosen the type check.
private val LOOSENED = Regex(""""((?:strict|noImplicit)\w*)"\s*:\s*false""")
private val SKIP_LIB_CHECK = Regex(""""skipLibCheck"\s*:\s*true""")
private val NO_CHECK = Regex(""""noCheck"\s*:""")
private val CHECK_JS = Regex(""""checkJs"\s*:\s*false""")

// In package.json: the `test` script.
private val TEST_SCRIPT = Regex("""^\s*"test"\s*:""")

// In package.json and the test configuration: options that leave tests out, quoted (JSON) or not (JavaScript).
private val LEAVING_OUT = Regex("""\b(testPathIgnorePatterns|modulePathIgnorePatterns|passWithNoTests)\b""")
private val EXCLUDE = Regex("""["']?exclude["']?\s*:""")

/** The TypeScript build-file blocklist (spec section 8): what loosens the type check or leaves tests out. */
internal object TypeScriptTestSkipping {
    /** Why [line] (1-based) of the build file [file] with [content] loosens the type check or leaves tests out, or null. */
    fun reason(
        file: String,
        content: String,
        line: Int,
    ): String? {
        val name = file.substringAfterLast('/')
        val text = content.lines()[line - 1]
        return when {
            name.startsWith("tsconfig") -> typeCheck(text) ?: exclude(content, line, "leaves files out of the type check")
            name == PACKAGE_JSON && TEST_SCRIPT.containsMatchIn(text) -> "a change to the `test` script changes which tests run"
            name == PACKAGE_JSON || TEST_CONFIG.matches(name) -> leavingOut(text) ?: exclude(content, line, "leaves tests out")
            else -> null
        }
    }

    private fun typeCheck(text: String): String? {
        val loosened = LOOSENED.find(text)
        return when {
            loosened != null -> "`${loosened.groupValues[1]}: false` loosens the type check"
            SKIP_LIB_CHECK.containsMatchIn(text) -> "`skipLibCheck: true` skips the type check of declaration files"
            NO_CHECK.containsMatchIn(text) -> "`noCheck` turns the type check off"
            CHECK_JS.containsMatchIn(text) -> "`checkJs: false` turns the type check of JavaScript off"
            else -> null
        }
    }

    private fun leavingOut(text: String): String? {
        val option = LEAVING_OUT.find(text) ?: return null
        return "`${option.value}` leaves tests out"
    }

    /** Why [line] adds to an `exclude` list: it has the key, or it is inside the list the nearest key above it opens. */
    private fun exclude(
        content: String,
        line: Int,
        effect: String,
    ): String? {
        val lines = content.lines()
        val opened = (line - 1 downTo 0).firstOrNull { EXCLUDE.containsMatchIn(lines[it]) }
        val between = if (opened == null) "" else lines.subList(opened, line - 1).joinToString("\n")
        val inside = opened == line - 1 || between.count { it == '[' } > between.count { it == ']' }
        return if (inside) "a new `exclude` entry $effect" else null
    }
}
