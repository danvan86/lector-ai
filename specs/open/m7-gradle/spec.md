# M7 – Gradle · Spec

Milestone: [M7 in features.md](../../../docs/features.md#m7--gradle-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

A Renovate PR in a Gradle repository (Groovy or Kotlin DSL) goes through the same M5 flow as a Maven repository: verify, recipe, fix loop with guardrails, commits, and the sticky comment.

**Why:** a large share of JVM projects build with Gradle, and Spring Boot Gradle projects are among the most common Renovate targets. M1–M5 built the whole pipeline for one build tool on purpose (features.md strategy). M7 is the first widening step. It also tests whether the adapter boundary holds: a second build tool should need a second adapter, not changes to `core`.

## What to build

### 1. `GradleAdapter` in `ecosystems:jvm`

- A second adapter next to `MavenAdapter` in `ecosystems:jvm`, registered in `cli`. It shares the javac parser, the JUnit XML parser, `javap` grounding, the cluster keys, and `LanguageSupport` with the Maven adapter.
- **Detect:** `settings.gradle`, `settings.gradle.kts`, `build.gradle`, or `build.gradle.kts` at the repository root.
- **Tool errors (exit `2`)** with a clear message:
  - `pom.xml` and Gradle files both at the root;
  - no `gradlew` at the root;
  - a Gradle version below 8 (read from `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties`);
  - an Android or Kotlin Multiplatform project (section 3);
  - a toolchain or daemon JVM request for a JDK that isn't in the image (section 2).

**Why:** detection, invocation, resolved versions, and the recipe runner differ between Maven and Gradle. Everything else is already shared (Q1). A repo with both build files would be guessed, and the tool doesn't guess (Q2). Gradle 8 and 9 keep the init script on one API level (Q3). Without a wrapper, the tool can't know which Gradle version the project needs (Q5). Without an Android SDK, the fix loop would only fight the environment (Q15).

### 2. How Gradle runs

- Always `./gradlew`. The wrapper downloads its distribution from the repository's own `distributionUrl`. The image contains no Gradle.
- Every call gets our init script with `-I`, `--console=plain`, and, if given, the user's `--gradle-init <file>` as a second `-I`.
- Our own init-script tasks (query, recipe) run with `--no-configuration-cache`. Verify uses the project's own settings.
- **JDK that runs Gradle:** the one requested in `gradle/gradle-daemon-jvm.properties` if the file exists. Otherwise the highest image JDK (17, 21, 25) that the wrapper's Gradle version supports, from a table in the adapter. Check the table against Gradle's compatibility matrix during implementation.
- **Toolchains:** resolved from the image JDKs (`org.gradle.java.installations.paths`). Auto-detection of other JDKs and auto-download are off.
- **Daemon:** on during a run. It is stopped at the end of every run, including runs that end with an error.
- The Gradle user home lives outside the repository, at a path the setup guide names so that a workflow can cache it.

**Why:** the wrapper respects the project's Gradle version, and regulated environments already point `distributionUrl` at an internal mirror, the same way M1 respects the Maven settings (Q5). `--gradle-init` mirrors `--maven-settings`: an init script can set repository mirrors and credentials (Q6). Gradle build scripts are code, so the Java target can't be read from a property as in M5. Toolchains let the project choose its compile JDK, and the daemon only has to run Gradle (Q4). No JDK is downloaded at run time (rule 6, M5 Q3). The daemon makes fix-loop iterations much faster, and stopping it means nothing leaks between runs or tests (Q11).

### 3. Query task: reading Gradle's model

One task in our init script writes JSON with:

- **Resolved versions:** the highest resolved version per package across all resolvable configurations of all projects, plus the buildscript classpath. Plugins appear under their marker coordinates `<id>:<id>.gradle.plugin`.
- **Classpath JARs:** the paths of the JARs on the compile classpaths, for `javap` grounding (M3).
- **Applied plugin IDs:** used to reject Android (`com.android.*`) and Kotlin Multiplatform (`org.jetbrains.kotlin.multiplatform`) projects.

The task compiles nothing, so it works when the code doesn't compile. If it fails (the build scripts don't configure), the versions are unreadable, and M2's rule applies: no recipe, or its result is discarded.

**Why:** Gradle's own resolution handles platforms, version catalogs, and conflict resolution. One task serves the M2 downgrade rules, M3 grounding, and the project-type check (Q8). Reading Gradle's cache directly would depend on an internal layout.

