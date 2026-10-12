# Review – M4 Guardrails

Scope: commits `7855f28..b6a14fa` against [`spec.md`](spec.md).
Method: read every source, test, query, and build file in the range; ran `./gradlew clean build --rerun-tasks
--no-build-cache`; ran the installed start script on bad `--max-changed-lines` values; probed the rules with a temporary
test on real Java, Kotlin, POM, and Maven config edits (reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 30 of 30 acceptance criteria are met; AC19, AC21, and AC22 only with caveats (F1, F2, F3). AC12 was rewritten
  after the review (F5). Loading tree-sitter on
  Linux can't be verified until CI runs the branch.
- The main rule gap: stubbing and suppression reject a placeholder that was already there when the line around it
  changes, so a signature fix of an empty no-op override can never pass (F1).
- The main hole: the build-file blocklist only sees `pom.xml`; `-DskipTests` in `.mvn/maven.config` or `mvnw` passes
  every guardrail (F2).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit `0`; 368 unit tests, 16 end-to-end tests |
| AC2 | Met | `EcosystemAdapter.languageSupport` (`EcosystemAdapter.kt:19`); `SourceFacts` in `LanguageSupport.kt:32` |
| AC3 | Met | `grep -rniE "treesitter\|tree-sitter\|kotlin\.test\|junit\|@Test\|skipTests" core/src/main core/build.gradle.kts`: no match |
| AC4 | Met | `libs.versions.toml:14-16`, `ecosystems/jvm/build.gradle.kts:11-13` |
| AC5 | Met | `queries/java/*.scm`, `queries/kotlin/*.scm` (7 each) |
| AC6 | Met | `Guardrails.kt:16-28`, rules in `FileRules.kt` and `SourceRules.kt` |
| AC7 | Met | `FixLoopTest`: "an attempt that breaks a guardrail is reset, counted, never verified…" (`verifyCalls == 0`) |
| AC8 | Met | Same test: `NO_PROGRESS`, clean `git status`, violation in `previousAttempts` |
| AC9 | Met | `RecipeStepTest`: "a recipe that deletes a method is kept…" |
| AC10 | Met | `FileRulesTest` (target, generated, untracked, outside); `FixLoopTest` with a real `.gitignore` |
| AC11 | Met | `FileRulesTest` 50/51; `HealCommandTest` `--max-changed-lines`; start script rejects `abc`, `0`, extra args with exit `2`, empty stdout |
| AC12 | Met | `JvmGuardrailsTest`, Java and Kotlin |
| AC13 | Met | `JvmGuardrailsTest`, "test deletion and test count reject a deleted test, even one a diagnostic names" |
| AC14 | Met | `JvmGuardrailsTest`, `@Disabled` (Java), `@Ignore` (Kotlin) |
| AC15 | Met | `JvmGuardrailsTest`, Java and Kotlin |
| AC16 | Met | `JvmGuardrailsTest`, Java and Kotlin, with and without a compile error |
| AC17 | Met | `JvmGuardrailsTest`, `SourceRulesTest` |
| AC18 | Met | `JvmGuardrailsTest`, "test count rejects a test that is no test anymore" |
| AC19 | Met, with caveats | `JvmGuardrailsTest` rejects all three; but existing placeholders in changed lines are rejected too (F1) |
| AC20 | Met | `JvmGuardrailsTest`, "stubbing lets an existing return null in an unchanged line pass" |
| AC21 | Met, with caveats | `JvmGuardrailsTest`; existing suppressions in changed lines are rejected too (F1) |
| AC22 | Met, with caveats | `MavenGuardrailsTest` covers each item in `pom.xml`; other Maven config files and some POM shapes get through (F2, F3, F4) |
| AC23 | Met | Blocklist in `MavenGuardrails.kt`; no `skipTests` in `core/src/main` |
| AC24 | Met | `JvmGuardrailsTest`, Java and Kotlin |
| AC25 | Met | `HealCommandTest`, `FixLoopReportTest`, `LectorEndToEndTest` through the start script |
| AC26 | Met | `HealCommandTest`: 5 tests before, 3 after, exit `0` |
| AC27 | Met | One rejecting test per rule in `JvmGuardrailsTest`, `FileRulesTest`, `SourceRulesTest` |
| AC28 | Met | `JvmLanguageSupportTest` on `fixtures/language-facts/` |
| AC29 | Met | No `LlmClient` in `Guardrail*.kt`, `FileRules.kt`, `SourceRules.kt` |
| AC30 | Met | Catalog: only `tree-sitter-java`, `tree-sitter-kotlin` |

## Findings

### Guardrail rules

**F1 · High · Stubbing and suppression reject placeholders that were already there, not only new ones.** `Stubbing`
and `Suppression` (`SourceRules.kt:137-159`) flag every fact in a touched line. Probes, each rejected:
`public void init(javax.FilterConfig c) {}` → `jakarta.FilterConfig` (`[stubbing] {}`), the same for a Kotlin
`override fun on(e: OldEvent) {}`, `old(); // TODO remove later` → `renamed(); // TODO remove later`, and
`@Suppress("DEPRECATION") fun run(x: Old)` → `New`. The spec says "a **new** `return null`, empty method body, or TODO"
(section 3) and Q5 "existing ones aren't our business". Empty no-op overrides (`Filter.init`, `destroy`, listener
callbacks) are exactly what major upgrades change the signature of, so the model can't fix them at all. Expected: a
placeholder in a touched line is rejected only if the lines the attempt replaced didn't hold the same one (by
normalized text, counted).

