# AGENTS.md

Guidance for AI coding agents working in this repository.

> **Guiding rule: keep it simple and practical.** Code, docs, and output should be easy to understand. Build what the current milestone needs, nothing more. When in doubt, choose the simpler option.

## What we are building

A CI tool that takes a dependency-update PR (Renovate, Dependabot, or any other), applies the matching OpenRewrite recipe, and uses a local LLM (Ollama) to fix the remaining build and test errors caused by the update. Pre-existing or flaky test failures are reported, never fixed. The result is a reviewable PR.

- Full concept: [`docs/concept.md`](docs/concept.md)
- Milestones: [`docs/features.md`](docs/features.md)

## Tech stack

- Kotlin (JVM), Gradle with Kotlin DSL
- OpenRewrite (recipe index and execution)
- Ollama over HTTP (local LLM)
- tree-sitter (language-agnostic guardrails)
- Delivered as a CLI, plus a container image and a GitHub Action that wrap the CLI. The CLI is the pipeline entry point, not a developer tool.

## Project layout

Planned Gradle modules. Create them as needed; don't scaffold empty ones.

```
core/          data model, ports (interfaces), pipeline, fix loop, guardrails
recipes/       recipe index built from OpenRewrite metadata
llm/           Ollama client, prompts
sources/       update-source adapters (renovate, dependabot, fallback)
ecosystems/    ecosystem adapters, one submodule each (jvm, ...)
git/           Git host adapters (github, ...)
cli/           command-line entry point; wires adapters into core
action/        GitHub Action wrapper
```

Adapters are compile-time plugins: modules that implement a `core` interface and are registered in `cli`. No dynamic loading. See concept section 2.

## Commands

```
./gradlew build            # compile, test, lint, coverage check
./gradlew test             # tests only
./gradlew ktlintFormat     # fix most ktlint violations
./gradlew koverHtmlReport  # coverage report under */build/reports/kover/
./gradlew :cli:run --args="..."
./gradlew :cli:installDist # start script at cli/build/install/lector/bin/lector
```

The build uses a JDK 25 toolchain; Gradle downloads it if it's missing.

## Architecture rules

These come from the concept. Don't break them without updating the concept first.

1. **The core knows no language and no bot.** Anything specific to Maven, npm, Renovate, etc. lives in an adapter. `core` depends on no other module; only `cli` knows concrete adapters.
2. **No LLM in deterministic steps.** Recipe selection, verification, and guardrails never call the LLM.
3. **The LLM returns search/replace edits, never whole files.** It always gets grounding (real API signatures and release notes).
4. **Guardrails reject, they don't repair.** An edit that breaks a rule is dropped and counts as a failed attempt.
5. **Never auto-merge. Never edit generated code.**
6. **This runs in CI, never on a developer machine.** No interactive prompts, no assumption of a local IDE, editor, or hand-installed toolchain. Everything the run needs is in the container image.
7. **Source code and build logs never leave the infrastructure.** Outbound calls are limited to Ollama, the Git host, and read-only metadata lookups (package registries, recipe artifacts, provider schemas).

## Coding conventions

- **SOLID, DRY, KISS, applied pragmatically.** Prefer the simplest thing that works. Extract an abstraction on the second real use, not the first. Interfaces exist where the concept defines an extension point (adapters), not everywhere.
- Small, focused classes and functions. Name things after domain terms from the concept (`DependencyChange`, `Diagnostic`, `Edit`, `EcosystemAdapter`).
- Prefer immutable data classes and pure functions. Keep I/O (processes, HTTP, Git) at the edges.
- No new dependency without a short reason in the PR description.
- English for code, comments, docs, and commits.

## Code quality

`./gradlew build` fails on any finding. The gates are strict by default and relaxed only where a real case needs it.

- **ktlint** owns formatting (`ktlint_official`, configured in `.editorconfig`). Run `ktlintFormat` instead of formatting by hand.
- **detekt** owns code smells and likely bugs. Defaults plus the deviations in `config/detekt/detekt.yml`. Notable project rules: no `print`/`println` (stdout is the report channel; use Clikt `echo` and SLF4J), no process exit outside `main`, no `else` on sealed types.
- **Kover** requires ≥ 80 % line and ≥ 70 % branch coverage per module. Bounds only go up.
- Fix the code first. If a rule is wrong for this project, change it in the config with a one-line reason. A single justified exception may use `@Suppress("RuleName")` with a comment that says why.
- Never add baselines, `ignoreFailures`, or coverage exclusions.

## Testing

- **Test-first:** write or update the test before the logic.
- Unit tests for core logic. Adapters are tested against small **fixture repos** under `src/test/resources/fixtures/`.
- No real LLM in unit tests: use a fake `LlmClient`. Tests against a real Ollama are tagged and opt-in.
- A change is done when `./gradlew build` passes.

## Commits and PRs

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`. Use the module as scope, e.g. `feat(recipes): build index from BOM`.
- One logical change per commit.

## Docs stay in sync

- A change to architecture or scope updates [`docs/concept.md`](docs/concept.md).
- Finishing or reshaping a milestone updates [`docs/features.md`](docs/features.md).