### 4. Verify and diagnostics

- **Stage 1:** an init-script task that depends on the `classes` task of every source set in every project, run with `--continue`.
- **Stage 2,** only if stage 1 has no errors: an init-script task that depends on every `Test` task in every project, run with `--continue`. Not `check`.
- **`runTests`:** runs only the given tests, in the `Test` task they came from. The task is known from the report path `build/test-results/<task>/`.
- **Parsers:** the shared javac parser. A Gradle variant for kotlinc (`e: file:///…:line:col …`). Gradle build failures (`* What went wrong:`) become `Diagnostic`s without a location. Test failures come from the shared JUnit XML parser on `build/test-results/**`.
- Paths are relative to the repository root, also in multi-project builds.

**Why:** the same compile-first behaviour as Maven. `testClasses` alone would miss `integrationTest` and `testFixtures` (Q10). Linters wired into `check` (Spotless, detekt, Checkstyle) would fail on unformatted model code as build errors without a location. Those come first in M3's progress tuple, so the attempt that fixes the last compile error would be rejected (Q17). Integration test suites are still covered because they are `Test` tasks. Console parsing is the M1 approach, and most of the parsing code is shared (Q9).

### 5. Recipe runner

- The init script applies the OpenRewrite Gradle plugin to the build and runs `rewriteRun` with the recipe artifact and recipe name from the index entry.
- The plugin JAR (version pinned in our version catalog) is shipped in the tool image as a small local repository that the init script uses. Recipe artifacts and the OpenRewrite libraries resolve through the project's repositories, never from the image.
- No file of the target repository is edited to run the recipe. The recipe's own changes are the only changes, captured with Git as in M2.
- Check during implementation that `rewriteRun` doesn't compile the project first. If it does, the run fails exactly when the update broke the build, as M2 found with Maven's `run` goal. Then reopen this point instead of working around it.

**Why:** concept section 5: Gradle recipes run through an init script, with no build-file edits. An init script's own classpath can't be redirected by a user's mirror, so shipping the plugin avoids a blocked fetch in regulated environments (Q7). Recipe JARs still never ship, because of their licences (M2 Q2). I believe the plugin is Apache 2.0 and small; both are checked and recorded (section 9).

### 6. Gradle plugins in the recipe index (`recipes`)

- The index also reads `org.openrewrite.gradle.plugins.UpgradePluginVersion` leaves. The package is the plugin's marker coordinates `<id>:<id>.gradle.plugin` in the `maven` namespace.
- The generated index contains `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4` for `org.springframework.boot:org.springframework.boot.gradle.plugin` with selector `3.4.x`.
- Selection, ranking, and the downgrade rules are unchanged.

**Why:** Gradle projects bump Spring Boot as a plugin, not as a parent. Without these leaves, a Spring Boot Gradle repo never gets a recipe, and the M7 "done when" fails (Q12). Marker coordinates are real Maven coordinates, so the namespace and `core` stay as they are (M2 Q6).

### 7. Fix loop and guardrails for Gradle

- **Build files:** `*.gradle`, `*.gradle.kts`, `gradle/*.versions.toml`, `gradle.properties`, and the sources of `buildSrc` and of included build-logic builds.
- **Build-file blocklist** (added or changed lines): disabling a `Test` task (`enabled = false`, `onlyIf`), `ignoreFailures`, and new `exclude`, `excludeTestsMatching`, or `excludeTags` filters.
- **`editableScope`** excludes `build/`, `.gradle/`, `**/generated*/**`, `gradle/wrapper/**`, `gradlew`, `gradlew.bat`, and `*.groovy` sources that aren't build scripts.
- Failures in Groovy tests are still verified, attributed, and reported.
- Cluster keys, grounding, and `LanguageSupport` are the shared JVM ones.

**Why:** the same policy as Maven in M4, so that legitimate fixes like a changed plugin ID stay possible (Q13). The wrapper decides which code is downloaded and executed, so the model can't change it. The recipe still can, because recipe output isn't guarded (M4 Q10). M4 has no Groovy grammar, so a Groovy test could be deleted unnoticed. Excluding `*.groovy` closes that loophole with one glob (Q14).

### 8. Pipeline, Renovate, action, and image

