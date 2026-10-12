# Progress – M7 Gradle

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Recipe index reads Gradle `UpgradePluginVersion` leaves under marker coordinates (`recipes`) | ✅ |
| 2 | Renovate: plugin rows to marker coordinates, Gradle wrapper rows skipped (`sources`) | ✅ |
| 3 | `GradleAdapter`: detection, tool errors, daemon JDK, command lines, build files, scope, blocklist | ✅ |
| 4 | Init script: query task, verify stages, `runTests`, Gradle output parsers, fixtures | ✅ |
| 5 | Grounding from the query, Android/KMP and toolchain errors, daemon stopped after every run | ✅ |
| 6 | Recipe runner: OpenRewrite Gradle plugin shipped with the tool, version check, image, licence | ✅ |
| 7 | CLI and action: adapter wiring, `--gradle-init`, `gradle-init` input, Gradle user home | ✅ |
| 8 | Tagged integration tests with real Gradle | ✅ |
| 9 | Quality gates verified | ✅ |
| 10 | Docs in sync | ✅ |

## Decisions during implementation

- **Q19, `rewriteRun` compiles first** (asked in chat, recorded in [`brainstorming.md`](brainstorming.md)): the plugin
  makes `rewriteRun` depend on every `compileJava` task. The recipe init script clears that dependency.
- **Plugin version 7.39.0**, not the newest 7.41.0: 7.39.0 is built on the BOM's OpenRewrite 8.89.0, and 7.40/7.41
  name a `rewrite-bom` 8.91.0 that Maven Central doesn't have. `:recipes:checkRewriteGradlePluginVersion` keeps the
  plugin and the BOM on one OpenRewrite version, like the Maven plugin's check.
- **The plugin ships as a directory of JARs** (`rewrite-gradle-plugin/` in the distribution: the plugin and the Kotlin
  standard library it needs), put on the init script's classpath, not as a Maven-layout repository. Same effect, no
  repository metadata to generate. It comes from the Gradle Plugin Portal, an exclusive repository for this one module.
- **Toolchain settings by Gradle version:** Gradle 9.1 reads them as `-D` Gradle properties and deprecates `-P`; Gradle
  8 reads only `-P` (checked with real Gradle 8.14.2).
- **Renovate packages from the badges:** real PR bodies show a plugin's ID or a version catalog alias in the table; the
  merge-confidence badge names the package (`maven` datasource, marker coordinates for plugins). A wrapper row is the
  `gradle-version` datasource, or, in a wrapper-only PR without badge columns, the package `gradle`. The spec speaks
  of a manager lookup; the source has always mapped datasources, and Gradle's are `maven`.
- **Gradle reports name the `Test` task:** a test failure's `tool` is the task path (`:app:integrationTest`), so
  `runTests` reruns it in that task. JUnit XML from Gradle names test methods with `()`.
- **Build-script errors keep their location:** Kotlin DSL errors come as `e: file:///…/build.gradle.kts:7:5`, so they
  become kotlinc diagnostics in the build script. Other failures are diagnostics without a location, with the
  `* Where:` line as part of the message.
- **Closing the adapters:** `cli` closes every `AutoCloseable` adapter after a run, also after a failure. `--stop` stops
  every daemon of a Gradle version in the Gradle user home, so the integration tests use a Gradle user home of their own
  (`build/gradle-test-home`): otherwise they would stop the daemon running the build.
- **Integration tests get JDKs 21 and 25** as `LECTOR_JDK_*` from Gradle's toolchains (downloaded if missing), the way
  the image provides them. The Maven integration tests now build on JDK 21 from that too.

## Quality gates

`./gradlew clean build --rerun-tasks --no-build-cache` exits `0` with 559 unit tests (core 175, ecosystems:jvm 189,
cli 106, recipes 37, sources 19, git 15, llm 18), 19 end-to-end tests, and 100 % lines and branches per module. The
opt-in integration tests pass: `:ecosystems:jvm:integrationTest` (5, real Gradle 8.14.2 and 9.8.1) and
`:cli:integrationTest` (4: the two Maven ones and two Gradle ones).

Probes, each failing on its own gate, then reverted:

| Gate | Probe | Failed with |
|---|---|---|
| ktlint | bad formatting in `ecosystems:jvm` main and test sources and in its `build.gradle.kts` | `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck` |
| ktlint, `buildSrc` | bad formatting in `buildSrc/src/main/kotlin/GradleFixtures.kt` | nothing at first: the root config linted only `buildSrc/**/*.kts`. Fixed (`**/*.kt` too); then `:ktlintKotlinScriptCheck` |
| detekt | `System.out.println` in `GradleConsole`, `println` in `GradleModelTest` | `ForbiddenMethodCall` in `detektMain` and `detektTest` |
| Kover | an untested function in `GradleWrapper.kt` | `:ecosystems:jvm:koverVerify` |
| Tests | a wrong assertion in `GradleConsoleTest` | `:ecosystems:jvm:test` |
| Plugin version | the Gradle plugin at 7.38.0 (OpenRewrite 8.88.0) | `:recipes:checkRewriteGradlePluginVersion` |

`./gradlew build --dry-run` lists every gate task of `ecosystems:jvm` once, `checkRewriteGradlePluginVersion`, and no
`integrationTest`.

By hand:

- `docker build -f docker/lector/Dockerfile .`: `lector --version` prints `0.1.0`; `LECTOR_REWRITE_GRADLE_PLUGIN` holds
  `plugin-7.39.0.jar`, `kotlin-stdlib-2.4.0.jar`, `annotations-13.0.jar`; no `gradle` and no Gradle distribution, no
  `rewrite-*.jar`; 1.63 GB.