Outcome: **Fixed.** `FileChange.replaced` marks the removed side of each hunk; `Stubbing` and `Suppression` drop a placeholder that the replaced lines already held, counted by normalized text. Java and Kotlin tests for the four probes.

**F2 · High · The build-file blocklist only sees `pom.xml`, so `-DskipTests` in `.mvn/maven.config` or `mvnw` passes.**
`MavenAdapter.isBuildFile` (`MavenAdapter.kt:128`) is `pom.xml` only, and `JvmScope` lets the model edit `.mvn/` and
`mvnw`. Probe: adding `-DskipTests` to `.mvn/maven.config`, or to the `mvn` call in `mvnw`: no violation. Maven reads
both on every verify, so the tests silently don't run, and the static test count doesn't notice. The same files can
also set version properties (`-Dspring.version=…`) without triggering the downgrade check, which only runs for build
files. Expected: `mvnw`, `mvnw.cmd`, and files under `.mvn/` are build files.

Outcome: **Fixed.** `isBuildFile` covers `mvnw`, `mvnw.cmd`, and `.mvn/…` at the root, so the blocklist and the downgrade check see them; `JvmGuardrailsTest` rejects `-DskipTests` in `.mvn/maven.config`.

**F3 · Medium · A POM line's plugin is the nearest `<artifactId>` above it, so a dependency inside the plugin hides
its configuration.** `MavenTestSkipping.reason` (`MavenGuardrails.kt:34`). Probe: surefire with a
`<dependencies>` block (the usual `surefire-junit-platform` provider) before `<configuration>`, then a new
`<excludes><exclude>**/*</exclude></excludes>`: no violation. An `<artifactId>` written after `<configuration>` (legal
in a POM) has the same effect. Expected: the plugin of a line is the `<plugin>` element around it.

Outcome: **Fixed.** The plugin is now the `<plugin>…</plugin>` block around the line, whatever order its children have; a plugin that never closes is none.

**F4 · Medium · The blocklist misses surefire and failsafe parameters that leave tests out other than `exclude` and
`skip`.** Probe: a new `<includes><include>None</include></includes>` or `<test>None</test>` in surefire passes;
so would `<groups>`, `<skipExec>`, `<skipAfterFailureCount>`. Each runs fewer tests than before. The spec's goal is
"disables tests in the build"; its list names the common cases. Expected: in surefire or failsafe, also `include…`,
`test`, `groups`, and every `skip…`.