- **Renovate manager lookup:** `gradle` → `maven`. Plugin rows map to marker coordinates `<id>:<id>.gradle.plugin`. Check how Renovate shows plugin rows on a captured PR body. `gradle-wrapper` maps to no ecosystem, so a wrapper-only PR produces no `DependencyChange` and is skipped (M5 rule).
- **`lector pr`** works unchanged for Gradle repos. M5's Maven JDK selection is replaced by section 2 for Gradle.
- **CLI:** `--gradle-init <file>` on `verify`, `migrate`, `heal`, and `pr`.
- **Action:** a `gradle-init` input, passed through like the Maven settings file.
- **Tool image:** adds the OpenRewrite Gradle plugin repository (section 5). No Gradle distribution, no Android SDK, no extra JDK.
- **Setup guide:** Gradle requirements (wrapper, Gradle 8+, toolchains from JDKs 17/21/25), how to cache the Gradle user home, `gradle-init` for mirrors. Limitations: Android and Kotlin Multiplatform, Groovy sources not edited, wrapper-only PRs skipped, linters not run.

**Why:** "done when" is the M5 flow, unchanged, on a Gradle repo. The lookup table is where concept section 3 maps managers to ecosystems. A wrapper bump has no Maven coordinates and usually needs build-logic changes that a human should make (Q18).

### 9. Tests, end-to-end run, and licensing

- **Parser tests** on captured Gradle output: javac, kotlinc, build failures without a location, JUnit XML from `build/test-results/`.
- **Adapter unit tests:** detection and each tool error from section 1, the daemon JDK table, the command lines (init scripts, `--console=plain`, `--continue`, `--gradle-init`, no `check`).
- **Index test:** the Spring Boot plugin entry from section 6.
- **Renovate parser test:** a captured Gradle PR body with a plugin row and a catalog row, and a `gradle-wrapper` PR that is skipped.
- **Fixture repos** under `src/test/resources/fixtures/`: Groovy DSL, Kotlin DSL with a version catalog, and a multi-project build.
- **Tagged integration tests with real Gradle:**
  - a base/head fixture with one test failure caused by the update and one pre-existing failure;
  - a Spring Boot 3.3 → 3.4 plugin bump that gets `UpgradeSpringBoot_3_4`, applied by type and verified, with no build file touched except by the recipe;
  - the query task on a fixture that doesn't compile still returns resolved versions.
- **End-to-end:** a second GitHub test repository with a Gradle build, Renovate, and the documented M5 workflow on a standard runner with the 7B model. A real Renovate PR that breaks the build gets a fix commit and a sticky comment with no manual steps.
- **Licensing:** `docs/licensing.md` records the OpenRewrite Gradle plugin's licence before a release that ships it.

**Why:** the fixtures cover the DSL and project variants quickly. The test repository proves "the M5 flow works the same" (Q16). The plugin is redistributed in our image, so its licence belongs with the other release blockers from M5.

## What not to do

