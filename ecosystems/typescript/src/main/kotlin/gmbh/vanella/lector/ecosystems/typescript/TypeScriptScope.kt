package gmbh.vanella.lector.ecosystems.typescript

// Directories of installed packages, the package managers' state, build output, and coverage.
private val GENERATED_DIRS = setOf("node_modules", ".yarn", "dist", "build", "out", "coverage", ".next", "generated", "__generated__")

// The lockfiles and the registry configuration: the package manager's, not the model's to edit (spec section 8).
private val PACKAGE_MANAGER_FILES =
    setOf("package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.yaml", "yarn.lock", ".npmrc", ".yarnrc", ".yarnrc.yml")

// Generated sources by name: `client.generated.ts`, `schema.gen.ts`, and Yarn's `.pnp.cjs` and `.pnp.loader.mjs`.
private val GENERATED_FILE = Regex("""^(?:.*\.generated\..*|.*\.gen\.ts|\.pnp\..*)$""")

// The test runners' configuration: `jest.config.ts`, `vitest.config.mts`, a variant a script names with `--config`
// (`jest.unit.config.js`, `vitest.e2e.config.ts`), Vite's config, which Vitest reads its `test` options from, the
// workspace file, Jest presets, and Jest's JSON configs (`jest-e2e.json`).
private val TEST_CONFIG =
    Regex(
        """^(?:(?:jest|vitest|vite)(?:[.-][\w-]+)*\.config\.(?:[cm]?[jt]s|json)|vitest\.workspace\.(?:[cm]?[jt]s|json)|""" +
            """jest\.preset\.[cm]?js|jest[.-][\w.-]*\.json)$""",
    )

// What the package managers read besides package.json: the type check's config, the pnpm workspace, and pnpm's hooks,
// which change what an install resolves.
private val BUILD_FILE = Regex("""^(?:package\.json|tsconfig.*\.json|pnpm-workspace\.yaml|\.pnpmfile\.[cm]?js)$""")

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

    /** Every `package.json`, `tsconfig*.json`, `pnpm-workspace.yaml`, `.pnpmfile.cjs`, and the test runners' configuration. */
    fun isBuildFile(file: String): Boolean {
        val name = file.substringAfterLast('/')
        return BUILD_FILE.matches(name) || TEST_CONFIG.matches(name)
    }
}

// In tsconfig*.json: options that loosen the type check.
private val LOOSENED = Regex(""""((?:strict|noImplicit)\w*)"\s*:\s*false""")
private val SKIP_LIB_CHECK = Regex(""""skipLibCheck"\s*:\s*true""")
private val NO_CHECK = Regex(""""noCheck"\s*:""")
private val CHECK_JS = Regex(""""checkJs"\s*:\s*false""")

// In package.json: the `test` script, the scripts it runs through (`test:unit`), and its `pre` and `post` scripts.
private val TEST_SCRIPT = Regex(""""(?:pre|post)?test(?:[:._-][^"]*)?"\s*:""")

// In package.json and the test configuration, quoted (JSON) or not (JavaScript): options that leave tests out or
// narrow which run, on their key's line or as a new entry of the list they open; Vitest's `include` and `dir` only in
// its `test` options, Vite has its own.
private val LEAVING_OUT = listOf("testPathIgnorePatterns", "modulePathIgnorePatterns", "exclude")
private val NARROWING = listOf("testMatch", "testRegex", "roots", "testNamePattern")
private val VITEST_NARROWING = listOf("include", "dir")
private val PASS_WITH_NO_TESTS = Regex("""\bpassWithNoTests\b""")
private val TEST_BLOCK = key("test")

/** The TypeScript build-file blocklist (spec section 8): what loosens the type check or leaves tests out. */
internal object TypeScriptTestSkipping {
    /** Why [line] (1-based) of the build file [file] with [content] loosens the type check or leaves tests out, or null. */
    fun reason(
        file: String,
        content: String,
        line: Int,
    ): String? {
        val name = file.substringAfterLast('/')
        val lines = content.lines()
        val text = lines[line - 1]
        return when {
            name.startsWith("tsconfig") -> {
                typeCheck(text)
                    ?: option(lines, line - 1, listOf("exclude"))?.let { "a new `exclude` entry leaves files out of the type check" }
            }

            name == PACKAGE_JSON && TEST_SCRIPT.containsMatchIn(text) -> {
                "a change to a test script changes which tests run"
            }

            name == PACKAGE_JSON || TEST_CONFIG.matches(name) -> {
                testOptions(lines, line - 1)
            }

            else -> {
                null
            }
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

    private fun testOptions(
        lines: List<String>,
        index: Int,
    ): String? {
        if (PASS_WITH_NO_TESTS.containsMatchIn(lines[index])) return "`passWithNoTests` leaves tests out"
        option(lines, index, LEAVING_OUT)?.let { return "a new `$it` entry leaves tests out" }
        option(lines, index, NARROWING)?.let { return "`$it` changes which tests run" }
        val vitest = option(lines, index, VITEST_NARROWING) ?: return null
        val block = opener(lines, index, '{', '}')
        return if (block != null && TEST_BLOCK.containsMatchIn(lines[block])) "`$vitest` changes which tests run" else null
    }

    /** Which of [options] [index] sets: on the option's own line, or inside the list the option opened. Null if none. */
    private fun option(
        lines: List<String>,
        index: Int,
        options: List<String>,
    ): String? {
        val list = opener(lines, index, '[', ']')
        return options.firstOrNull { key(it).containsMatchIn(lines[index]) || (list != null && key(it).containsMatchIn(lines[list])) }
    }
}

/** The key [name] of a JSON or JavaScript object, quoted or not. */
private fun key(name: String) = Regex("""(?:^|[\s{,])["']?$name["']?\s*:""")

/** The line of the innermost [open] above [index] that is still open there, or null. Brackets in strings count too. */
private fun opener(
    lines: List<String>,
    index: Int,
    open: Char,
    close: Char,
): Int? {
    var depth = 0
    for (row in index - 1 downTo 0) {
        for (char in lines[row].reversed()) {
            if (char == close) depth++
            if (char == open && depth-- == 0) return row
        }
    }
    return null
}
