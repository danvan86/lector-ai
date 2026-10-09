# Concept Paper: Hybrid AI-Powered Dependency Migration

> Status: Draft v3. Revised from the original concept based on review.

## 1. Objective

Automated dependency updates (Renovate, Dependabot) are routine for patch and minor versions. Major upgrades still regularly break the build, and today a developer has to fix them by hand. The two existing approaches each fail in a different way:

- **Rule-based tools (OpenRewrite)** are precise but incomplete. They miss edge cases, project-specific setups, and breaking changes that no recipe covers yet.
- **Generative AI alone** hallucinates when it tries to migrate a whole codebase. It runs into context and budget limits, and it doesn't know APIs released after its training cutoff.

**Solution:** a hybrid tool that runs entirely inside the CI/CD pipeline.

1. **OpenRewrite** does the deterministic structural transformations wherever a recipe exists.
2. A **local LLM** (via Ollama) fixes the remaining errors in small, grounded, verifiable steps.
3. The result is a reviewable PR in which nothing the update broke is left broken, or that clearly reports what is left.

**Where it runs: in the CI/CD pipeline, and nowhere else.** This is not a developer tool. Nobody installs it, nobody runs it on a laptop, and nobody opens an IDE for it. It is a pipeline job that reacts to a dependency-update PR and leaves behind commits and a report. "Local" in this document always means *on infrastructure you control* — a self-hosted runner with an Ollama sidecar — never *on a developer machine*. The CLI exists because the container image and the GitHub Action need an entry point; it is not the product.

### Design principles

| Principle | Meaning |
|---|---|
| **Technology-agnostic** | Works for every technology OpenRewrite supports. All language-specific logic lives in ecosystem adapters. |
| **Source-agnostic** | Works with Renovate, Dependabot, and any other PR that changes a dependency. |
| **Deterministic where possible, AI where necessary** | Recipe selection, verification, and guardrails never depend on an LLM. |
| **Grounded, not guessing** | The LLM always receives the real API surface of the new version and the relevant release notes. |
| **"Green" means nothing the update broke is left** | Verification runs the full test and validation suite, not only compilation. But only failures caused by the update count: compile errors always, a failing test only if it passes on the base commit. Pre-existing and flaky failures are reported, never fixed (section 5). |
| **Self-hosted and private** | Runs on your own CI infrastructure. Source code and build logs never leave it. |
| **Transparent** | Every change is traceable, attributed, and reviewable. Nothing is auto-merged. |

## 2. Architecture overview

The system has two independent extension axes: **update sources** (where the change comes from) and **ecosystems** (which technology is affected). The core never knows which bot or language it is working with.

```
Update source (Renovate | Dependabot | Fallback)
        ↓  DependencyChange[] + release notes + branch policy
Recipe index (deterministic, built from recipe metadata)
        ↓  recipe (optional)
Ecosystem adapter (JVM | JS/TS | Python | .NET | Terraform | Config …)
        ↓  run recipe → verify → Diagnostic[]
Fix loop (grounded, diff-based edits, guardrails, budget)
        ↓  Edit[]
Git output (in-place commits | companion PR) + PR report
```

- A new bot means one new **source adapter**.
- A new technology means one new **ecosystem adapter**.
- A new Git platform means one new **Git host adapter**.
- The core, fix loop, and guardrails stay unchanged.

### 2.1 Ports and adapters

The core defines interfaces (ports) for every extension point. Adapters implement them in their own modules. The core depends on no adapter.

```
                         ┌──────────────────────────────────────────┐
  Renovate ───┐          │                  core                    │          ┌─── Maven
  Dependabot ─┼──▶ UpdateSource                         EcosystemAdapter ◀──┼─── Gradle
  Fallback ───┘          │                                          │          └─── TypeScript
                         │   data model · pipeline · fix loop       │
                         │   guardrails · budgets                   │
  Ollama ────────┬──▶ LlmClient                               GitHost ◀────────── GitHub / GitLab
  FakeLlm (tests)┘       │                                          │
                         │               RecipeIndex ◀────────────────── recipes (OpenRewrite BOM)
                         └──────────────────────────────────────────┘

  ──▶ Port  =  "implements the interface that core defines"
```

