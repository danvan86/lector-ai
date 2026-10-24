# Architecture

How the implemented system is built. The [concept](../concept.md) says what the system should become. These documents describe what exists now, with diagrams. Each document starts with a short summary for agents.

| Document | Covers | Milestone |
|---|---|---|
| [Project skeleton](project-skeleton.md) | Modules, build conventions, quality gates, CLI contract, version flow, tests, CI | M0 |
| [Verify pipeline](verify-pipeline.md) | `verify` command, attribution, Maven adapter and parsers, report formats, exit codes, fixtures | M1 |
| [Recipe migration](recipe-migration.md) | `migrate` command, recipe index generated at build time, selection and ranking, downgrade rules, Maven recipe runner and resolved versions | M2 |
| [Fix loop](fix-loop.md) | `heal` command, `FixLoop` (clusters, attempts, progress tuple, budgets, commits and resets), `LlmClient` and the Ollama client, prompt and trimming, JVM cluster keys and `javap` grounding, the request's grounding with the API changes first (M10), real-model test | M3 |
| [Guardrails](guardrails.md) | `Guardrails` and its rules in `core`, `LanguageSupport` and `SourceFacts`, tree-sitter queries for Java and Kotlin, JVM scope and Maven build-file blocklist, rejections and tests run in the `heal` report | M4 |
| [PR pipeline](pr-pipeline.md) | `pr` command, `PrPipeline` (trigger checks, verify first, recipes once per group, report comment, label), update sources (Renovate, Dependabot, fallback) and their selection, branch policy and companion PRs, `GitHost` and `GitHubHost`, JDK selection, scrubbed process environment, GitHub Action and workflow, tool and Ollama images, release workflow | M5, M8 |
| [Benchmark](benchmark.md) | `benchmark` module (not shipped): corpus, `Harness` and its three modes, metrics, `results.json`, merge, `report.md` with the 90/10 verdict and the manual review, `Benchmark` workflow | M6 |
| [Gradle adapter](gradle-adapter.md) | `GradleAdapter` next to `MavenAdapter`: wrapper, init scripts, daemon JDK and toolchains, the query task and its model, two verify stages, Gradle parsers, recipe runner with the shipped OpenRewrite Gradle plugin, Gradle plugin leaves in the index, Renovate badges, Gradle guardrails, `--gradle-init` | M7 |
| [TypeScript adapter](typescript-adapter.md) | `TypeScriptAdapter` in `ecosystems:typescript` and `ecosystems:shared`: detection and tool errors, Node selection, npm, pnpm, and Yarn through Corepack, install, type check and tests, `tsc` and JUnit parsing, cluster keys, the lockfile following a build-file edit (`updateLockfiles`), guardrails with `loosening`, grounding from `.d.ts` (Plug'n'Play included), lockfiles and semver, no recipe step, image and action | M9 |
| [API changes](api-changes.md) | `apiChanges` on `EcosystemAdapter`, `ApiChangesGrounding` in `ecosystems:jvm`: what the errors refer to, the old JARs (Maven's `maven-dependency-plugin` fetch once per run, Gradle's classpaths from every model), `ApiModel` from class files with the ClassFile API, `ApiDiff` with seven categories, the text and its limits, the order in the prompt | M10 |

When a milestone changes how the system is built, update the matching document or add a new one here.
