# Review – M7 Gradle

Scope: commits `8928500..6f60b67` against [`spec.md`](spec.md).
Method: read every source, test, build, fixture, and doc file in the diff against `origin/main`; ran
`./gradlew clean build --rerun-tasks --no-build-cache`; ran the installed start script on a Gradle fixture with real
Gradle 8.14.2 (happy path and bad input); ran the init script's query task by hand on the multi-project fixture with
Gradle 9.8.1; probed the lint gates with temporary changes (all reverted, `git status` clean).

Outcome per finding: **Fixed** · **Not changed** (with reason) · **Needs decision** (with options).

Severity: **High** = breaks a requirement or the external contract · **Medium** = real gap, should fix before the next milestone · **Low** = cleanup / nit.

## Summary

- 43 of 46 acceptance criteria are met. AC45 can't be verified from the repo (needs the Gradle test repository on
  GitHub). AC34 and AC33 are met only with caveats (F1, F3).
- The main gap: the test-skipping blocklist has easy routes around it (`setOnlyIf`, `include` patterns, `excludes`, an
  `exclude` closure) (F1), and a Gradle build can't get credentials from the environment, which the spec promises for
  mirrors (F2).
- The main gate gap: unused imports pass every gate; M7 added 38 of them (F5).

## Acceptance criteria

