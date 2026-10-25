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

## M6 – Benchmark 🟨

**Goal:** measure instead of guessing.

- `benchmark` module (not shipped): 22 Maven cases, 16 historical bot PRs that failed CI and 6 synthetic bumps, each run in three modes (recipe only, fix loop only, recipe + fix loop), the model modes three times; a case whose head has no failure caused by the update is invalid
- Metrics per run: green, failures per stage before and after, recipes, attempts, accepted fixes, guardrail rejections per rule, stop reason, runtime per stage, changed lines of recipe and fix commits, pre-existing and flaky failures, cut requests
- `report.md` from `results.json` and a manual review: green rates with spread, the 90/10 verdict (the recipe's mean share of changed lines on green recipe + fix-loop runs, holds at 90%), rejections, runtime
- `Benchmark` workflow: `workflow_dispatch` on standard runners with `qwen2.5-coder:7b`, one job per case, mode, and repetition
- Fix requests carry only the diagnostics that fit into the context, each with its code; the rest come in the next round
- Still open: the first run, its review, and the results under `benchmark/results/<date>/`

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

## M8 – Dependabot and fallback 🟨

**Goal:** the tool doesn't care where the PR came from.

- Dependabot source adapter: every entry of `fetch-metadata`'s `updated-dependencies-json`, grouped updates included, Gradle plugins as marker coordinates, release notes from the PR body (`sources` module)
- Fallback source: PRs with the opt-in label `lector`; the changes are every resolved-version difference between base and head
- Source selection in the order Renovate, Dependabot, fallback, with a `source` input to override
- Companion-PR mode (`heal/<branch>`): rebuilt and force-pushed when the source PR changes, the same companion PR kept, closed when the source PR is closed, merged, or green
- Branch policy per source (Renovate in place, Dependabot and fallback companion), overridable with the `mode` input
- Documentation: the [setup guide](setup.md) covers Dependabot secrets, companion PRs, and the opt-in label
- Still open: the runs on a real Dependabot PR and a real labelled manual PR in a test repository on GitHub

**Done when:** Dependabot PRs and manual dependency PRs are fixed the same way as Renovate PRs.

## M9 – Second ecosystem: TypeScript 🟨

**Goal:** prove the adapter abstraction holds outside the JVM.

- `ecosystems:typescript` with `TypeScriptAdapter` (ecosystem `npm`, semver): npm, pnpm, Yarn classic, and Yarn Berry with Plug'n'Play, single packages and workspaces; Node 22 and 24 picked from `.nvmrc`, `.node-version`, or `engines.node`; package managers through Corepack, as `packageManager` pins them
- Verify: the frozen install, the `build` script of every workspace package another one depends on, `tsc` (`--build` with project references, else `--noEmit` per package), then Jest (with the image's `jest-junit`), Vitest, or the `test` script; reruns by test file and name
- No recipe step: OpenRewrite JavaScript is MSAL-licensed and runs only in the Moderne CLI ([licensing](licensing.md)). Verify leads straight to the fix loop
- Guardrails: tree-sitter TypeScript, TSX, and JavaScript queries; a blocklist for loosening the type check and leaving tests out, by added lines and by each build file's whole change; no edits to lockfiles, registry config, installed packages, build output, or generated code
- Grounding from `.d.ts` files (`exports` types, `types`, `index.d.ts`, `@types`), following re-exports, read from Yarn's zip archives under Plug'n'Play
- Resolved versions from all four lockfile formats, without an install; a build-file fix lets the lockfile follow, in the same commit
- Renovate's and Dependabot's npm updates map to `npm`; Node version PRs are skipped. `--npmrc`, `--yarnrc`, and the action's `npmrc` and `yarnrc` inputs
- Tool image: Node 22 and 24, Corepack 0.36, pnpm 12, Yarn 4 and 1 pre-cached, `jest-junit`; versions in `docker/lector/node` for Dependabot
- Shared with the JVM adapters through `ecosystems:shared`: the JUnit XML reader and the tree-sitter facts
- Four fixture repositories and tagged integration tests with the real toolchains (`./gradlew :ecosystems:typescript:integrationTest`)
- Benchmark: cases name their ecosystem, the report shows TypeScript apart from the JVM (fix loop only: npm has no recipes), and three TypeScript cases (real Renovate and Dependabot PRs on npm and Yarn 1, each checked in the tool image) join the corpus
- Still open: the run on a real Renovate PR in a TypeScript test repository on GitHub, and the first benchmark run with the TypeScript cases

**Changes to `core` in M9:**

| Change | Reason |
|---|---|
| `EcosystemAdapter.updateLockfiles`, called by the fix loop after a build-file edit, before the downgrade check; the files it changed go into the attempt's commit | A fix to `package.json` needs its lockfile in the same commit, or the next frozen install fails, and the downgrade check must read the versions the lockfile resolves after the edit. Maven and Gradle update nothing. The one port change TypeScript forced |
| `EcosystemAdapter.loosening`, called by the build-file blocklist with each build file before and after an attempt | The blocklist saw only added lines. In TypeScript, removing a line loosens: without the `test` script a package runs no tests, without `"strict": true` the type check is lax. Maven and Gradle run every test and check by default and return nothing (review D1) |
| None for "only files tracked by Git" (spec section 9) | The scope rule has rejected untracked files for every adapter since M4 (`Scope` in `FileRules.kt`) |

The lesson: the ports held for a second ecosystem. What they missed is that a build-file edit can change a second file the
build tool derives from it, and that removing a setting can loosen as much as adding one, where the safe behaviour is
opt-in.

**Done when:** a TypeScript repo runs through without changes to `core`. If `core` had to change, that's the lesson.

## M10 – API diff as grounding ✅

**Goal:** the model learns what changed between the old and the new version's API, not only what the new version has.

- `EcosystemAdapter.apiChanges(repo, change, diagnostics)`, default none; the fix loop puts the API changes of every change before every full API, each distinct text once, so trimming cuts the full APIs first. The prompt's section is `API`
- JVM (Maven and Gradle): the public API of both versions read from class files with the JDK's ClassFile API; seven kinds, decided in order: renamed package, moved class, removed class, changed signature, moved member (static, same name and erased parameters, searched in the whole group), rename candidate, removed member. Ambiguous changes are removed, never guessed. A typed value with every entry; limits only in the text
- Old JARs: Maven fetches the missing artifacts with `maven-dependency-plugin` `dependency:get` (one call per artifact, `-N`, no transitive dependencies, the run's settings, 2 minutes), once per group and version and run; Gradle reads them from any model read in the run, like the base commit's, without another Gradle run. Only when the errors refer to the group. Any failure means no API changes, never a failed run
- TypeScript: unchanged (a later milestone)
- Proof: PDFBox 2 → 3 (`PDDocument.load(File)` moved to `PDFParser.load(File)`) and SnakeYAML 1.33 → 2.3 (`Constructor(Class)` became `Constructor(Class, LoaderOptions)`) are red with qwen2.5-coder:7b before M10 and green with it; both joined the benchmark corpus

**Done when:** the two proof cases go from red to green through the API changes alone.

## M11 – Fix-loop yield 🟨

**Goal:** the fix loop keeps correct partial fixes, doesn't count environment failures against the update, and stops
spending builds on answers it already rejected. A local comparison of 7B, 14B, and 30B models found the model's size
barely mattered: correct fixes were rejected by the count-only progress check, and 12 of 16 retries repeated the attempt
before them.

- Uncovered compile errors count as progress with equal counts: every sent error is a compile error and gone, no new
  error lies in a line the edits wrote, no new build error without a location, no count higher, no error an earlier
  accepted attempt removed is back. Test failures keep the counts-only rule
- A build error without a location that the base commit has too (its checkout path aside) is pre-existing when the
  head's verify ran tests, and in Maven skipped no module. The base is verified at most once per run (`RunBase`); a
  failure or timeout there never ends the run. Every Maven command skips signing (`-Dgpg.skip=true`)
- A repeated answer (same edits as a rejected attempt on the cluster since the last accepted one, any order, trailing
  whitespace ignored) is not built: the model is asked once more at temperature 0.7 with a note; a second repeat
  rejects the attempt without a build
- An edit that can't be applied names its number and file, and shows the most similar lines of the committed file, the
  lines of several matches, or the tracked file of a guessed path (a failed edit, not a scope violation)
- A run that ends red suggests the built, guardrail-clean rejected attempt on its last commit with the lowest counts,
  as a diff marked "not applied, needs review": in `heal`'s text and JSON reports and in the PR comment, never committed
- The Benchmark workflow takes a `model` input (default `qwen2.5-coder:7b`)
- Still open: the benchmark runs `7b-before` (tool commit `2f543e1`), `7b-after`, and `14b-after` on GitHub-hosted
  runners, the review of both 7B runs, and the 14B's outcome for the default-model choice

**Done when:** on the cases valid in both 7B runs, the fix-loop modes have more green runs after than before, no case
and mode green before is red in all three repetitions after, and the share of reviewed runs accepted unchanged is not
lower.

## M12 – Simple setup 🟨

**Goal:** a repository on github.com gets lector-ai in three steps instead of an 80-line workflow.

- Reusable workflow `.github/workflows/lector.yml` (`workflow_call`): the trigger conditions, one run per PR (no bot name
  needed), the Ollama service, `fetch-metadata`, the App token, the checkout, the action, and the cleanup job; the
  action's inputs plus `runs-on`, `ollama-image`, `timeout-minutes`; the App's client ID and key as declared secrets
- The bot's identity from the App's slug: `lector pr --app-slug` and the action's `app-slug` input look up
  `<slug>[bot]` on github.com and build its noreply email. `--bot-name`/`--bot-email` stay for GitHub Enterprise
- Every run on a Renovate PR logs the value for `gitIgnoredAuthors`, the first one included
- `./gradlew build` checks that the reusable workflow, the action, the Ollama image, and the docs name the same version,
  and that the workflow offers the action's inputs with the same defaults
- Documentation: the [setup guide](setup.md) and the README lead with the short setup (a pre-filled link that creates
  the App, `gh secret set`, ten lines of workflow); the full workflow stays as the advanced setup
- Still open: the run in a test repository on github.com with only the short setup (Renovate, Dependabot, a labelled PR,
  closing, cancelling), and opening the pre-filled App link once

**Changes to `core` in M12:**

| Change | Reason |
|---|---|
| `UpdateSource.setupHint(identity)`, logged by the pipeline once a source takes the PR | Renovate needs the bot's email in its configuration before lector-ai's first push. The text is the source's, so `core` names no bot |

**Done when:** a Renovate PR and a Dependabot PR in a test repository, set up only with the short setup, get the same
result as with the M8 workflow.

## M13 – Generated rewrite rules ⬜

**Goal:** fix the mechanical part of an update without the model.

- Turn the unambiguous entries of M10's typed API diff (moved class, renamed package, moved member) into generated ast-grep rules and run them before the fix loop
- Needs the diff before the fix loop: today it is built from the errors of each attempt

## Later ⬜

Not planned in detail yet. Prioritize after M13 based on demand and benchmark results.

- More ecosystems: Python, .NET, Terraform, config files (YAML/XML/properties)
- GitLab CI support
- Embedding fallback for recipes without structured metadata
- Better error clustering, and grounding from a migration-guide index per dependency
- API changes for TypeScript (old vs. new `.d.ts`)
- A linter bound to Maven `verify` that fails only on the model's code still blocks the attempt (one that fails on the
  base too is pre-existing since M11)
- From M11: the full class name of a missing class as a hint; mapping a failing test without a location to its source
  file (per adapter); the budget defaults (attempts without progress stays 2), measured again on M11's benchmark
- From the first runs on lector-sample-app (October 2026):
  - A minor version past the newest recipe (Spring Boot 4.1.1, `rewrite-recipe-bom` 3.37.0 has `UpgradeSpringBoot_4_0`)
    gets no recipe, because a recipe matches only if its target contains the PR's target (concept section 4). Running
    the closest lower recipe of the same major as a first step would change that rule. A newer BOM may simply have the
    recipe
  - The model sends edits with empty search text to append to a file (`application.properties`). They are rejected, as
    the edit format says; the prompt could say so, or the format could allow appending
  - The 7B model on a 2-CPU runner can take longer than the 15-minute request timeout for a large first request. Such a
    run now ends with its report (`modelUnanswered`); a configurable timeout or less grounding on the first request
    would let it try
