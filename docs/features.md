# Features & Milestones

The vision from [`concept.md`](concept.md), split into small, shippable steps. Each milestone is usable on its own and builds on the one before.

**Strategy:** get one path working end to end first (JVM + Maven + Renovate), then widen it (Gradle, Dependabot, a second ecosystem). AI output only reaches a real PR once guardrails exist.

Status: ⬜ open · 🟨 in progress · ✅ done

---

## M0 – Project skeleton ✅

**Goal:** an empty project that builds and runs in CI.

- Gradle multi-module setup (Kotlin DSL) with `core` and `cli`
- CLI that prints its version
- CI workflow running `./gradlew build`
- Quality gates in `build`: ktlint, detekt, Kover coverage

**Done when:** `./gradlew build` is green locally and in CI.

## M1 – Verify and report (JVM/Maven, no AI) ✅

**Goal:** the tool can tell you whether a dependency update breaks the build, and where.

- Data model: `Diagnostic`, `Verification`
- `EcosystemAdapter` interface with a first JVM/Maven implementation: `detect`, `verify`, `runTests`
- Parse compiler and test failures into `Diagnostic`s (javac and kotlinc, surefire/failsafe reports)
- Attribution: compile errors always count; failing tests are rerun once (flaky) and compared with the base commit (pre-existing)
- CLI: `verify <repo> [--base <ref>]` prints a readable report

**Done when:** a broken fixture repo produces the correct list of diagnostics, and a pre-existing test failure is reported but not attributed to the update.

## M2 – Recipe index and OpenRewrite run ✅

**Goal:** the right recipe is found and applied automatically, without an LLM.

- Data model: `DependencyChange`; `VersionScheme` and resolved versions from the ecosystem adapter
- Build the index from recipe metadata at build time: `(ecosystem, package, target version) → recipe`, plus a license report
- Pick the most specific recipe that doesn't overshoot the target version
- Never downgrade: no recipe for an outdated PR, and a recipe that moves a package down is discarded
- Run the recipe through the Maven plugin (`runNoFork`) without editing `pom.xml`
- Then verify (M1); CLI: `migrate <repo> --package … --from … --to … [--base <ref>]`

**Done when:** a Spring Boot fixture repo gets the right upgrade recipe, and it's applied and verified.

## M3 – Fix loop (CLI only, no CI yet) ✅

**Goal:** the LLM fixes remaining errors. Driven through the CLI directly, not yet wired into a pipeline; CI integration lands in M5.

- Ollama client with structured output (JSON schema)
- Grounding: API signatures of the new version (`javap`), release notes, code around the error
- Group errors by root cause and fix the root cause first
- Search/replace edits, allowing build-file edits
- Budgets: attempts per cluster, global time limit, stop when there's no progress
- CLI: `heal <repo>`

**Done when:** at least one fixture repo goes from red to green using only the fix loop.

## M4 – Guardrails ✅

**Goal:** the LLM can't make the build green by deleting code.

- `LanguageSupport` for JVM: tree-sitter grammars for Java and Kotlin plus queries for methods, tests, assertions, skips, suppressions, stubs
- tree-sitter symbol comparison (no deleted methods; a method a current diagnostic points into may go only if it was empty or its body moves to a new method)
- Test protection: no deleted or skipped tests, no removed or changed assertions, the static test count stays the same (Java and Kotlin)
- Block stubs (`return null`, empty bodies, `TODO`) and new suppressions; reject edits that add parse errors
- Diff size limit, `editableScope` (no generated code), build-file blocklist (no test-disabling config)
- Close rule 5: reject edits to generated code (`editableScope`). Not enforced in M3

**Done when:** every guardrail rule has a test that rejects a matching bad edit.

## M5 – Renovate in CI (first end-to-end release) 🟨

**Goal:** a Renovate PR is fixed automatically in the pipeline.

- Renovate source adapter: every change of a grouped PR and the release notes, read from the PR body (`sources` module)
- `GitHost` port with a GitHub adapter (`git` module): event, comment, label, fetch, push
- `lector pr`: trigger checks, verify first and stop when green, one commit per distinct recipe, the fix loop, a plain push to the Renovate branch
- One sticky PR comment with what was done and what is left; `ai-assisted` label
- GitHub Action (Docker container action) and two images: the tool with JDKs 17, 21, 25 and Maven, and Ollama with the weights baked in
- Secure trigger: check PR author and same repo; GitHub App token; builds never see the token in their environment
- Documentation: [setup guide](setup.md) with the `gitIgnoredAuthors` setup for Renovate
- Legal check of the Moderne Source Available License (recipe index, `rewrite-spring`) before release: open, recorded in [`licensing.md`](licensing.md)
- Still open: the run on a real Renovate PR in a test repository on GitHub, and the first release to GHCR

**Done when:** a real Renovate PR in a test repo gets a fix commit and a report, with no manual steps.

## M6 – Benchmark ⬜

**Goal:** measure instead of guessing.

- Collection of real repos with known upgrades
- Metrics: green rate after recipe vs. after fix loop, remaining failures caused by the update, guardrail rejections, runtime
- Runs on demand; results go into a short report

**Done when:** we have first numbers for JVM/Maven and know whether the 90/10 assumption holds.

## M7 – Gradle 🟨

**Goal:** JVM projects on Gradle work too.

- `GradleAdapter` next to `MavenAdapter` in `ecosystems:jvm`, sharing parsers, grounding, cluster keys, and guardrails; Gradle 8 and 9, always through the project's wrapper
- Run recipes through a Gradle init script (no build file edits), with the OpenRewrite Gradle plugin shipped in the tool image
- Verify and diagnostics for Gradle: every source set compiles first, then every `Test` task runs (not `check`)
- The recipe index covers Gradle plugin upgrades (Spring Boot as a plugin); Renovate's plugin rows map to the plugin's marker coordinates
- `--gradle-init` and the action's `gradle-init` input, for mirrors and credentials
- Fixture repos for Gradle (Groovy DSL, Kotlin DSL with a version catalog, a multi-project build), and integration tests with real Gradle
- Still open: the run on a real Renovate PR in a Gradle test repository on GitHub

**Done when:** the M5 flow works the same for a Gradle repo.

## M8 – Dependabot and fallback ⬜

**Goal:** the tool doesn't care where the PR came from.

- Dependabot source adapter (`fetch-metadata`, including grouped updates)
- Fallback adapter: compare manifests between base and head
- Companion-PR mode (`heal/<branch>`), regenerated when the source PR changes
- Branch policy configurable per source

**Done when:** Dependabot PRs and manual dependency PRs are fixed the same way as Renovate PRs.

## M9 – Second ecosystem: TypeScript ⬜

**Goal:** prove the adapter abstraction holds outside the JVM.

- TypeScript adapter: `tsc --noEmit` + tests, grounding from `.d.ts` files
- Without a recipe if OpenRewrite JS isn't usable (check licensing first)
- Fixture repos and benchmark entries

**Done when:** a TypeScript repo runs through without changes to `core`. If `core` had to change, that's the lesson.

## Later ⬜

Not planned in detail yet. Prioritize after M9 based on demand and benchmark results.

- More ecosystems: Python, .NET, Terraform, config files (YAML/XML/properties)
- GitLab CI support
- Embedding fallback for recipes without structured metadata
- Better error clustering and smarter grounding (migration-guide index per dependency)
- Linters bound to Maven `verify` can block the last fix attempt, the same problem M7 Q17 solved for Gradle
