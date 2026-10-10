# AGENTS.md

Guidance for AI coding agents working in this repository.

> **Guiding rule: keep it simple and practical.** Code, docs, and output should be easy to understand. Build what the current milestone needs, nothing more. When in doubt, choose the simpler option.

## What we are building

A CI tool that takes a dependency-update PR (Renovate, Dependabot, or any other), applies the matching OpenRewrite recipe, and uses a local LLM (Ollama) to fix the remaining build and test errors caused by the update. Pre-existing or flaky test failures are reported, never fixed. The result is a reviewable PR.

- Full concept: [`docs/concept.md`](docs/concept.md)
- Milestones: [`docs/features.md`](docs/features.md)
- Architecture as built: [`docs/architecture/`](docs/architecture/README.md)

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
./gradlew build            # compile, unit + end-to-end tests, lint, coverage check
./gradlew test             # unit tests only (fast, no distribution)
./gradlew :cli:endToEndTest # end-to-end tests against the installed start script
./gradlew :cli:integrationTest # opt-in: verify and migrate with real Maven on fixtures (needs mvn and network)
./gradlew :recipes:generateRecipeIndex # recipe index and license report, under recipes/build/generated/recipe-index/
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
- Every external process gets a timeout, and its stdout and stderr are drained (or redirected to files). Otherwise a full pipe or a stuck process hangs the run.
- Exit `1` means "the update caused a problem". Any other failure exits with `2`, usage errors and unexpected exceptions included. Otherwise the pipeline blames the update for a tool bug.
- Values from Java APIs are platform types. Check for null where Java can return it, and fail with a clear message instead of a `NullPointerException`.
- All versions live in `gradle/libs.versions.toml`, including BOMs for versions that would otherwise come in transitively. Dependabot can only update what's declared.
- No new dependency without a short reason in the PR description.
- English for code, comments, docs, and commits.

## Code quality

`./gradlew build` fails on any finding. The gates are strict by default and relaxed only where a real case needs it.

- **ktlint** owns formatting (`ktlint_official`, configured in `.editorconfig`). Run `ktlintFormat` instead of formatting by hand.
- **detekt** owns code smells and likely bugs. Defaults plus the deviations in `config/detekt/detekt.yml`. Notable project rules: no `print`/`println`, also not via `System.out` and its `printf`, `format`, `append`, `write` (stdout is the report channel; use Clikt `echo` and SLF4J), no process exit outside `main`, no `else` on sealed types.
- **Kover** requires 100 % line and branch coverage per module. The only exclusion is the `main` entry point (`MainKt`): it exits the process, so only the end-to-end tests run it.
- Fix the code first. If a rule is wrong for this project, change it in the config with a one-line reason. A single justified exception may use `@Suppress("RuleName")` with a comment that says why.
- Never add baselines, `ignoreFailures`, or further coverage exclusions.
- Don't shape the design around coverage. Code that can't run in-process (`main`) stays a one-liner in its own file; logic moves into a testable function because it belongs there, not to raise the number.

## Testing

- **Test-first:** write or update the test before the logic.
- Unit tests for core logic. Adapters are tested against small **fixture repos** under `src/test/resources/fixtures/`.
- No real LLM in unit tests: use a fake `LlmClient`. Tests against a real Ollama are tagged and opt-in.
- Test the failure paths, not only the happy path: invalid input, missing or malformed resources. For the CLI, assert exit code, stdout, and stderr on every path, because they are the contract with the pipeline.
- A command that should run only some tests is checked once with the real build tool, not only by its command line. Lifecycles run other test tasks first (Maven's `verify` runs every surefire test before failsafe).
- End-to-end tests run the installed start script as a separate process. They are tagged `e2e` and run in `endToEndTest`, not in `test`.
- A change is done when `./gradlew build` passes.

## Commits and PRs

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`. Use the module as scope, e.g. `feat(recipes): build index from BOM`.
- One logical change per commit.
- Never commit or push on `main`. Each milestone gets its own branch (`<prefix>/<feature>`) and PR; in Conductor, its own workspace. The hook in `.claude/hooks/` blocks commits and pushes on `main` and on a detached HEAD.

## Docs stay in sync

- A change to architecture or scope updates [`docs/concept.md`](docs/concept.md).
- Finishing or reshaping a milestone updates [`docs/features.md`](docs/features.md).
- A change to how the system is built (modules, build, CLI contract, tests, CI) updates [`docs/architecture/`](docs/architecture/README.md).