| Don't | Why |
|---|---|
| Put Gradle knowledge into `core`, or change `core` ports for Gradle | Rule 1. If `core` has to change, say so in the PR: it's the lesson M9 is meant to find early. |
| Merge both build tools into one `JvmAdapter`, or add a module `ecosystems:gradle` | Decided against (Q1). |
| Pick a build tool when both `pom.xml` and Gradle files exist | Decided against (Q2): tool error. |
| Support Gradle 7 or older | Decided against (Q3). |
| Parse build scripts to find the Java version, or start Gradle twice to pick the JDK | Decided against (Q4). |
| Allow toolchain auto-download, or add JDK 8 or 11 | Rule 6, Q4, M5 Q3. |
| Put a Gradle distribution in the image, or fall back to a system `gradle` | Decided against (Q5). |
| Override `distributionUrl` or add a mirror option for distributions | The repo's own wrapper config is respected (Q5). Changing it would be a build-file edit. |
| Fetch the OpenRewrite Gradle plugin from the Plugin Portal at run time | Decided against (Q7). |
| Ship recipe JARs or OpenRewrite libraries in the image | M2 Q2: they resolve through the project's repositories. |
| Edit `build.gradle(.kts)` or `settings.gradle(.kts)` to apply the plugin or run a recipe | Concept section 5: init script only. |
| Use the Gradle Tooling API | Decided against (Q8, Q9). |
| Search `~/.gradle/caches` for JARs or versions | Decided against (Q8). |
| Run `check` or `build` in verify | Decided against (Q17, Q10). |
| Skip, filter, or ignore tests in the verify command | Same as M1: verify only observes. Filters only in `runTests`. |
| Add a second JVM namespace (`gradle`, `gradle-plugin`, `gradle-distribution`) | Decided against (Q12, Q18, M2 Q6). |
| Let the model edit wrapper files or Groovy sources | Decided against (Q13, Q14). |
| Add a Groovy grammar to `LanguageSupport` | Decided against (Q14). |
| Support Android or Kotlin Multiplatform, or add the Android SDK | Decided against (Q15). |
| Handle Gradle wrapper update PRs | Decided against (Q18): skipped and documented. |
| Fix the same linter problem for Maven | It's outside M7. Recorded as an open point (Docs to update). |
| Add Gradle cases to the benchmark | Not part of M7's "done when". The benchmark corpus is M6's decision. |
| Support Dependabot or companion PRs | M8. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | `GradleAdapter` lives in `ecosystems:jvm` next to `MavenAdapter`, and `cli` registers both | 1 | Sources of `ecosystems:jvm`; adapter list in `cli` |
| AC3 | No module `ecosystems:gradle` exists | 1, Don't | `settings.gradle.kts` |
| AC4 | `core` has no project dependencies and no Gradle-specific code | 1, Don't | `core/build.gradle.kts` has no `project(`; case-insensitive search of `core/src/main` for `gradle`: no matches |
| AC5 | Detection succeeds for each of the four root build files and fails without them | 1 | Unit test |
| AC6 | `pom.xml` plus Gradle files at the root → exit `2` | 1 | CLI or adapter test |
| AC7 | No `gradlew` → exit `2` | 1 | CLI or adapter test |
| AC8 | A wrapper with Gradle 7.x → exit `2` | 1 | Unit test on a fixture `gradle-wrapper.properties` |
| AC9 | Every Gradle command line uses `./gradlew`, `-I <our init script>`, and `--console=plain` | 2 | Unit tests on the built command lines |
| AC10 | `--gradle-init <file>` is passed as an additional `-I` on every Gradle call | 2, 8 | Unit test on the command lines |
| AC11 | Query and recipe calls use `--no-configuration-cache`; verify calls don't add it | 2 | Unit test on the command lines |
| AC12 | Daemon JDK: `gradle-daemon-jvm.properties` wins; otherwise the table gives the highest supported image JDK for 8.0, 8.5, and 9.1 | 2 | Unit tests |
| AC13 | Gradle runs with toolchain auto-download and auto-detection off and installation paths set to the image JDKs | 2 | Unit test on the command line or init script |
| AC14 | A toolchain request for JDK 11 → exit `2` with a message naming the JDK | 1, 2 | Integration test or adapter test |
| AC15 | After every run, including one that fails, no Gradle daemon started by the run is left | 2 | Integration test checks `./gradlew --status` (or the process list) after the run |
| AC16 | The query task returns resolved versions, including a plugin under marker coordinates, for a fixture that doesn't compile | 3 | Tagged integration test |
| AC17 | The query task returns compile-classpath JAR paths that exist on disk | 3 | Tagged integration test |
| AC18 | A fixture applying `com.android.application` or `org.jetbrains.kotlin.multiplatform` → exit `2` | 3 | Adapter test with a fake query result |
| AC19 | Stage 1 compiles every source set, including a custom `integrationTest` source set | 4 | Tagged integration test: a compile error in `integrationTest` is reported in stage 1 |
| AC20 | Stage 2 runs only if stage 1 has no errors | 4 | Unit or integration test |
| AC21 | Stage 2 runs every `Test` task and no `check` or `build` task; a failing `spotlessCheck`-style task wired into `check` doesn't show up | 4, Don't | Command line test; tagged integration test with a fixture that has a failing check-only task |
| AC22 | `runTests` runs only the given tests, in the `Test` task they came from | 4 | Unit test on the command line for a test from `integrationTest` |
| AC23 | Gradle kotlinc errors are parsed into `Diagnostic`s with file, line, column, and message | 4 | Parser test on captured output |
| AC24 | A Gradle `* What went wrong:` failure becomes a `Diagnostic` without a location | 4 | Parser test on captured output |
| AC25 | Diagnostics in the multi-project fixture have paths relative to the repository root | 4 | Parser or integration test |
| AC26 | The tagged base/head integration test finds one failure caused by the update and one pre-existing failure | 4, 9 | Run the tagged test task: passes |
| AC27 | The OpenRewrite Gradle plugin version is pinned in `gradle/libs.versions.toml` | 5 | Version catalog |
| AC28 | The tool Dockerfile adds the plugin as a local repository and no `rewrite-*` recipe JARs and no Gradle distribution | 5, 8, Don't | Dockerfile content |
| AC29 | The Spring Boot integration test chooses `UpgradeSpringBoot_3_4`, applies a type-based change, and verifies | 5, 6, 9 | Run the tagged test task: passes |
| AC30 | After a recipe run, the only changes in the fixture are the recipe's (no plugin or init lines added to build files) | 5, Don't | Integration test inspects `git diff` |
| AC31 | The generated index maps `org.springframework.boot:org.springframework.boot.gradle.plugin` with selector `3.4.x` to `UpgradeSpringBoot_3_4` | 6 | Index test |
| AC32 | The index uses no namespace other than `maven` for JVM entries | 6, Don't | Index test or index report |
| AC33 | The Gradle adapter marks `*.gradle`, `*.gradle.kts`, `gradle/*.versions.toml`, `gradle.properties`, and `buildSrc` sources as build files | 7 | Unit test |
| AC34 | Each blocklist entry (`enabled = false`, `onlyIf`, `ignoreFailures`, new `exclude`, `excludeTestsMatching`, `excludeTags`) rejects a matching edit | 7 | One guardrail test each |
| AC35 | An edit to `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, a file under `build/` or `.gradle/`, or a `*.groovy` test is rejected by the scope rule | 7 | Guardrail tests |
| AC36 | An edit to a `.gradle` or `.gradle.kts` build script is not rejected by the scope rule | 7 | Guardrail test |
| AC37 | The Renovate lookup maps `gradle` to `maven`, and a plugin row to marker coordinates | 8 | Parser test on a captured Gradle PR body |
| AC38 | A `gradle-wrapper`-only PR produces no `DependencyChange` and is skipped with exit `0` | 8 | Parser and pipeline tests |
| AC39 | `verify`, `migrate`, `heal`, and `pr` accept `--gradle-init` | 8 | CLI tests or `--help` output |
| AC40 | `action/action.yml` has a `gradle-init` input | 8 | File content |
| AC41 | The setup guide covers the Gradle requirements, caching the Gradle user home, `gradle-init`, and the limitations from section 8 | 8 | Docs content |
| AC42 | Fixtures for Groovy DSL, Kotlin DSL with a version catalog, and a multi-project build exist | 9 | `src/test/resources/fixtures/` in `ecosystems:jvm` |
| AC43 | The tagged Gradle integration tests are not part of `./gradlew build` | 9 | Build configuration excludes the tag from the default test task |
| AC44 | `docs/licensing.md` records the licence of the OpenRewrite Gradle plugin | 9 | File content |
| AC45 | A real Renovate PR in a Gradle test repository got a fix commit and a sticky comment with no manual steps | 9 | Outside the repo: link to the PR, recorded in `docs/features.md` when M7 is set to ✅. Can't verify from the repo alone |
| AC46 | No Gradle Tooling API, Groovy grammar, or Android dependency is added | Don't | Version catalog and module build files |

## Docs to update

- `docs/features.md`: set M7 to ✅ when done, with the link to the end-to-end PR. Add to **Later**: "Linters bound to Maven `verify` can block the last fix attempt, the same problem M7 Q17 solved for Gradle."
- `docs/concept.md` section 4: the index also reads Gradle `UpgradePluginVersion` leaves under plugin marker coordinates.
- `docs/concept.md` section 5: the JVM **Verify** cell becomes "Maven `verify` / every Gradle `Test` task". Mention that the OpenRewrite Gradle plugin ships in the image.
- `docs/concept.md` section 3: the Renovate lookup table maps `gradle` to `maven`, and `gradle-wrapper` to nothing.
- `docs/concept.md` section 8.4 and the M5 setup guide: apart from dependencies, the job now also downloads the Gradle distribution through the wrapper. The guide's "Maven only" limitation becomes the Gradle limitations from section 8.
- `AGENTS.md` **Commands**: the command for the tagged Gradle integration tests, if it differs from the Maven one.