| Port | Implemented by | Purpose |
|---|---|---|
| `UpdateSource` | `sources` (Renovate, Dependabot, Fallback) | Turn a PR into `DependencyChange[]`, release notes, and a branch policy |
| `EcosystemAdapter` | `ecosystems:*` (JVM, TypeScript, …) | Detect, run recipe, verify, ground, editable scope, language support for guardrails |
| `RecipeIndex` | `recipes` | Find the recipe for a `DependencyChange` |
| `LlmClient` | `llm` (Ollama), fake in tests | Turn grounded context into `Edit[]` |
| `GitHost` | `git` (GitHub, later GitLab) | Commits, PRs, comments, labels |

The pipeline itself is **not** an extension point. It is a fixed sequence in the core. Guardrails are an internal list of rules (see section 7), not a public extension point.

**Module dependencies.** Every module points to `core`, and `core` points to nothing. `cli` is the composition root: the only place that knows the concrete adapters and wires them into the core.

```
                 cli   (composition root)
     ┌────────┬───┴────┬─────────┬─────────┐
     ▼        ▼        ▼         ▼         ▼
  sources  ecosystems  llm    recipes     git
     │     ├─ jvm       │        │         │
     │     └─ ts …      │        │         │
     └────────┴────────┴────┬────┴─────────┘
                            ▼
                          core
```

Each ecosystem is its own submodule (`ecosystems:jvm`, `ecosystems:typescript`, …), so it brings only its own dependencies.

### 2.2 Plugin model

Adapters are **compile-time plugins**: Gradle modules that implement a core interface, registered in a plain list in `cli`. There is no dynamic loading and no versioned plugin API.

| Level | How | When |
|---|---|---|
| 1. Compile-time modules | `cli` wires adapters in a list | **Now** |
| 2. `ServiceLoader` | Adapters register via `META-INF/services` | Different image variants (e.g. JVM only) |
| 3. Runtime plugins | Separate JARs or processes, versioned plugin API | Third parties ship their own adapters |

Why not runtime plugins now:

- Until a second ecosystem exists, the right interface is unknown. A stable plugin API would freeze a guess.
- Loading foreign code into a tool with write access to repos is a supply-chain risk, especially in regulated environments. Everything ships in one container image anyway.
- The heavy, language-specific work already runs out of process: adapters drive external toolchains (`mvn`, `tsc`, `pyright`, …).

If an adapter turns into mostly glue around another runtime (e.g. the TypeScript compiler API, Python `inspect`), it calls a small script in that runtime. An adapter as a separate process with a JSON protocol is only worth it once that glue dominates.

### 2.3 Normalized data model

| Type | Fields | Purpose |
|---|---|---|
| `DependencyChange` | `ecosystem, package, fromVersion, toVersion, updateType` | Neutral description of a dependency update |
| `Diagnostic` | `file, line, column, code, message, severity, tool` | Normalized compiler, type-checker, and validator output. SARIF is the preferred exchange format. |
| `Edit` | `file, search, replace` | Language-neutral, verifiable code change |
| `Verification` | `causedByUpdate, preExisting, flaky, unattributed` (each `Diagnostic[]`) | Result of verify plus attribution (section 5). Only `causedByUpdate` is the tool's job. |

## 3. Update-source adapters

Each source turns its PR into the same normalized input: `DependencyChange[]`, release notes, and a branch policy.

| | Renovate | Dependabot | Fallback (any PR) |
|---|---|---|---|
| **Dependency info** | Branch name, PR body, `postUpgradeTasks` | `dependabot/fetch-metadata`: `package-ecosystem`, `previous-version`, `new-version`, `update-type`; `updated-dependencies-json` for grouped PRs | Ecosystem adapter compares manifests and lockfiles between base and head |
| **Release notes** | PR body | PR body (release notes, changelog, commits) | Registry or GitHub releases |
| **Recognized by** | Bot author plus branch prefix `renovate/` | PR author `dependabot[bot]` plus branch prefix `dependabot/` | — |
| **Default branch policy** | In-place commits (with `gitIgnoredAuthors`) | Companion PR | Companion PR |
| **Platforms** | GitHub, GitLab, and others | GitHub | Any |

The fallback makes the tool independent of whoever opened the PR, including Snyk, manual bumps, and future bots.

### 3.1 Source-specific details