- The image ran `verify --base HEAD~1` on the Gradle base/head fixture: Gradle 9.8.1 on JDK 25, the Java 21 toolchain
  from the image JDKs, one failure caused by the update and one pre-existing, exit `1`, and no Gradle daemon left.

## Acceptance criteria

| ID | Status | Evidence |
|---|---|---|
| AC1 | met | `./gradlew clean build --rerun-tasks --no-build-cache` exits `0` |
| AC2 | met | `GradleAdapter.kt` next to `MavenAdapter.kt`; `adapters()` in `RootCommand.kt` registers both, Gradle first |
| AC3 | met | `settings.gradle.kts` includes `ecosystems:jvm` only |
| AC4 | met | `core/build.gradle.kts` has no `project(`; `grep -ri gradle core/src/main` finds nothing |
| AC5 | met | `GradleAdapterTest`: the four root files, and none |
| AC6 | met | `GradleAdapterTest`, and `RootCommandTest` (exit `2`) |
| AC7 | met | `GradleAdapterTest` |
| AC8 | met | `GradleAdapterTest`: Gradle 7.6.4 |
| AC9 | met | `GradleAdapterTest`: every run starts with the wrapper, `-I` our script, `--console=plain` |
| AC10 | met | `GradleAdapterTest`: the user's script as the second `-I` on every run |
| AC11 | met | `GradleAdapterTest`: query and recipe with `--no-configuration-cache`, the stages and reruns without |
| AC12 | met | `GradleAdapterTest`: the properties file wins; 8.0 → 17, 8.5 → 21, 9.1 → 25 |
| AC13 | met | `GradleAdapterTest`: auto-detect and auto-download off, paths of the installed JDKs |
| AC14 | met | `GradleAdapterTest` (captured output), `GradleIntegrationTest` (real Gradle) |
| AC15 | met | `GradleIntegrationTest`: `gradlew --status` after close, after a failed build; unit tests for close |
| AC16 | met | `GradleIntegrationTest`: the Kotlin plugin's marker coordinates on a build that doesn't compile |
| AC17 | met | `GradleIntegrationTest`: grounding reads the classpath JAR of commons-lang3 |
| AC18 | met | `GradleModelTest`: `com.android.application`, `com.android.library`, Kotlin Multiplatform |
| AC19 | met | `GradleIntegrationTest`: the compile error in `integrationTest` in stage 1 |
| AC20 | met | `GradleAdapterTest` and `GradleIntegrationTest` |
| AC21 | met | `GradleAdapterTest` (command lines), `GradleIntegrationTest` (the `lint` task bound to `check` never runs) |
| AC22 | met | `GradleAdapterTest`: `:app:integrationTest --tests com.example.AppIT` |
| AC23 | met | `GradleConsoleTest` on captured kotlinc output |
| AC24 | met | `GradleConsoleTest` on a captured Groovy DSL failure |
| AC25 | met | `GradleConsoleTest` and `GradleIntegrationTest` on the multi-project fixture |
| AC26 | met | `cli` `GradleIntegrationTest`: verify on the base/head fixture |
| AC27 | met | `rewrite-gradle-plugin = "7.39.0"` in `gradle/libs.versions.toml` |
| AC28 | met | Dockerfile sets `LECTOR_REWRITE_GRADLE_PLUGIN` to the plugin directory of the distribution; the built image has no recipe JAR and no Gradle |
| AC29 | met | `cli` `GradleIntegrationTest`: `UpgradeSpringBoot_3_4` renames `setConnectTimeout`, verify green |
| AC30 | met | same test: the changed files are the recipe's, no `rewrite` or `lector` in the build scripts |
| AC31 | met | `GeneratedIndexTest` |
| AC32 | met | `GeneratedIndexTest` |
| AC33 | met | `GradleGuardrailsTest` |
| AC34 | met | `GradleGuardrailsTest`, and one through the core's `Guardrails` |
| AC35 | met | `GradleGuardrailsTest` |
| AC36 | met | `GradleGuardrailsTest` |
| AC37 | met | `RenovateSourceTest` on captured Gradle PR bodies (a plugin row, a catalog alias row) |
| AC38 | met | `RenovateSourceTest` and `PrCommandTest` (exit `0`) |
| AC39 | met | `--help` of `verify`, `migrate`, `heal`, `pr`; `VerifyCommandTest`, `PrCommandTest` |
| AC40 | met | `action/action.yml` |
| AC41 | met | `docs/setup.md`: Gradle and Java, the cache step, `gradle-init`, limitations |
| AC42 | met | `gradle-groovy`, `gradle-kotlin-catalog`, `gradle-multi-project` in `ecosystems/jvm/src/test/resources/fixtures/` |
| AC43 | met | `test` excludes the tag in both modules; `build --dry-run` has no `integrationTest` |
| AC44 | met | `docs/licensing.md` |
| AC45 | can't verify yet | needs a Gradle test repository with Renovate on GitHub |
| AC46 | met | no Tooling API, Groovy grammar, or Android dependency in the catalog or the build files |

## Open

- **AC45:** a real Renovate PR in a Gradle test repository on GitHub, with the M5 workflow. M7 stays 🟨 in
  `features.md` until then.
- The Gradle cache step in `docs/setup.md` is untested on GitHub: the paths follow GitHub's mount of the job's home
  into container actions.
- Dependabot and the Plugin Portal: whether Dependabot proposes updates of `rewrite-gradle-plugin` through the
  `exclusiveContent` repository is unchecked. The version check fails the build when only one of plugin and BOM moves.