| ID | Result | Evidence |
|---|---|---|
| AC1 | Met | `./gradlew clean build --rerun-tasks --no-build-cache`: exit `0`, 160 tasks executed |
| AC2 | Met | `GradleAdapter.kt` next to `MavenAdapter.kt`; `adapters()` in `RootCommand.kt` returns Gradle, then Maven |
| AC3 | Met | `settings.gradle.kts` includes only `ecosystems:jvm` |
| AC4 | Met | `core/build.gradle.kts` has no `project(`; `grep -ri gradle core/src/main`: no match |
| AC5 | Met | `GradleAdapterTest` "detects a repository with any of the four root build files", "doesn't detect …" |
| AC6 | Met | start script: `verify` on the fixture plus `pom.xml` → exit `2`, stdout empty, stderr names both build tools |
| AC7 | Met | start script without `gradlew` → exit `2`, stderr "has no Gradle wrapper (gradlew)" |
| AC8 | Met | start script on Gradle 7.6.4 → exit `2`, stderr "supports Gradle 8 and newer"; `GradleAdapterTest` |
| AC9 | Met | `GradleAdapterTest` "every Gradle run uses the wrapper, our init script, and the plain console" |
| AC10 | Met | `GradleAdapterTest` "the user's init script is passed to every run as a second init script" |
| AC11 | Met | `GradleAdapterTest` "verify reads the model, …"; `GradleModelTest` recipe run |
| AC12 | Met | `GradleAdapterTest`: 8.0 → 17, 8.5 → 21, 9.1.0 → 25, the properties file wins; table matches Gradle's matrix |
| AC13 | Met | `GradleAdapterTest` "toolchains come from the installed JDKs only …"; real run's command line in the log |
| AC14 | Met | `GradleAdapterTest` on captured output; `GradleIntegrationTest` with real Gradle |
| AC15 | Met | `GradleIntegrationTest` `--status` after close; start-script run: no daemon of its Gradle user home left |
| AC16 | Met | `GradleIntegrationTest` on `gradle-kotlin-catalog` (doesn't compile); by hand: query on the multi-project fixture with Gradle 9.8.1 |
| AC17 | Met | `GradleIntegrationTest`: grounding reads `StringUtils` from the classpath JAR |
| AC18 | Met | `GradleModelTest` "an Android or Kotlin Multiplatform project is a tool error" |
| AC19 | Met | `GradleIntegrationTest` "stage 1 compiles every source set, the integration tests too …" |
| AC20 | Met | `GradleAdapterTest` "doesn't run the tests when the code doesn't compile" |
| AC21 | Met | `GradleAdapterTest` (no `check`/`build`), `GradleIntegrationTest` (the `lint` task bound to `check` never runs) |
| AC22 | Met | `GradleAdapterTest` "runs only the given tests, each in the Test task it came from" |
| AC23 | Met | `GradleConsoleTest` on captured kotlinc output |
| AC24 | Met | `GradleConsoleTest` on a captured Groovy DSL failure |
| AC25 | Met | `GradleConsoleTest`, `GradleIntegrationTest` (`app/src/integrationTest/…` paths) |
| AC26 | Met | `cli` `GradleIntegrationTest` (opt-in, reported green in `progress.md`; not rerun in this review) |
| AC27 | Met | `rewrite-gradle-plugin = "7.39.0"` in `gradle/libs.versions.toml` |
| AC28 | Met | Dockerfile: `LECTOR_REWRITE_GRADLE_PLUGIN=/opt/lector/rewrite-gradle-plugin`; the distribution holds `plugin-7.39.0.jar`, `kotlin-stdlib`, `annotations` only |
| AC29 | Met | `cli` `GradleIntegrationTest` (opt-in, reported green in `progress.md`) |
| AC30 | Met | same test inspects the changed files |
| AC31 | Met | `GeneratedIndexTest` |
| AC32 | Met | `GeneratedIndexTest` |
| AC33 | Met, with caveats | `GradleGuardrailsTest`; included builds other than `buildSrc` and `build-logic` aren't build files (F3) |
| AC34 | Met, with caveats | one `GradleGuardrailsTest` per listed entry; other spellings of the same rules get through (F1) |
| AC35 | Met | `GradleGuardrailsTest` |
| AC36 | Met | `GradleGuardrailsTest` |
| AC37 | Met | `RenovateSourceTest` on captured Gradle PR bodies |
| AC38 | Met | `RenovateSourceTest`, `PrCommandTest` "a PR that only updates the Gradle wrapper is skipped with exit 0" |
| AC39 | Met | `--help` of `verify`, `migrate`, `heal`, `pr` lists `--gradle-init`; missing file → exit `2`, stderr "Gradle init script … is not a file" |
| AC40 | Met | `action/action.yml` input `gradle-init` → `LECTOR_GRADLE_INIT` |
| AC41 | Met | `docs/setup.md` section 3 and Limitations |
| AC42 | Met | `gradle-groovy`, `gradle-kotlin-catalog`, `gradle-multi-project` |
| AC43 | Met | `test` excludes the tag in `ecosystems:jvm` and `cli`; `build --dry-run` lists no `integrationTest` |
| AC44 | Met | `docs/licensing.md` |
| AC45 | Can't verify | needs a real Renovate PR in a Gradle test repository on GitHub |
| AC46 | Met | version catalog and module build files |

## Findings

### Guardrails

**F1 · Medium · The test-skipping blocklist has routes around it.** `GradleGuardrails.kt:34-40` matches `onlyIf`,
`exclude…`/`include…` with `TestsMatching|Tags|Engines`, and `exclude` followed by a quote. These lines disable or
filter tests and pass: `setOnlyIf { false }` (no word boundary before `onlyIf`), `include '**/OnlyThis*'`,
`excludes = ['**/SlowTest*']`, `exclude { it.name.contains('Slow') }`, `exclude(listOf("**/SlowTest*"))`,
`filter { setExcludePatterns(['*Slow*']) }`, `excludeCategories`/`excludeGroups` (JUnit 4, TestNG). The Maven blocklist
matches every `exclude\w*`/`include\w*`/`skip\w*` element in the test plugins, so Gradle is the weaker one. Consequence:
a model can make a test failure go away by filtering the test out, which is the one thing M4's guardrails exist to stop.
Expected: match `setOnlyIf`, the `…Patterns`, `…Categories`, `…Groups` filters, and every `include`/`exclude`/`includes`/`excludes`
except a dependency exclude (`exclude group: …`, `exclude(group = …)`, `exclude(module = …)`), which stays a legitimate fix.
A settings file's `include(":lib")` is a project, not a filter.

Outcome: **Fixed.** The blocklist matches `setOnlyIf`, the `…Patterns`/`…Categories`/`…Groups` filters, and every
`include`/`exclude`/`includes`/`excludes` except a dependency exclude and a settings file's projects; one test per route
(`1f5c49b`).

**F3 · Low · Included builds other than `buildSrc` and `build-logic` aren't build files.** `GradleGuardrails.kt:27`.
Spec section 7 says "the sources of `buildSrc` and of included build-logic builds". An included build can be called
anything (`includeBuild("conventions")`); its `.kt` convention plugins are then source files: the scope rule lets the
model edit them and the blocklist doesn't read them, so `tasks.withType<Test> { enabled = false }` in one gets through.
Precompiled script plugins (`*.gradle.kts`) are covered by their suffix.

Outcome: **Fixed** (decided in chat: option A, the query reports the included builds). The query task writes the
included builds inside the repository, and `isBuildFile` treats their files as build files; unit test, and the
multi-project fixture got an included build `conventions` for the integration test (`2dc0a91`).

### Configuration

**F2 · Medium · A Gradle build can't read credentials from the environment.** `runProcess` passes only allowlisted
variables (`Processes.kt:50`: `LC_`, `MAVEN_`), and the Gradle adapter adds only `JAVA_HOME` and `GRADLE_USER_HOME`
(`GradleAdapter.kt:294`). Maven gets `MAVEN_*` for the `${env.…}` credentials in a settings file (setup guide, the
`maven-settings` row). For Gradle, neither `ORG_GRADLE_PROJECT_<name>` (Gradle's own way to pass a property from the
environment) nor any other variable reaches the build or the `--gradle-init` script. Consequence: a mirror that needs a
password, the regulated case spec section 2 names (Q6: "an init script can set repository mirrors and credentials"), only
works with the password written into the committed init script. Expected: the Gradle adapter passes the tool's
`GRADLE_*` and `ORG_GRADLE_PROJECT_*` variables to Gradle (not `core`: AC4), and the setup guide's `gradle-init` row says
so, as the `maven-settings` row does.

Outcome: **Fixed.** The Gradle adapter takes the tool's environment and passes `GRADLE_*` and `ORG_GRADLE_PROJECT_*`
(replacing the `userHome` parameter); the setup guide's `gradle-init` row names them (`9022bb7`).

**F4 · Low · Reports outside `<project dir>/build/test-results/` aren't found.** `TestReports.kt:22` derives the task
path from the directory, "as in Gradle's default layout". A project with another build directory, or a `projectDir`
that doesn't follow its path, has its reports missed or its task misnamed. A failing test then shows up as an
unexplained Gradle failure, a build problem that is always attributed to the update. Rare; the fix (the query task
reports each `Test` task's path and JUnit XML directory) is a bigger change than the case is worth now.

Outcome: **Not changed.** Recorded as a limitation in the setup guide. Revisit when a real repository has another
build directory: then the query task reports each `Test` task's path and JUnit XML directory.

### Quality gates

**F5 · Medium · Unused imports pass every gate.** ktlint 1.8 has no unused-import rule any more, and detekt's
`UnusedImport` is inactive by default. Probe: with `style>UnusedImport: active: true`, detekt finds 39 unused imports,
38 of them from M7: `GradleAdapter.kt:24` (`writeText`), 20 in `FakeGradleTest.kt` (the imports of
`GradleAdapterTest` copied whole), 8 in `GradleAdapterTest.kt`, 8 in `GradleModelTest.kt`, 1 in `VerifyCommandTest.kt`,
plus 1 older one in `GitHubHostTest.kt`. Consequence: dead imports pile up and hide which test needs what. Expected:
activate `UnusedImport` in `config/detekt/detekt.yml` and remove the 39.

Outcome: **Fixed.** `UnusedImport` is active in `config/detekt/detekt.yml`, the 39 imports are gone, and a probe import
fails `detektTest` (`057e9e7`).

### Tests

**F6 · Low · "a daemon that doesn't stop is logged, not a failure" asserts nothing.** `GradleAdapterTest.kt:369`. It
passes as long as `close()` doesn't throw, so it would also pass if `close()` never tried to stop the daemon. Expected:
assert that each `close()` ran `--stop` once.

Outcome: **Fixed.** The test asserts that each `close()` ran `--stop` with its one-minute timeout; it fails when
`close()` skips the stop (`0cf2541`).

## Found while fixing

Nothing new. `:ecosystems:jvm:integrationTest` (5) and `:cli:integrationTest` (4) pass with real Gradle 8.14.2 and
9.8.1 after the fixes.

## What's good

- The adapter boundary held: no change to `core`, the second build tool is a second adapter in the same module, and the
  shared parts (javac parser, JUnit XML, `javap`, cluster keys) were generalized instead of copied (`TestReports` with a
  layout, `JavapGrounding` with a JAR source).
- Verify never runs `check`, and the integration test proves it with a failing `lint` task bound to `check`.
- The query task works on a build that doesn't compile and on Gradle 8 and 9, and `readModel` keeps verify going when
  the model can't be read.
- The daemon is stopped through `List<EcosystemAdapter>.use`, also after `Error`s, and the integration tests use their
  own Gradle user home so `--stop` can't kill the build's own daemon.
- The plugin/BOM version check (`checkRewriteGradlePluginVersion`) keeps the shipped plugin on the BOM's OpenRewrite,
  the same way as the Maven plugin.
- Every tool error was checked through the start script: exit `2`, empty stdout, one clear line on stderr.
