# Architecture – Project skeleton

Milestone: [M0](../features.md#m0--project-skeleton-) · Spec: [`m0-project-skeleton/spec.md`](../../specs/done/m0-project-skeleton/spec.md) · Review: [`review.md`](../../specs/done/m0-project-skeleton/review.md)

> **Summary for agents**
>
> - Eight modules: `core` (data model, ports, and pipeline, depends on nothing), `recipes` (recipe index), `llm` (Ollama client), `sources` (Renovate adapter), `git` (GitHub adapter), `ecosystems:jvm` (Maven and Gradle adapters), each depending on `core` only, `cli` (the `lector` command, depends on all six), and `benchmark` (M6, never shipped; see [Benchmark](benchmark.md)). Every module applies the convention plugin `lector.kotlin-conventions` from `buildSrc`. Library and plugin versions are in `gradle/libs.versions.toml`, the base images' in the Dockerfiles under `docker/`.
> - `./gradlew build` is the single gate: compile, unit tests, end-to-end tests, ktlint, detekt, Kover. It fails on any finding.
> - The CLI contract: stdout carries only command output, logs go to stderr through SLF4J, and `LectorCommand.execute()` sets the exit code (`0` for success, help, and version; `2` for invalid usage, because `1` means "caused by the update").
> - The version is defined once, in `gradle.properties`. The build writes it into a resource, and `AppVersion` reads it at runtime.
> - `RootCommand.kt` is the composition root. `rootCommand()` builds the command tree and wires the adapters into the subcommands (`verify` since M1, see [Verify pipeline](verify-pipeline.md); `migrate` since M2, see [Recipe migration](recipe-migration.md); `heal` since M3, see [Fix loop](fix-loop.md); `pr` since M5, see [PR pipeline](pr-pipeline.md)).
> - A new module: add it to `settings.gradle.kts`, apply `id("lector.kotlin-conventions")`, depend only on `core`, and register it in `cli`. A new subcommand: add it in `rootCommand()`, with unit tests for every exit path and an `e2e` test for the start script.

## Overview

M0 is the foundation that every later milestone builds on. It contains no product logic. It provides the module layout, the shared build conventions, a CLI that prints its version, the quality gates, and CI.

```mermaid
flowchart LR
    subgraph repo["Repository"]
        direction TB
        catalog["gradle/libs.versions.toml<br/>all versions"]
        buildSrc["buildSrc<br/>lector.kotlin-conventions"]
        core["core<br/>data model, ports"]
        cli["cli<br/>lector command"]
    end
    catalog --> buildSrc
    buildSrc -. applied by .-> core
    buildSrc -. applied by .-> cli
    cli -- implementation --> core
    cli --> dist["installDist<br/>bin/lector + lib/*.jar"]
    dist --> ci["CI: ./gradlew build"]
```

## Modules

| Module | Contents | Depends on |
|---|---|---|
| `core` | Data model, ports, attribution, process runner (M1), recipe step (M2), fix loop (M3), guardrails (M4), the PR pipeline and the ports `UpdateSource` and `GitHost` (M5). | Nothing, by construction: its build file has no `project(…)` dependency. |
| `recipes` | The recipe index (M2): generated at build time by its `generator` source set, read at run time by `BundledRecipeIndex`. | `core`, kotlinx-serialization-json, SLF4J API. OpenRewrite only in the `generator` source set, never at run time. |
| `llm` | The Ollama client and the prompt (M3). | `core`, kotlinx-serialization-json, SLF4J API. No HTTP library: the JDK client. |
| `ecosystems:jvm` | The Maven adapter (M1, recipe runner and resolved versions since M2, cluster keys and `javap` grounding since M3, tree-sitter language support, scope, and build-file blocklist since M4) and the Gradle adapter (M7). | `core`, `maven-artifact`, kotlinx-serialization-json, `tree-sitter` with the Java and Kotlin grammars (JNI). |
| `sources` | Update-source adapters (M5): `RenovateSource` reads the changes and release notes from a Renovate PR's body. | `core`, SLF4J API. |
| `git` | Git host adapters (M5): `GitHubHost` reads the event, comments, labels, fetches, and pushes. | `core`, kotlinx-serialization-json, SLF4J API. No GitHub client: the JDK `HttpClient` and `git`. |
| `benchmark` | The benchmark harness, corpus, and report (M6). Not shipped: no module depends on it. See [Benchmark](benchmark.md). | `core`, `recipes`, `llm`, `ecosystems:jvm`, Clikt, kotlinx-serialization-json with its compiler plugin, SLF4J API (`slf4j-simple` at runtime). |
| `cli` | `lector` command, version lookup, logging setup. The composition root. | `core`, `recipes`, `llm`, `sources`, `git`, `ecosystems:jvm`, Clikt, kotlinx-serialization-json, SLF4J API (`slf4j-simple` at runtime). |
| `buildSrc` | The convention plugin, and `gradleFixtures()` for the test tasks that run real Gradle (M7). Not a product module. | Kotlin, ktlint, detekt, and Kover Gradle plugins; the Kotlin serialization plugin on its classpath, for `benchmark`. |

The dependency direction from the [concept](../concept.md#21-ports-and-adapters) holds: `cli → ecosystems:jvm → core`, `cli → recipes → core`, `cli → llm → core`, `cli → sources → core`, `cli → git → core`, and `cli → core`, never the other way. New adapter modules join the same way. `benchmark` sits beside `cli`: it uses `core` and the adapters, and nothing uses it. `action/` and `docker/` are not Gradle modules: they ship the start script as a container image and a GitHub Action ([PR pipeline](pr-pipeline.md)).

```mermaid
flowchart TB
    cli["cli<br/>(composition root)"] --> core
    jvm["ecosystems:jvm"] --> core
    cli --> jvm
    recipes --> core
    cli --> recipes
    llm --> core
    cli --> llm
    sources --> core
    cli --> sources
    git --> core
    cli --> git
    later["later: more ecosystems,<br/>sources, and hosts"] -.-> core
    cli -.-> later
    bench["benchmark<br/>(not shipped)"] --> core
    bench --> jvm
    bench --> recipes
    bench --> llm
```

## Build conventions

Shared build logic lives in one place, so modules can't drift apart.

| What | Where | Notes |
|---|---|---|
| Versions of libraries and plugins | `gradle/libs.versions.toml` | Also the JUnit BOM, so transitive JUnit artifacts are pinned. The only exception is the foojay plugin in `settings.gradle.kts`, because a settings `plugins {}` block can't read the catalog. |
| Repositories | `settings.gradle.kts` | Maven Central, and the Gradle Plugin Portal as an `exclusiveContent` repository for the OpenRewrite Gradle plugin (`org.openrewrite:plugin`) only (M7). |
| Versions of the base images | `FROM` lines of `docker/lector/Dockerfile` and `docker/ollama/Dockerfile` | Dependabot's `docker` ecosystem updates them. The Ollama version is also the one `:cli:realModelTest` runs. |
| Kotlin, JDK toolchain, tests, quality gates | `buildSrc/src/main/kotlin/lector.kotlin-conventions.gradle.kts` | Applied by every module with `id("lector.kotlin-conventions")`. |
| JDK 25 | `jvmToolchain(25)` in the convention plugin | The foojay resolver downloads the JDK if it's missing. |
| Tool version | `version` in `gradle.properties` | The only place it is written. |
| Root and `buildSrc` script linting | `build.gradle.kts` at the root | Applies `base`, so the root project has a `check` task, and ktlint covers `buildSrc/**/*.kts` and `buildSrc/**/*.kt`. Also registers `checkActionImage`. |
| Gradle daemon memory | `org.gradle.jvmargs` in `gradle.properties` | 2 GB. The default 512 MB runs out with detekt type resolution and Kotlin compilation in one daemon. |
| Formatting rules | `.editorconfig` | `ktlint_official`, no overrides. |
| detekt deviations | `config/detekt/detekt.yml` | Only the deviations from the defaults, each with a reason. |
| Versions that must match | `:recipes:checkRewritePluginVersion`, `:recipes:checkRewriteGradlePluginVersion`, and the root `checkActionImage`, all in `check` | `rewrite-maven-plugin` and `rewrite-gradle-plugin` must be built on the OpenRewrite version of `rewrite-recipe-bom`; Dependabot bumps them in separate PRs. The `image:` of `action/action.yml` must end in `/lector:<version>`, so the action runs the tool of its own release. |

## Build pipeline

`./gradlew build` runs `check` in every project. Each gate is wired into `check`, so a finding anywhere fails the build.

```mermaid
flowchart LR
    compile["compileKotlin<br/>compileTestKotlin"] --> test["test<br/>unit, in-process"]
    test --> kover["koverVerify<br/>100 % lines and branches"]
    compile --> install["installDist"] --> e2e["endToEndTest<br/>tag e2e, start script"]
    compile --> detekt["detektMain<br/>detektTest"]
    ktlint["ktlint source-set checks<br/>+ build scripts"]
    rootKtlint["root ktlintKotlinScriptCheck<br/>root scripts + buildSrc"]
    versions["checkRewritePluginVersion<br/>checkRewriteGradlePluginVersion<br/>checkActionImage"]
    test --> check
    kover --> check
    e2e --> check
    detekt --> check
    ktlint --> check
    rootKtlint --> check
    versions --> check
    check --> build["build"]
```

### Quality gates

| Gate | Task in `check` | Covers | Project rules |
|---|---|---|---|
| ktlint | `ktlint*SourceSetCheck`, `ktlintKotlinScriptCheck` | `*.kt` in every source set, `*.kts` in every module and the root, `*.kts` and `*.kt` in `buildSrc` | `ktlint_official` from `.editorconfig`. Fix with `./gradlew ktlintFormat`. |
| detekt 2.x | `detektMain`, `detektTest` (type resolution), plus `detektGenerator` in `recipes` | Kotlin sources | `warningsAsErrors`. Forbidden: `print`/`println` (also `System.out`, including `printf`, `format`, `append`, `write`), process exit outside `main`, `else` on sealed types, unused imports (`UnusedImport`: ktlint no longer checks them). The plain `detekt` task is disabled because it only repeats the findings. |
| Kover | `koverVerify` | In-process tests per module | 100 % lines and branches. `*.MainKt` is excluded because `main` exits the process; the end-to-end tests run the CLI's, the benchmark workflow the benchmark's. |
| Tests | `test`, `endToEndTest` | Unit and end-to-end. The opt-in `:cli:integrationTest`, `:ecosystems:jvm:integrationTest`, and `:cli:realModelTest` are not part of `check`. | Every test must pass. |
| Matching versions | `:recipes:checkRewritePluginVersion`, `:recipes:checkRewriteGradlePluginVersion`, `checkActionImage` | The rewrite plugins and the recipe BOM; the action's image tag and `version` | See [Build conventions](#build-conventions). |

There are no baselines, no `ignoreFailures`, and no coverage exclusions besides `MainKt`. Each gate was shown to fail the build on a deliberate violation (see the spec's acceptance criteria and the review).

## CLI

### Runtime flow

The `application` plugin builds a distribution: the start script `bin/lector` and the JARs in `lib/`. The tool image (`docker/lector/Dockerfile`) copies it to `/opt/lector` and runs `bin/lector` as its entry point ([PR pipeline](pr-pipeline.md)).

```mermaid
sequenceDiagram
    participant P as Pipeline / shell
    participant S as bin/lector (start script)
    participant M as main()
    participant V as AppVersion
    participant R as rootCommand()
    participant C as LectorCommand (Clikt)
    P->>S: lector --version
    S->>M: java --enable-native-access=ALL-UNNAMED … MainKt
    M->>V: read()
    V-->>M: "0.1.0" from version.properties
    M->>R: rootCommand(version)
    R->>R: log "Starting lector 0.1.0" (debug, stderr)
    R-->>M: LectorCommand
    M->>C: main(args)
    C-->>P: stdout "0.1.0", exit 0
```

| File | Role |
|---|---|
| `Main.kt` | `main()` reads the version and calls `execute()` on the root command. Nothing else, because it is excluded from coverage. |
| `RootCommand.kt` | `rootCommand()` is the composition root and logs the start. |
| `LectorCommand.kt` | The Clikt root command (a `NoOpCliktCommand`): `--version`, help on no arguments. Its subcommands are added in `rootCommand()`. `execute()` runs it like Clikt's `main`, but usage errors exit with `TOOL_ERROR` (`2`). |
| `AppVersion.kt` | Reads `version.properties` from the classpath. Fails with a clear message if the resource or its `version` key is missing. |

### Contract with the pipeline

The pipeline reads stdout and the exit code, so both are fixed and tested on every path.

| Invocation | stdout | stderr | Exit code |
|---|---|---|---|
| `lector --version` | `0.1.0` | empty | `0` |
| `lector` (no arguments) | Help text | empty | `0` |
| `lector --help` | Help text | empty | `0` |
| `lector --bogus` | empty | Usage and `Error: no such option --bogus` | `2` |
| `lector foo` | empty | Usage and `Error: no such subcommand foo` | `2` |
| `lector verify …` | See [Verify pipeline](verify-pipeline.md#cli-contract) | | `0`, `1`, or `2` |
| `lector migrate …` | See [Recipe migration](recipe-migration.md#cli-contract) | | `0`, `1`, or `2` |
| `lector heal …` | See [Fix loop](fix-loop.md#cli-contract) | | `0`, `1`, or `2` |
| `lector pr …` | See [PR pipeline](pr-pipeline.md#lector-pr) | | `0`, `1`, or `2` |

Output goes through Clikt's `echo`. Logs go through SLF4J to stderr (`slf4j-simple`, level `info` by default, `debug` with `JAVA_OPTS=-Dorg.slf4j.simpleLogger.defaultLogLevel=debug`). Libraries added later (OpenRewrite, HTTP clients) log through SLF4J too, so their output can't leak into stdout.

### Version flow

```mermaid
flowchart LR
    props["gradle.properties<br/>version=0.1.0"] --> gen["generateVersionResource<br/>(WriteProperties task)"]
    gen --> res["build/generated/resources/version/<br/>gmbh/vanella/lector/cli/version.properties"]
    res --> jar["cli JAR"]
    jar --> read["AppVersion.read()"]
    read --> out["lector --version"]
    props --> sys["system property lector.version<br/>(test tasks)"]
    sys --> tests["tests compare against it"]
```

No Kotlin file contains the version literal. Tests compare against the same property, so a version bump needs no test change.

## Tests

| Level | Task | What runs | Examples |
|---|---|---|---|
| Unit | `test` | In-process, excludes the `e2e`, `integration`, and `real-model` tags. Clikt's `test()` helper captures stdout, stderr, and the status code of a command. Exit codes that `LectorCommand.execute()` sets (usage errors) are tested with `executeInProcess()` (`Execution.kt`), which captures output and exit without ending the JVM. | `LectorCommandTest`, `AppVersionTest`, `RootCommandTest` |
| Integration | `:cli:integrationTest`, `:ecosystems:jvm:integrationTest` | Tag `integration`. Real Maven and Gradle on fixture repositories; opt-in, not in `check`. See [Verify pipeline](verify-pipeline.md#tests), [Recipe migration](recipe-migration.md#tests), and [Gradle adapter](gradle-adapter.md#tests). | `VerifyIntegrationTest`, `MigrateIntegrationTest`, `GradleIntegrationTest` |
| Real model | `:cli:realModelTest` | Tag `real-model`. `heal` with a real model in Ollama through Testcontainers; opt-in, not in `check`. See [Fix loop](fix-loop.md#tests). | `RealModelTest` |
| End-to-end | `endToEndTest` | Tag `e2e`. Runs `bin/lector` from `installDist` as a separate process with `JAVA_HOME` pinned. Streams go to files, with a 30 s timeout. | `LectorEndToEndTest` |

`./gradlew test` stays fast and doesn't build the distribution. `endToEndTest` depends on `installDist` and finds the start script through the `lector.bin` system property, which is derived from `installDist` and `applicationName`. Test fixtures live under `src/test/resources/` of each module (for example a version resource without a `version` key in `cli`, Maven and Gradle fixture repos in `ecosystems:jvm`).

```mermaid
flowchart LR
    subgraph unit["test (in-process)"]
        u1["LectorCommand.test(args)"] --> u2["stdout / stderr / statusCode"]
    end
    subgraph e2e["endToEndTest (separate process)"]
        e1["ProcessBuilder(bin/lector, args)"] --> e2["stdout file / stderr file / exit code"]
    end
    install["installDist"] --> e1
```

## CI and dependency updates

| What | Where | Behavior |
|---|---|---|
| Build workflow | `.github/workflows/build.yml` | On every pull request and on pushes to `main`: JDK 25 via `actions/setup-java`, Gradle via `gradle/actions/setup-gradle`, then `./gradlew build`. This builds our own tool; it is not the product pipeline. |
| Real-model workflow | `.github/workflows/real-model.yml` | Manual (`workflow_dispatch`, optional `model`) and nightly: `./gradlew :cli:realModelTest`. Never on push or pull requests. |
| Benchmark workflow | `.github/workflows/benchmark.yml` | Manual only (`workflow_dispatch`, optional `cases`): one job per case, mode, and repetition with the 7B Ollama image as a service, then a merged `results.json` and `report.md` as an artifact. See [Benchmark](benchmark.md). |
| Release workflow | `.github/workflows/release.yml` | On a version tag `v*`: checks that the tag is `v<version>` and that the MSAL check in `docs/licensing.md` is done, then publishes the tool image and the Ollama images to GHCR. See [PR pipeline](pr-pipeline.md). |
| Dependabot | `.github/dependabot.yml` | Weekly updates for the `gradle`, `github-actions`, and `docker` ecosystems. Gradle updates come through the version catalog, Docker updates through the Dockerfiles in `docker/`. Major updates of `eclipse-temurin` are ignored: the tool image has one stage per JDK release. |
| License | `LICENSE` | Apache 2.0. |

## Decisions and known limits

| Decision / limit | Reason | Revisit |
|---|---|---|
| `installDist` instead of a fat JAR or a native image | Maps directly to the container image (M5 copies it as it is) and avoids JAR merging and reflection problems. | — |
| Full `clikt` artifact, not `clikt-core` | `clikt-core` exits with `0` and prints errors to stdout unless `main` wires streams and exit codes by hand. The full artifact pulls in Mordant with JNA and needs `--enable-native-access=ALL-UNNAMED` in the start script and test JVMs. The tool image runs the same start script. | — |
| detekt 2.x alpha | detekt 1.x can't analyze Kotlin 2.4. | When 2.0 is stable |
| foojay version in `settings.gradle.kts` | A settings `plugins {}` block can't read the version catalog. | — |
| Domain types and ports arrive with the milestones that need them | M1 added `Diagnostic`, `Verification`, and `EcosystemAdapter`; M2 added `DependencyChange`, `VersionScheme`, and the recipe ports; M3 added `Edit`, `LlmClient`, and the adapter's `ground`, `clusterKey`, and `isBuildFile`; M4 added `LanguageSupport`, `SourceFacts`, `Guardrail`, and the adapter's `languageSupport`, `isEditable`, `testDisablingConfig`, and `testsRun`; M5 added `UpdateSource`, `GitHost`, `PullRequest`, `CommitIdentity`, `PrPipeline`, and the `timeout` of `verify` and `runTests`; M7 added none (Gradle is a second adapter). No type ahead of its first use. | Each milestone |
