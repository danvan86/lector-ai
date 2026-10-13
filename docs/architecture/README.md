# Architecture

How the implemented system is built. The [concept](../concept.md) says what the system should become. These documents describe what exists now, with diagrams. Each document starts with a short summary for agents.

| Document | Covers | Milestone |
|---|---|---|
| [Project skeleton](project-skeleton.md) | Modules, build conventions, quality gates, CLI contract, version flow, tests, CI | M0 |
| [Verify pipeline](verify-pipeline.md) | `verify` command, attribution, Maven adapter and parsers, report formats, exit codes, fixtures | M1 |
| [Recipe migration](recipe-migration.md) | `migrate` command, recipe index generated at build time, selection and ranking, downgrade rules, Maven recipe runner and resolved versions | M2 |
| [Fix loop](fix-loop.md) | `heal` command, `FixLoop` (clusters, attempts, progress tuple, budgets, commits and resets), `LlmClient` and the Ollama client, prompt and trimming, JVM cluster keys and `javap` grounding, real-model test | M3 |
| [Guardrails](guardrails.md) | `Guardrails` and its rules in `core`, `LanguageSupport` and `SourceFacts`, tree-sitter queries for Java and Kotlin, JVM scope and Maven build-file blocklist, rejections and tests run in the `heal` report | M4 |
| [PR pipeline](pr-pipeline.md) | `pr` command, `PrPipeline` (trigger checks, verify first, recipes once per group, report comment, label), update sources (Renovate, Dependabot, fallback) and their selection, branch policy and companion PRs, `GitHost` and `GitHubHost`, JDK selection, scrubbed process environment, GitHub Action and workflow, tool and Ollama images, release workflow | M5, M8 |

When a milestone changes how the system is built, update the matching document or add a new one here.