**Renovate**
- Add the bot identity to `gitIgnoredAuthors`. Otherwise Renovate stops maintaining the branch after foreign commits.
- Map Renovate `manager` names to ecosystem adapters with a lookup table.

**Dependabot**
- Dependabot stops rebasing after foreign commits, and `@dependabot rebase` or `recreate`, or a superseding version, overwrites them. Two strategies, configurable:
  - **In-place:** accept that Dependabot no longer rebases. The pipeline is idempotent and reruns on every new head SHA.
  - **Companion PR (default):** the Dependabot branch stays untouched. The tool opens a stacked PR `heal/<dependabot-branch>` and closes and regenerates it when Dependabot updates or supersedes its PR.
- Workflows triggered by Dependabot get a read-only `GITHUB_TOKEN` and can only read **Dependabot secrets**. They need `permissions: contents: write` or a GitHub App token whose key is stored as a Dependabot secret.
- Map `package-ecosystem` values (`maven`, `gradle`, `npm`, `pip`, `nuget`, `terraform`, …) to ecosystem adapters with a lookup table.

## 4. Recipe selection: deterministic and maintenance-free

No LLM picks recipes. The index is derived from the recipes' own metadata:

1. Load all recipe descriptors from the configured recipe BOM (`Environment.builder().scanRuntimeClasspath().build().listRecipeDescriptors()` or `rewrite:discover`).
2. Walk each composite recipe tree and collect the typed upgrade leaves (`UpgradeDependencyVersion`, `UpgradeParentVersion`, and their equivalents for npm, NuGet, …) with their options (`groupId`, `artifactId`, `newVersion`, …).
3. Build the index `(ecosystem, package, target version) → top-level recipe`. Choose the most specific recipe that covers the change without overshooting the target version.
4. Rebuild the index automatically whenever the recipe BOM is updated.

**Fallback:** for recipes without structured upgrade leaves, use embedding search over name and description. Any match is checked against the index before running. Recipe names are never generated.

**The recipe is optional.** If no matching recipe exists, the pipeline goes straight to verify and the fix loop. This covers every Renovate and Dependabot PR, including ecosystems OpenRewrite doesn't support (Go, Rust, PHP, …). OpenRewrite is a quality boost where available, not a prerequisite.

## 5. Ecosystem adapters

Every adapter implements the same interface:

| Responsibility | JVM (Maven/Gradle) | JS/TS | Python | .NET | Terraform/HCL | Config (YAML/XML/properties) |
|---|---|---|---|---|---|---|
| **Run recipe** | Maven plugin via CLI coordinates / Gradle init script (no build file edits) | OpenRewrite JS* | OpenRewrite Python* | OpenRewrite C#* | rewrite-hcl | rewrite-yaml/xml/properties |
| **Verify** | `verify` / `check` | `tsc --noEmit` + tests | pyright/mypy (JSON) + pytest | `dotnet build` + `dotnet test` | `terraform validate -json` | Schema validation, `helm lint`, app startup |
| **Grounding** | `javap` / `-sources.jar` | `.d.ts` files in `node_modules` | `.pyi` stubs / `inspect` | XML docs / assembly metadata | `terraform providers schema -json` | JSON schemas, config metadata |
| **Excluded from editing** | `target/generated-sources`, `build/generated` | `dist/`, generated clients | Generated protobuf/OpenAPI code | `obj/`, `*.g.cs` | `.terraform/` | Rendered templates |
| **Language support (guardrails)** | tree-sitter Java/Kotlin | tree-sitter TypeScript/JavaScript | tree-sitter Python | tree-sitter C# | tree-sitter HCL | tree-sitter YAML/XML |

\* Check maturity and licensing (see section 9).

Interface outline:

```
EcosystemAdapter
  detect(repo)                       → bool
  verify()                           → Diagnostic[]   (build + tests/validation)
  runTests(tests)                    → Diagnostic[]   (only the given tests: rerun, baseline)
  ground(change, diagnostics)        → Context        (API surface, docs)
  editableScope()                    → Glob[]         (excludes generated code)
  language                           → LanguageSupport (tree-sitter grammar + queries)

  Optional capabilities (absent = the step is skipped):
  recipeRunner?.run(recipe)          → Result
  manifestDiff?.diff(base, head)     → DependencyChange[]   (fallback source)
```

The recipe runner is optional because the recipe is optional (section 4). The manifest diff is only needed by the fallback source.