Outcome: **Fixed.** The surefire/failsafe pattern is `exclude…`, `include…`, `skip…`, `test`, `groups`.

**F5 · Low · Symbol comparison lets a method go when any diagnostic in any file names it.** `SymbolComparison`
(`SourceRules.kt:39`) checks every diagnostic caused by the update. A common name (`get`, `run`, `close`) in an
unrelated error elsewhere allows deleting every method of that name. The spec's wording ("its name appears in a current
diagnostic") allows this; the legitimate case it is for (an `@Override` of a removed method) is always reported in the
method's own file.

Outcome: **Fixed, with a spec change (decided in chat).** A diagnostic shows a method has to change, not that its
logic may go: deleting `configure(HttpSecurity)` in a Spring Security 6 upgrade compiles and drops every access rule.
Also, javac's "method does not override or implement a method from a supertype" names no method, so the Java case the
exception was for never passed. Now a method may go only if a current diagnostic in its file points into its lines and
its body was empty or comes back in a method the attempt added (rename, move). `Method` carries its `body`; spec Q3,
AC12, concept section 7, and features.md are updated. A rename or move that also adapts the body is rejected and left
to a human; M6's rejections per rule show whether that blocks too much.

### Robustness and contracts

**F6 · Medium · A failure other than `ToolError` during an attempt leaves the edits in the working tree, and an `Error`
exits with `1`.** `FixLoop.attempt` (`FixLoop.kt:134-140`) resets only on `ToolError`, but M4 runs tree-sitter after
the edits are written: a native library that doesn't load (`UnsatisfiedLinkError`), a broken query, or any bug leaves
them behind. `RepositoryCommand.run` catches `Exception`, so an `Error` escapes `main`, and the JVM exits with `1`
(checked: an uncaught `UnsatisfiedLinkError` in `main` exits `1`), which the pipeline reads as "the update caused a
problem". AGENTS.md: reset on every way out, exceptions included; any failure that isn't the update's exits `2`.
Expected: the loop resets on any `Throwable` and rethrows; the command maps any `Throwable` to exit `2`.

Outcome: **Fixed.** Both catch `Throwable` (rethrown after the reset; turned into exit `2` in the command), with tests that throw an `Error`.

**F7 · Low · The resolved versions are read before the scope check.** `FixLoop.apply` (`FixLoop.kt:186-188`) runs
Maven's effective POM for an edit to any `…/pom.xml` before `write` checks the scope. An edit to `target/pom.xml` costs
a Maven run, and if that run fails, the attempt is rejected as "versions can't be read" instead of a scope violation,
so the report's count per rule misses it. Expected: check the scope first.

Outcome: **Fixed.** The scope check moved to the start of `apply`; in `FixLoopTest`, a POM edit next to an untracked file is a scope violation even though the versions can't be read.

### Leftovers

**F8 · Low · The catalog comment says "native libraries for Linux and macOS", but they are for glibc only.** The JARs
ship `x86_64`/`aarch64-linux-gnu`, macOS, and Windows libraries; an Alpine (musl) image can't load them. M5 builds the
container image.

Outcome: **Fixed.** The comment names glibc, so M5's image choice sees it.

## Found while fixing

None. The fix round ran no new process and left no stray files; every clean build passed on the first run.

## What's good

- The rules are generic and small; all language knowledge is in query files. A new language really is a grammar and
  queries.
- Scope runs on the paths before anything is written, because a reset doesn't restore ignored files. Keep that order.
- Violations are data (`rule`, `file`, `line`, `reason`) end to end: retry prompt, text report, JSON, count per rule.
- `lost(...)` counts by key, so overloads, moved skips, and re-indented assertions behave right without special cases.
- The line diff has a size cap, so a huge rewrite can't blow up memory; it is one hunk and fails diff size anyway.
- The end-to-end test parses real Java with the bundled grammar through the start script and asserts that stderr has no
  JNI warning.
