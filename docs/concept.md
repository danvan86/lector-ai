# Concept Paper: Hybrid AI-Powered Dependency Migration

> Status: Draft v2. Revised from the original concept based on review.

## 1. Objective

Automated dependency updates (Renovate, Dependabot) are routine for patch and minor versions. Major upgrades still regularly break the build, and today a developer has to fix them by hand. The two existing approaches each fail in a different way:

- **Rule-based tools (OpenRewrite)** are precise but incomplete. They miss edge cases, project-specific setups, and breaking changes that no recipe covers yet.
- **Generative AI alone** hallucinates when it tries to migrate a whole codebase. It runs into context and budget limits, and it doesn't know APIs released after its training cutoff.

**Solution:** a hybrid tool that runs entirely inside the CI/CD pipeline.

1. **OpenRewrite** does the deterministic structural transformations wherever a recipe exists.
2. A **local LLM** (via Ollama) fixes the remaining errors in small, grounded, verifiable steps.
3. The result is a reviewable PR that builds and passes tests, or that clearly reports what is left.

### Design principles

| Principle | Meaning |
|---|---|
| **Technology-agnostic** | Works for every technology OpenRewrite supports. All language-specific logic lives in ecosystem adapters. |
| **Source-agnostic** | Works with Renovate, Dependabot, and any other PR that changes a dependency. |
| **Deterministic where possible, AI where necessary** | Recipe selection, verification, and guardrails never depend on an LLM. |
| **Grounded, not guessing** | The LLM always receives the real API surface of the new version and the relevant release notes. |
| **"Green" means tests pass, not just compiles** | Verification runs the full test and validation suite, not only compilation. |
| **Local and private** | Source code and build logs never leave your own infrastructure. |
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
- The core, fix loop, guardrails, and Git/PR handling stay unchanged.

### 2.1 Normalized data model

| Type | Fields | Purpose |
|---|---|---|
| `DependencyChange` | `ecosystem, package, fromVersion, toVersion, updateType` | Neutral description of a dependency update |
| `Diagnostic` | `file, line, column, code, message, severity, tool` | Normalized compiler, type-checker, and validator output. SARIF is the preferred exchange format. |
| `Edit` | `file, search, replace` | Language-neutral, verifiable code change |

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

1. Load all recipe descriptors from the configured recipe BOM (`Environment.scanRuntimeClasspath().listRecipeDescriptors()` or `rewrite:discover`).
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

\* Check maturity and licensing (see section 9).

Interface outline:

```
EcosystemAdapter
  detect(repo)                       → bool
  runRecipe(recipe)                  → Result
  verify()                           → Diagnostic[]   (build + tests/validation)
  ground(change, diagnostics)        → Context        (API surface, docs)
  editableScope()                    → Glob[]         (excludes generated code)
  diffManifests(base, head)          → DependencyChange[]   (fallback)
```

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

### 6.3 Edit format

- The LLM returns **search/replace edits**, never whole files. Small models drop lines and truncate.
- Output is enforced through **structured output** (Ollama `format` with a JSON schema). Code fences are stripped anyway.
- Edits that don't apply cleanly are rejected.

### 6.4 Budgets

- Maximum attempts per error cluster (e.g. 3)
- Global limit on attempts and wall-clock time per run
- Stop once no progress is made (the error count doesn't drop)

## 7. Guardrails

The easiest way for an LLM to make the build green is to delete code. The following checks are deterministic and language-agnostic, built on **tree-sitter**:

| Check | Rule |
|---|---|
| Symbol comparison | No deleted functions or methods unless the recipe or the API change requires it |
| Test protection | No deleted tests, no removed or weakened assertions, no new skip/ignore annotations |
| Test count | The number of tests executed must not drop |
| Stubbing | No new `return null`, empty bodies, or `TODO` placeholders in place of logic |
| Suppression | No new suppressions such as `@SuppressWarnings`, `// @ts-ignore`, `# type: ignore`, `#pragma warning disable` |
| Diff size | Maximum change size per fix |
| Scope | Edits only within the adapter's `editableScope()` |

An edit that breaks a rule is rejected and counts as a failed attempt.

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

Some recipe modules have moved from Apache 2.0 to the **Moderne Source Available License** or proprietary terms. Before supporting an ecosystem, check per module:
- Which license applies, and is our use covered?
- Can it run without the Moderne CLI?

## 10. Positioning against prior art

| Product | Approach | Difference |
|---|---|---|
| Amazon Q Developer code transformation | OpenRewrite plus an LLM fix loop (Java) | Cloud, IDE/console-driven |
| GitHub Copilot app modernization (Java) | OpenRewrite plus an LLM | Cloud, IDE-driven |
| Konveyor Kai (Red Hat) | Static analysis plus an LLM, open source | Closest open-source equivalent; focused on application migration |
| Moderne | Commercial OpenRewrite platform | Commercial, large-scale |

**Our differentiators:** fully local, CI-native, triggered by Renovate or Dependabot, technology-agnostic, and no IDE or developer involvement. This is aimed especially at regulated environments.

## 11. Success measurement

A **benchmark per ecosystem** with real repositories (our own plus open-source projects), using these metrics:

- Build-green rate after the recipe alone vs. after the fix loop
- Test-pass rate
- Share of AI fixes accepted unchanged by reviewers
- Guardrail rejection rate
- Runtime and attempts per PR

Results are reported **separately per ecosystem**. "Works for Java" and "works for Python" will be very different numbers. The often-quoted 90/10 split between recipe and AI is a hypothesis this benchmark has to confirm or refute.

## 12. Benefits

- **Zero developer friction:** no OpenRewrite knowledge, no recipe maintenance, no local LLM setup. Developers get a reviewable PR that builds and passes tests.
- **No mapping maintenance:** the recipe index is derived automatically from recipe metadata.
- **Broad coverage:** the fallback makes the tool independent of the update source and of OpenRewrite.
- **High reliability:** OpenRewrite does the structural heavy lifting, and the LLM only handles small, grounded, guarded fixes.
- **Privacy:** fully on-premise.
