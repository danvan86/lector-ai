# M0 – Project skeleton · Spec

Milestone: [M0 in features.md](../../../docs/features.md#m0--project-skeleton-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

An empty project that builds, tests, and lints itself, locally and in CI.

**Why:** every later milestone adds modules and code to this skeleton. If the build conventions, module boundaries, and CI are right from the start, M1–M9 only add domain logic and never have to fix the foundation. M0 is deliberately boring. It contains no product logic.

## What to build

### 1. A Gradle multi-module build with `core` and `cli`

- Root project `lector-ai`, Gradle Kotlin DSL, Gradle wrapper committed.
- Two modules: `core` and `cli`. `cli` depends on `core`. `core` depends on no other module.
- Base package `gmbh.vanella.lector` (e.g. `gmbh.vanella.lector.cli`).
- `core` stays empty of domain code in M0.

**Why:** the concept makes `core` the center that knows no adapter, and `cli` the composition root (concept section 2.1). Setting up this direction now makes the rule hold "by construction": `core`'s build file has no project dependencies, so it can't import an adapter.

### 2. Shared build conventions in one place

- One convention plugin in `buildSrc` that every module applies. It sets the Kotlin version, the JVM toolchain, the test setup, and the quality gates from section 7 (ktlint, detekt, Kover).
- All dependency and plugin versions live in the version catalog `gradle/libs.versions.toml`.

**Why:** modules will multiply (recipes, llm, sources, ecosystems, git). Without one place for conventions, Kotlin versions, JVM targets, and lint rules drift between modules. The catalog gives Dependabot one file to update.

### 3. JDK 25 via Gradle toolchain

- The build targets JDK 25 through `jvmToolchain(25)`.
- The foojay toolchain resolver is enabled, so Gradle downloads JDK 25 when it's missing.

**Why:** JDK 25 is the current LTS and keeps every tree-sitter binding open for M4 (the official one needs the FFM API, JDK 22+). Auto-download means the build works on any machine without asking contributors to install a specific JDK first. This applies to building the tool. It does not mean the product runs on developer machines (AGENTS.md rule 6).

### 4. A CLI that prints its version

- Executable name: `lector`.
- `lector --version` prints the version and exits with `0`.
- `lector` without arguments prints the help text.
- Built with Clikt.
- The version comes from the Gradle `version` property (initially `0.1.0`), written into a generated resource at build time and read by the CLI at runtime.

**Why:** a version command is the smallest thing that proves the whole chain works: build, packaging, start script, resources. Clikt gives us subcommands (`verify`, `migrate`, `heal`) later without rework. One version source in the build avoids a hard-coded string that gets out of sync.

### 5. Runnable distribution

- The Gradle `application` plugin produces a distribution via `installDist`: start scripts in `bin/` plus the JARs in `lib/`.

**Why:** this layout drops straight into the container image in M5, without fat-JAR merging problems.

### 6. Logging that keeps stdout clean

- SLF4J API with `slf4j-simple` as the backend, writing to stderr.
- stdout is reserved for the command's actual output (version now, reports later).

**Why:** later steps and the pipeline read the report from stdout (M1 `--format json`). Logs must never mix into it. Libraries we add later (OpenRewrite, HTTP clients) already log through SLF4J, so their logs end up in the same place.

### 7. Tests, coverage, and static analysis as part of `build`

- Tests use JUnit 5 with `kotlin.test` assertions.
- At least one test covers `--version`.
- `./gradlew build` (through `check`) fails on any of these:
  - **ktlint** (`org.jlleitschuh.gradle.ktlint`): style violations in `*.kt` and `*.kts`. Code style `ktlint_official` from `.editorconfig`, no disabled rules.
  - **detekt** (`dev.detekt`, 2.x): code smells and likely bugs. `buildUponDefaultConfig = true`, config `config/detekt/detekt.yml` (only the deviations, each with a reason), `warningsAsErrors`. `check` runs the type-resolving tasks (`detektMain`, `detektTest`), because several rules in our config only work with type resolution.
  - **Kover** (`org.jetbrains.kotlinx.kover`): coverage below **80 % lines** or **70 % branches**, per module. `koverVerify` runs in `check`.
- No baselines, no ignored failures, no `@ExcludeFromCoverage`-style exclusions. Generated code is the only thing excluded from analysis and coverage.
- detekt 2.x needs Kotlin 2.4 or newer.

**Why:** AGENTS.md says a change is done when `./gradlew build` passes. That only means something if `build` includes tests and the quality gates. Test-first starts here, with the version command. ktlint owns formatting, detekt owns code smells; each violation is reported by one tool. Starting strict on an empty codebase is free; adding strictness later means cleaning up first. detekt 2.x is still alpha, but 1.x can't analyze Kotlin 2.4. The detekt config adds three project rules: `println` is forbidden (stdout is the report channel, section 6), exiting outside `main` is forbidden (exit codes are a contract), and `else` on sealed types is forbidden (new `Diagnostic` kinds must not vanish silently). Coverage is per module so `core` can't hide behind `cli`.

### 8. CI for this repository

- A GitHub Actions workflow on GitHub-hosted runners runs `./gradlew build` on every pull request and on pushes to `main`.
- JDK 25 is provided with `actions/setup-java`.

**Why:** the repository lives on GitHub, and GitHub-hosted runners need no setup. This CI builds our own tool. It is not the product pipeline and needs no GPU or Ollama.

### 9. Dependency updates and license

- Dependabot configuration for the `gradle` and `github-actions` ecosystems.
- `LICENSE` file with Apache 2.0.

**Why:** our own dependencies should stay current from day one, and our repo later serves as a Dependabot test case for M8. Apache 2.0 matches OpenRewrite core and is easy for regulated companies to adopt.

## What not to do

| Don't | Why |
|---|---|
| Add domain types (`DependencyChange`, `Diagnostic`, `Edit`) or ports (`EcosystemAdapter`, …) | They belong to M1 and later. Building them now means guessing their shape without a test that needs them. |
| Create other modules (`recipes`, `llm`, `sources`, `ecosystems`, `git`, `action`) | AGENTS.md: create modules when a milestone needs them, no empty scaffolding. |
| Add subcommands other than `--version` and help | `verify` arrives in M1. Empty placeholder commands are dead code. |
| Build a container image or a GitHub Action wrapper | That is M5. The image needs toolchains and Ollama, which don't exist yet. |
| Use a fat JAR or a GraalVM native image | Decided against: `installDist` is enough and maps directly to the image. Native images fight reflection-heavy libraries. |
| Derive the version from Git tags | Decided against: one explicit `version` property is simpler. |
| Add ArchUnit or Konsist | Decided against for now. "core depends on nothing" holds by construction. Revisit when there are several contributors. |
| Add a ktlint or detekt baseline, `ignoreFailures`, or coverage exclusions | Gates that can be bypassed quietly stop being gates. Fix the code or change the rule in the config, with a reason. |
| Add detekt's ktlint wrapper (`detekt-formatting`) | ktlint already runs. One tool per concern. |
| Add CodeQL | Decided against: the repo is private without GitHub Code Security. |
| Use Logback or a hand-written logger | Decided against: `slf4j-simple` is enough for a CLI job. |
| Configure Renovate for this repo | Decided: Dependabot. |
| Set up tagged Ollama tests or self-hosted runners | That is M3. M0 CI runs only what `./gradlew build` runs. |
| Publish artifacts or create releases | Not needed until there is something to ship (M5). |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | 1–7 | Run it from the repo root: exit code `0` |
| AC2 | The root project is named `lector-ai` and includes exactly the modules `core` and `cli` | 1 | `settings.gradle.kts` |
| AC3 | `core` has no project dependencies | 1 | `core/build.gradle.kts` contains no `project(` |
| AC4 | `cli` depends on `core` | 1 | `cli/build.gradle.kts` |
| AC5 | All Kotlin sources are in packages under `gmbh.vanella.lector` | 1 | `package` lines in every `*.kt` file under `core/` and `cli/` |
| AC6 | Both modules apply one shared convention plugin from `buildSrc` | 2 | `buildSrc/` exists; `plugins {}` in both module build files |
| AC7 | No dependency or plugin version is written in a module build file; all versions are in `gradle/libs.versions.toml` | 2 | Module build files reference only `libs.*` aliases |
| AC8 | The build uses a JDK 25 toolchain | 3 | `jvmToolchain(25)` in the convention plugin |
| AC9 | The foojay toolchain resolver is enabled | 3 | `settings.gradle.kts` |
| AC10 | `installDist` produces a `lector` start script | 4, 5 | `./gradlew :cli:installDist`, then `cli/build/install/lector/bin/lector` exists |
| AC11 | `lector --version` prints exactly `0.1.0` to stdout and exits with `0` | 4 | Run the start script; stdout = `0.1.0`, exit code `0` |
| AC12 | `lector` without arguments prints the help text | 4 | Run the start script without arguments; output contains usage and the `--version` option |
| AC13 | The version is defined only by the Gradle `version` property | 4 | No `0.1.0` literal in any `*.kt` file |
| AC14 | Logging uses the SLF4J API with `slf4j-simple`, writing to stderr | 6 | Catalog and `cli` dependencies; logs from a run appear on stderr, not on stdout |
| AC15 | A test covers `--version`, using JUnit 5 and `kotlin.test` | 7 | Test source under `cli/src/test/` |
| AC16 | A ktlint violation makes `./gradlew build` fail | 7 | Add a style violation temporarily, run `./gradlew build`: it fails on ktlint; revert |
| AC17 | A GitHub Actions workflow runs `./gradlew build` on pull requests and on pushes to `main`, with JDK 25 from `actions/setup-java` | 8 | Workflow file under `.github/workflows/` |
| AC18 | The CI workflow is green | 8 | GitHub checks on the PR (outside the repo) |
| AC19 | Dependabot is configured for `gradle` and `github-actions` | 9 | `.github/dependabot.yml` |
| AC20 | `LICENSE` contains the Apache License 2.0 | 9 | `LICENSE` at the repo root |
| AC21 | `core` contains no domain types and no ports | Don't | No `DependencyChange`, `Diagnostic`, `Edit`, `EcosystemAdapter`, or other interfaces in `core/` |
| AC22 | The CLI has no subcommands | Don't | Help output lists no commands |
| AC23 | No container image, GitHub Action wrapper, or extra modules exist | Don't | No `Dockerfile`, no `action/`, no other module directories |
| AC24 | None of the rejected tools are configured: Shadow, GraalVM native image, Git-tag versioning, ArchUnit/Konsist, Logback, Renovate, detekt-formatting, CodeQL | Don't | Version catalog, build files, and repo root (no `renovate.json`) |
| AC25 | A detekt finding makes `./gradlew build` fail, including a type-resolution rule | 7 | Add a `println` call to `cli` main code temporarily, run `./gradlew build`: it fails on `ForbiddenMethodCall`; revert |
| AC26 | Coverage below the bounds makes `./gradlew build` fail | 7 | Add an untested function to `cli` temporarily, run `./gradlew build`: it fails on `koverVerify`; revert |
| AC27 | No baseline, `ignoreFailures`, or coverage exclusion is configured | 7, Don't | Convention plugin; no `*baseline*.xml` files in the repo |

## Docs to update

- `docs/features.md`: set M0 to ✅ when done.
- `AGENTS.md`: adjust the **Commands** section if the commands differ (e.g. add `./gradlew :cli:installDist`). Keep **Code quality** in sync with the gates above.