### Attribution: what did the update break?

A failing test does not mean the update broke it. It may be a pre-existing bug, a developer issue, or a flaky test. The tool must only work on failures the update caused, so every verify result is attributed. The logic is language-neutral and lives in the core; the adapter only runs builds and tests.

```
verify on the PR head
   │
   ├── compile errors (main or test code) ─────────────────▶ caused by update
   │
   └── failing tests ──▶ rerun once ──passes──▶ flaky (reported, ignored)
                             │ fails again
                             ▼
                    same tests on the base commit (separate worktree)
                       ├── fail there too ──▶ pre-existing (reported, ignored)
                       └── pass there ──────▶ caused by update
```

- **Compile errors always count**, including tests that no longer compile. Code that compiled before the update and doesn't after is the update's doing.
- **The base commit is only tested when needed:** only if the head has failing tests, and only those tests. In the common case there is no extra build.
- **Without a known base commit**, failing tests are *unattributed*: reported, but not counted.
- **Only failures caused by the update** reach the fix loop, fail the job, and appear as "left to fix" in the report. Everything else is listed separately, so reviewers still see it.

### Difficulty by technology class

- **Statically typed languages** (Java, Kotlin, C#, TypeScript, Go): strong compiler signal. The approach works best here.
- **Dynamic languages** (Python, plain JS, Ruby): "no errors" is a weak signal. These depend on a type checker and test coverage, so expect lower success rates and stricter review.
- **Config and infrastructure as code:** breaking changes often only appear at runtime or deploy time. Verification is adapter-specific (`terraform plan`, application startup).

## 6. The fix loop

### 6.1 Grounding is the key factor

A local model often doesn't know the new version of an API, because it was released after the model's training cutoff. Without context, the model guesses. So every request contains:

- **The real API surface of the new version** from the ecosystem adapter (signatures, types, schemas)
- **Release notes and the migration guide** from the update-source adapter (the PR body) plus a per-dependency index
- **The OpenRewrite diff**, so the model can see which migration pattern has already been applied
- **Only the relevant code section** around the error, not the whole file and not the whole project

### 6.2 Error handling

- **Cluster before fixing:** errors are grouped by missing symbol or package. One root cause, such as a missing module or changed artifact coordinates, can produce hundreds of follow-on errors. Root causes are fixed first.
- **Build-file fixes:** many breaking changes require changes in `pom.xml`, `package.json`, or `*.csproj`, not in source code. The loop allows edits to build files.
- **Generated code** is never edited. Fixes belong in the generator configuration or the source annotations.
- **Only failures caused by the update** are in scope (section 5). Pre-existing and flaky test failures are never touched.

### 6.3 Edit format

- The LLM returns **search/replace edits**, never whole files. Small models drop lines and truncate.
- Output is enforced through **structured output** (Ollama `format` with a JSON schema). Code fences are stripped anyway.
- Edits that don't apply cleanly are rejected.

### 6.4 Budgets

- Maximum attempts per error cluster (e.g. 3)
- Global limit on attempts and wall-clock time per run
- Stop once no progress is made (the number of failures caused by the update doesn't drop)

## 7. Guardrails

The easiest way for an LLM to make the build green is to delete code. The following checks are deterministic and language-agnostic, built on **tree-sitter**:

| Check | Rule |
|---|---|
| Symbol comparison | No deleted functions or methods unless the recipe or the API change requires it |
| Test protection | No deleted tests, no removed or weakened assertions, no new skip/ignore annotations |
| Test count | The number of tests executed must not drop compared to a baseline. This needs a test run, so it is checked after verify, not per edit. Where the baseline comes from is decided in M4. |
| Stubbing | No new `return null`, empty bodies, or `TODO` placeholders in place of logic |
| Suppression | No new suppressions such as `@SuppressWarnings`, `// @ts-ignore`, `# type: ignore`, `#pragma warning disable` |
| Diff size | Maximum change size per fix |
| Scope | Edits only within the adapter's `editableScope()` |

An edit that breaks a rule is rejected and counts as a failed attempt.

**Where the language knowledge lives.** The rules are generic and live in the core. What counts as a method, a test, an assertion, or a suppression in a given language comes from the ecosystem adapter's `LanguageSupport`: a tree-sitter grammar plus query files. A new language needs a grammar and queries, not new guardrail logic.

```
Guardrails (core, generic)                   EcosystemAdapter (jvm)
  "no deleted methods"      ── asks ──▶       LanguageSupport
  "no removed assertions"                       grammar: tree-sitter-java
  "no new suppressions"                         queries: methods.scm, tests.scm,
  "no new skip annotations"                              asserts.scm, suppressions.scm
```

Each rule is a small `Guardrail` implementation in a list, tested on its own.

## 8. CI/CD integration and Git output

### 8.1 Trigger

- Run only for **major/minor updates or failed builds**. Patch updates that build cleanly don't need the tool.
- Use `pull_request`, **not** `pull_request_target` with a checkout of the PR code.
- **Security:** don't gate on `github.actor`. The actor is whoever triggered the most recent event, and this has been exploited. Check instead that the PR author is the expected bot **and** the head repo is the same repo, not a fork.

### 8.2 Tokens

- Pushes made with `GITHUB_TOKEN` don't trigger new workflow runs, so required checks wouldn't run on the fix commit. Use a **GitHub App token**.
- Prevent a loop where the bot's own commit triggers the fix job again.

### 8.3 PR structure

Separate, traceable commits:
1. The bot's version bump (Renovate or Dependabot)
2. The OpenRewrite recipe output
3. One commit per AI fix, with the error it addressed and a one-line rationale

Also:
- Label the PR `ai-assisted`. **It is never auto-merged.**
- **Partial results:** if the fix loop fails, the OpenRewrite commit is still pushed and the remaining diagnostics are posted as a PR comment.

### 8.4 Infrastructure

- **Orchestrator:** GitHub Actions (self-hosted runners) or GitLab CI
- **Ollama** runs as a network-isolated sidecar service. The model weights are **baked into the runner image**, so nothing is downloaded per job.
- **Model:** a coding model that is strong across many languages (e.g. the Qwen-Coder family). On CPU-only runners, 7B is slow and anything larger is impractical, so GPU runner availability is the real infrastructure question.
- **No cloud data leakage:** source code and logs stay inside your own infrastructure.

## 9. Licensing and maturity

OpenRewrite support is not equally mature everywhere:

- **Mature:** JVM (Java, Kotlin, Groovy), Maven/Gradle, XML/YAML/JSON/properties, HCL
- **Newer, partly tied to the Moderne CLI:** JavaScript/TypeScript, Python, C#

Some recipe modules have moved from Apache 2.0 to the **Moderne Source Available License** (MSAL) or proprietary terms. Before supporting an ecosystem, check per module:
- Which license applies, and is our use covered?
- Can it run without the Moderne CLI?

**State of the check** (Maven Central and npm, October 2026):

| License | Modules |
|---|---|
| Apache 2.0 | `rewrite-core`, `rewrite-java`, `rewrite-maven`, `rewrite-maven-plugin`, the Gradle plugin, `rewrite-recipe-bom`, `rewrite-java-dependencies` |
| MSAL | `rewrite-spring`, `rewrite-migrate-java`, `rewrite-testing-frameworks`, `rewrite-javascript` / `@openrewrite/rewrite` |

The engine and the build plugins are Apache 2.0. Most recipes that migrate code are under MSAL. JavaScript/TypeScript recipes only run through the Moderne CLI or Platform, and the public catalog has no migration recipes. So the TypeScript adapter runs without a recipe (M9).

**Distribution model and MSAL.** The tool is free, Apache 2.0, and self-hosted. Each organisation runs it on its own code. Moderne's licensing FAQ allows running MSAL recipes "on your own code", also for open-source projects. It prohibits commercialising them, offering them as a managed service, and "consulting/professional services where you charge people and run these recipes". This model stays compatible under these conditions:

- **MSAL artifacts are never shipped:** not in the image, the distribution, or a pre-filled cache. The organisation's own Maven or Gradle fetches them, so the organisation is the licensee and runs them on its own code (M2).
- **No paid service that runs the recipes:** no hosted version, and no paid setup, support, or consulting in which we run the tool on a client's code.
- **License notices stay visible:** the license report per recipe artifact (M2).

**Remaining risk.** The FAQ explains the license, but the license text governs, and it is broader. It prohibits "offering a product or service, the value of which derives to any extent from the value of the Software" and one "that accomplishes for users any purpose of the Software", and it doesn't require payment. A strict reading could cover a free tool whose recipe step runs MSAL recipes. We are licensees ourselves, because our tests and the benchmark run `rewrite-spring`. Before the first release (M5):

1. Ask Moderne for a written confirmation of the model above.
2. Have the confirmation and the license reviewed legally. Record the outcome in `docs/licensing.md`.

**If the tool is ever commercialised,** the MSAL recipe step needs a license from Moderne or has to be limited to Apache 2.0 recipes. The recipe is optional (section 4), so this is a configuration change, not a redesign.

## 10. Positioning against prior art

A caveat on the comparison below: the closest products are **general coding agents or modernization suites that a developer drives**, usually from an IDE, for a migration someone decided to start. Reducing them to "OpenRewrite plus an LLM" understates them — that is one capability inside a much larger product. The meaningful difference is not technique, which overlaps a lot, but **trigger and operating model**: this tool is an unattended pipeline job that reacts to dependency-update PRs nobody wants to think about.

| Product | What it actually is | Difference from this tool |
|---|---|---|
| **AWS Transform custom** (successor to Amazon Q Developer code transformation) | Managed, agentic cloud service for Java runtime upgrades, AWS SDK migration, and framework transitions | Cloud: code is uploaded to AWS. Metered, Java-focused, and started as a migration campaign rather than per dependency PR. Note the predecessor is being retired — Amazon Q Developer was a full coding agent whose `/transform` feature (and a scriptable transformation CLI) is end-of-support on 30 April 2027, with new subscriptions blocked since May 2026. |
| **GitHub Copilot app modernization** (Java and .NET, GA since September 2025) | Assessment reports, OpenRewrite-based transformation, build patching, dependency updates, and containerization, driven from VS Code, JetBrains, or Visual Studio agent mode | Cloud models, IDE-driven, developer in the loop for each step. It can *generate* a CI/CD pipeline as part of modernizing an app, but it does not itself run as one. |
| **Konveyor Kai** (Red Hat, open source) | An LLM guided by Konveyor static-analysis rules plus retrieval over previously solved migrations, shipped as an IDE extension with backend components | The closest in spirit: open source, and its model provider is configurable rather than fixed to one vendor's cloud. But it is driven by analyzer rules for platform migration, and it runs in the editor — not off a Renovate or Dependabot PR. |
| **Moderne** | Commercial platform for running OpenRewrite recipes across many repositories at once | Commercial and campaign-oriented, aimed at large-scale refactoring programmes rather than a single dependency bump in one repository's pipeline. |

**Where this one is actually different:** it is triggered automatically by routine dependency-update PRs, runs unattended in the pipeline with no IDE and no developer, and keeps source code and build logs on infrastructure you control. This is aimed especially at regulated environments.

Two honest qualifications. "CI-native" on its own is **not** a differentiator: AWS shipped a scriptable transformation CLI, and anything with a CLI can be put in a pipeline. Neither is "hybrid recipe plus LLM", which is close to the industry default by now. What is not otherwise on offer is the combination of an **automatic dependency-PR trigger**, **fully self-hosted inference**, and **no developer in the loop**.

## 11. Success measurement

A **benchmark per ecosystem** with real repositories (our own plus open-source projects), using these metrics:

- Build-green rate after the recipe alone vs. after the fix loop
- Share of runs with no remaining failure caused by the update (pre-existing and flaky failures tracked separately)
- Share of AI fixes accepted unchanged by reviewers
- Guardrail rejection rate
- Runtime and attempts per PR

Results are reported **separately per ecosystem**. "Works for Java" and "works for Python" will be very different numbers. The often-quoted 90/10 split between recipe and AI is a hypothesis this benchmark has to confirm or refute.

## 12. Benefits

- **Zero developer friction:** no OpenRewrite knowledge, no recipe maintenance, no local LLM setup. Developers get a reviewable PR in which nothing the update broke is left broken.
- **No mapping maintenance:** the recipe index is derived automatically from recipe metadata.
- **Broad coverage:** the fallback makes the tool independent of the update source and of OpenRewrite.
- **High reliability:** OpenRewrite does the structural heavy lifting, and the LLM only handles small, grounded, guarded fixes.
- **Privacy:** fully on-premise.
