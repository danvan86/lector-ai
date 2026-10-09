# M9 – Second ecosystem: TypeScript · Spec

Milestone: [M9 in features.md](../../../docs/features.md#m9--second-ecosystem-typescript-) · Decisions: [brainstorming.md](brainstorming.md) · Status: **Ready to implement**

## Goal

A Renovate or Dependabot PR in a TypeScript repository (single package or workspace, with npm, pnpm, or Yarn) goes through the same flow as a JVM repository: verify, fix loop with guardrails, commits, and the sticky comment. There is no recipe step.

**Why:** M1–M8 built and widened the pipeline on the JVM. M9 tests the central claim of the concept: the core knows no language (rule 1), so a new ecosystem is a new adapter. TypeScript has a strong compiler signal (concept section 5), so it is the best second ecosystem. It also exercises the "recipe is optional" path (concept section 4) for real, because OpenRewrite JavaScript can't be used (Q10).

## What to build

### 1. Module `ecosystems:typescript` and detection

- A new module `ecosystems:typescript` with a `TypeScriptAdapter`, registered in `cli`. It depends on `core` only (project-wise).
- **Detect:** `package.json` and `tsconfig.json` at the repository root.
- **Package manager** from the lockfile: `package-lock.json` → npm, `pnpm-lock.yaml` → pnpm, `yarn.lock` → Yarn. Classic vs. Berry comes from the lockfile format.
- **Workspaces:** npm and Yarn `workspaces` in `package.json`, pnpm `pnpm-workspace.yaml`. Every workspace package is type-checked and tested.
- **Tool errors (exit `2`)** with a clear message:
  - no lockfile, or more than one of the three;
  - a lockfile format older than the maintained majors of its manager (section 7);
  - a requested Node version that isn't in the image (section 2);
  - no `typescript` dependency in the root or a workspace package that has a `tsconfig.json`.

**Why:** each ecosystem is its own submodule with its own dependencies (concept 2.1). npm, pnpm, and Yarn classic and Berry are all supported (Q2), and so are workspaces from the start (Q6). Green means the full suite, so all workspace packages are verified. Like M7 Q2, the tool doesn't guess when the repo is ambiguous.

### 2. Toolchain: Node, package managers, registry, environment

- **Node in the image:** 22 and 24. Add 26 when it becomes LTS.
- **Node selection:** `.nvmrc` or `.node-version` (a version, a major, `lts/*`, or an LTS codename). Otherwise the highest image version that satisfies `engines.node`. Otherwise the newest LTS in the image. If no image version matches → tool error.
- **Package managers through Corepack:** Corepack is installed in the image (pinned). It honours `packageManager` in `package.json`. The current majors of pnpm and Yarn, plus Yarn classic, are pre-cached in the image. Any other pinned version is fetched from the configured registry at run time. Without `packageManager`: the image defaults. npm is the one bundled with the selected Node.
- **Registry config:** the project's own `.npmrc` and `.yarnrc.yml` are used as they are. Optional `--npmrc <file>` and `--yarnrc <file>` are installed as the user-level config for the run.
- **Environment:** every child process (install, build scripts, `tsc`, test runners, `yarn node`) gets the scrubbed, allowlisted environment from M5 section 3, extended with the Node, npm, Yarn, and Corepack registry variables (`NPM_CONFIG_*`, `YARN_*`, `COREPACK_NPM_*`). It never contains the token or `INPUT_*`.

**Why:** several Node versions follow the same approach as the JDKs in M5 (Q3). Node 20 is end of life. `packageManager` pins the manager, not Node. Corepack respects the project's pinned version the way the Maven and Gradle wrappers do (Q12). The run-time fetch is a read-only registry lookup (rule 7). The registry options mirror `--maven-settings` and `--gradle-init` (Q13). Lifecycle scripts and tests run dependency code, so the token must not be in their environment (Q14).

### 3. Install

- **First install** on the PR head is frozen: `npm ci`, `pnpm install --frozen-lockfile`, `yarn install --frozen-lockfile` (classic), `yarn install --immutable` (Berry).
- **Lifecycle scripts run** as in the project's own CI.
- **After a fix attempt that edited a build file** (section 8), the install runs without the frozen flag, so the lockfile can change. The lockfile change goes into the same commit as the edit. Then M3's downgrade check reads resolved versions (section 7).
- The **base worktree** for attribution (M1) gets its own frozen install. It only runs when tests fail.

**Why:** a frozen install is what the project's CI does with a Renovate or Dependabot lockfile. `package.json` is editable, and the lockfile follows it (Q7). Skipping scripts would skip the project's own codegen, and the compile errors that followed would count as caused by the update (Q14).

### 4. Verify and diagnostics

- **Stage 1 (type check):**
  1. The `build` script, if present, of every workspace package that another workspace package depends on, in dependency order.
  2. If the root `tsconfig.json` has `references`: `tsc --build`. Otherwise `tsc --noEmit -p <dir>/tsconfig.json` for the root and for every workspace package that has a `tsconfig.json`.
  - `tsc` is the project's own, run through the package manager, with `--pretty false`.
  - A failing build script yields its parsed `tsc` diagnostics if the output contains any. Otherwise it yields one `Diagnostic` without a location. Type checking still runs.
- **Stage 2 (tests),** only if stage 1 has no errors. For the root and every workspace package:
  - `test` script calls `jest` → `<pm> exec jest` in that directory, with the project's config, plus `--reporters=default --reporters=<image path to jest-junit>` and an output path for the report.
  - `test` script calls `vitest` → `<pm> exec vitest run` with `--reporter=default --reporter=junit` and an output file.
  - Any other `test` script → `<pm> test`. Exit code only: a failure becomes one `Diagnostic` without a location, carrying the console tail.
  - No `test` script → no tests for that package.
- **`runTests`:** reruns only the given tests: the test file plus a name filter (`-t`), in the package they came from.
- **Parsers:** `tsc` lines `file(line,col): error TSnnnn: message` become `Diagnostic`s, with the code as `TSnnnn`. Jest and Vitest failures come from the shared JUnit XML parser (moved out of `ecosystems:jvm` into a place both adapters can use, but not into `core`). Paths are relative to the repository root.
- **Cluster keys:** module name for "cannot find module" (`TS2307`); module plus symbol for missing exports (`TS2305`, `TS2614`, `TS2724`); property name for `TS2339`; otherwise code plus file.

**Why:** the same compile-first behaviour as the JVM (M1). Workspace packages that are linked through their build output would otherwise produce false compile errors. Those always count as caused by the update (Q16). Calling the runner directly gives control over the reporter, and single tests can be rerun for the flaky and base checks (Q4, Q15). `jest-junit` comes from the image, so the repository isn't changed. The `test` script is only used to find the runner.

### 5. Yarn Berry Plug'n'Play

- With PnP, every tool runs through `yarn` (`yarn tsc`, `yarn jest`, `yarn vitest`). Yarn's own TypeScript patch makes `tsc` work.
- For grounding (section 6), a single `yarn node` call to the PnP API returns the package's location inside its zip archive. The adapter reads `.d.ts` files from the zip with the JDK.
- Check during implementation that Jest loads `jest-junit` from the image path under PnP. If it doesn't, reopen this point instead of working around it.

**Why:** PnP is Yarn Berry's default mode, and full Yarn coverage was chosen (Q2, Q11). Forcing `node-modules` would make our build differ from the project's CI. The PnP call is the small "glue in another runtime" case from concept 2.2.

### 6. Grounding from `.d.ts`

- **Where the types are,** in this order: the `types` condition of the imported subpath in `exports`, then `types` / `typings`, then `index.d.ts`, then `@types/<name>`.
- Relative re-exports (`export * from './x'`, `export { a } from './x'`) inside the package are followed.
- Declarations are chosen with the tree-sitter TypeScript grammar: the symbols named in the diagnostic and the failing file's imports from that package. The result fits the M3 context budget. Nothing else is added.
- The files come from the installed tree (`node_modules`, or the zip under PnP). There are no extra downloads.

**Why:** tree-sitter needs no extra runtime and reuses `LanguageSupport` (Q5). The order matches how `tsc` resolves types for most packages. Modern packages export everything through barrel files, so re-exports must be followed (Q19). Whole `.d.ts` files are too large (Q5 C).

### 7. Version scheme and resolved versions

- **Ecosystem namespace:** `npm` for all three package managers.
- **`versionScheme`:** semver 2.0 ordering including pre-releases. `updateType` is major, minor, or patch.
- **Resolved versions** from the lockfile, without an install: the resolved version of every direct dependency of the root and of every workspace package. If workspaces resolve different versions, the highest wins. Parsers for `package-lock.json`, `pnpm-lock.yaml`, Yarn classic `yarn.lock`, and Yarn Berry `yarn.lock`.
- Only the lockfile formats of the currently maintained manager majors are supported. Which ones they are is checked during implementation and listed in the setup guide. Older formats → tool error.
- **No recipe runner.** The adapter has no `recipeRunner`, and the index has no `npm` entries, so the pipeline goes from verify straight to the fix loop.

**Why:** one namespace per package registry, as `maven` covers Gradle (M2, M7). Resolved versions feed M3's downgrade check and M8's fallback on base and head, and parsing the lockfile works on both without an install (Q20). OpenRewrite JavaScript is MSAL-licensed, only runs through the Moderne CLI, and its catalog has no migration recipes (Q10).

### 8. Fix loop and guardrails for TypeScript

- **`LanguageSupport`** with the TypeScript, TSX, and JavaScript grammars from `tree-sitter-ng`. It covers `.ts`, `.tsx`, `.mts`, `.cts`, `.js`, `.jsx`, `.mjs`, `.cjs`. Queries report:
  - methods: function declarations, class methods, and functions assigned to exported constants;
  - tests: `it` / `test` calls, grouped by `describe`;
  - assertions: `expect(…)` and `assert…` calls inside a test;
  - skip markers: `.skip`, `.todo`, `.only`, `xit`, `xtest`, `xdescribe`;
  - suppressions: `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `eslint-disable*`;
  - stubs: `return null`, `return undefined`, empty function bodies, and `TODO` placeholders in place of logic;
  - parse errors.
- **Build files:** every `package.json`, `tsconfig*.json`, `pnpm-workspace.yaml`, `jest.config.*`, and `vitest.config.*`.
- **Build-file blocklist** (added or changed lines):
  - in `tsconfig*.json`: `strict`, any `strict*`, or any `noImplicit*` set to `false`; `skipLibCheck: true`; `noCheck`; `checkJs: false`; new `exclude` entries;
  - in `package.json`: any change to the `test` script; `passWithNoTests`;
  - in `package.json` and the Jest/Vitest config files: new `testPathIgnorePatterns`, `modulePathIgnorePatterns`, or `exclude` entries, and `passWithNoTests`.
- **`editableScope`** excludes `node_modules/`, `.yarn/`, `.pnp.*`, lockfiles, `.npmrc`, `.yarnrc.yml`, `dist/`, `build/`, `out/`, `coverage/`, `.next/`, `**/generated/**`, `**/__generated__/**`, `*.generated.*`, `*.gen.ts`.

**Why:** a grammar plus queries is all a new language needs (concept section 7, M4). JavaScript config files and plain-JS sources occur in every TypeScript repo, and an unguarded file can't be accepted (Q18). The suppression list was chosen in Q8. `.only` silently skips every other test. Build files stay editable so that real fixes are possible, like `moduleResolution: "bundler"` or a split-out `@types` package. Loosening the compiler or skipping tests is blocked (Q7, Q17). Registry config and lockfiles are not the model's to edit, the same way the Gradle wrapper isn't (M7).

### 9. Core change: only files tracked by Git

- `core`'s scope rule (M4) rejects an edit to a file that isn't tracked by Git, for every adapter, next to the existing Git-ignored check.
- It lands as a separate commit before the TypeScript adapter, with its own guardrail test.
- `docs/features.md` records it under M9 as a deliberate core change that TypeScript didn't force.

**Why:** "tracked by Git" is a strong, language-neutral signal. It also catches generated output that isn't ignored (Q9). In core, it is one check next to an existing one, and the JVM benefits too (Q21). Naming it keeps the "done when" honest.

### 10. Sources, CLI, action, image, setup guide

- **Renovate lookup:** manager `npm` → `npm`. Node version bumps (`nvm`, `nodenv`, and `node` rows from `engines` or `volta`) map to no ecosystem and are skipped, like Gradle wrapper PRs (M7 Q18).
- **Dependabot lookup:** `npm_and_yarn` and `npm` → `npm`. Check on a real Dependabot PR which value `fetch-metadata` reports.
- **Fallback (M8):** works through the resolved versions from section 7. No new capability.
- **CLI:** `--npmrc <file>` and `--yarnrc <file>` on `verify`, `migrate`, `heal`, and `pr`.
- **Action:** `npmrc` and `yarnrc` inputs, passed through like the Maven settings file.
- **Tool image:** adds Node 22 and 24, Corepack (pinned), pre-cached pnpm and Yarn (Berry and classic), and `jest-junit` (pinned). There are no OpenRewrite JavaScript artifacts and no Moderne CLI.
- **Setup guide:** TypeScript requirements (lockfile, `typescript` dependency, Node 22/24, supported lockfile formats), registry setup (`npmrc`, `yarnrc`, `COREPACK_NPM_*` for mirrors), how to cache the package manager stores, and the limitations: no recipe step, only Jest and Vitest report per test, Nx/Turborepo task graphs are not used, Node version bump PRs are skipped.

**Why:** concept section 3 maps managers and ecosystems through lookup tables. The M5 action stays the only configuration surface. Everything the run needs is in the image (rule 6).

### 11. Licensing

- `docs/licensing.md` records the result of the Q10 check: `org.openrewrite:rewrite-javascript` and `@openrewrite/rewrite` are under the Moderne Source Available License, JS/TS recipes only run through the Moderne CLI or Platform, and the tool therefore runs no JavaScript recipes. Also the licences of Corepack, `jest-junit`, the Node distributions, and the `tree-sitter-ng` TypeScript and JavaScript grammars that the image or distribution now contains.

**Why:** Q1 asked for OpenRewrite JavaScript only if the license allowed it, and the check found that it doesn't. Everything new that we redistribute belongs with the other release blockers from M5.

### 12. Tests, end-to-end run, and benchmark

- **Parser tests:** `tsc` output, Jest and Vitest JUnit XML, and each lockfile format, including a workspace lockfile.
- **Adapter unit tests:** detection and each tool error from section 1, Node selection, the command lines per manager (frozen and unfrozen install, `tsc`, Jest, Vitest, `runTests`), the build order from section 4, the type resolution order from section 6, and the cluster keys.
- **Guardrail tests:** each query category from section 8, each blocklist entry, and each `editableScope` exclusion.
- **Fixture repos** under `src/test/resources/fixtures/` in `ecosystems:typescript`:
  - npm single package with Jest;
  - pnpm workspace with Vitest, where `app` imports `lib` through `lib`'s `dist/`;
  - Yarn classic;
  - Yarn Berry with PnP.
- **Tagged integration tests with real toolchains**, not part of `./gradlew build`:
  - each fixture installs, type-checks, and runs its tests;
  - a base/head fixture with one test failure caused by the update and one pre-existing failure;
  - grounding on the PnP fixture returns declarations read from a zip archive.
- **End-to-end:** a GitHub TypeScript test repository with Renovate and the documented M5 workflow on a standard runner with the 7B model. A real Renovate PR that breaks the type check gets a fix commit and a sticky comment with no manual steps.
- **Benchmark:** at least three TypeScript cases in the M6 corpus, with results reported separately from the JVM (concept section 11).
- **Core changes:** `docs/features.md` lists every change made to `core` during M9, with its reason. Expected: only section 9.

**Why:** fixtures cover each package manager and the workspace build case quickly. The test repository proves the M5 flow on a non-JVM repo. The benchmark gives TypeScript its own numbers. The list of core changes is the M9 "done when" (Q22).

## What not to do

| Don't | Why |
|---|---|
| Run OpenRewrite JavaScript recipes, ship `rewrite-javascript` or `@openrewrite/rewrite`, or add the Moderne CLI | MSAL, Moderne CLI only, no migration recipes (Q10). |
| Add `npm` entries to the recipe index | Nothing could run them (Q10). |
| Run codemods shipped by packages | Decided against (Q1 C). |
| Put TypeScript, npm, or Node knowledge into `core` | Rule 1. Section 9 is the only planned core change, and it is language-neutral. |
| Change a `core` port for TypeScript without listing it | The list of core changes is the "done when". An unlisted change hides the lesson. |
| Force `nodeLinker: node-modules` or reject PnP repos | Decided against (Q11). |
| Install fixed manager versions and ignore `packageManager`, or forbid run-time manager fetches | Decided against (Q12). |
| Configure registries only through environment variables | Decided against (Q13). |
| Install with `--ignore-scripts` | Decided against (Q14). |
| Pass the token or `INPUT_*` to any child process | Q14, M5 section 3. |
| Run `<pm> test` with appended reporter flags for Jest or Vitest | Decided against (Q15). |
| Add `jest-junit` or any other package to the target repo's `package.json` | The repo isn't changed to run verify (Q15). |
| Run every workspace package's `build` script, or skip the build step | Decided against (Q16). |
| Use Nx, Turborepo, or Lerna task graphs | Out of scope. Package manager workspaces only. |
| Verify only the workspace packages that depend on the changed package | Green means the full suite (concept section 1). |
| Make `tsconfig*.json` read-only, or editable without the blocklist | Decided against (Q17). |
| Block new `as any` / `as unknown as` casts | Decided against (Q8 B). |
| Leave `.js`/`.mjs`/`.cjs` files unguarded, or exclude them from editing | Decided against (Q18). |
| Use a Node script with the TypeScript compiler API for grounding, or send whole `.d.ts` files | Decided against (Q5). The PnP lookup in section 5 is the only Node call. |
| Read resolved versions through `npm ls` / `pnpm list` / `yarn info` | Decided against (Q20). |
| Put "tracked by Git" only into the TypeScript adapter | Decided against (Q21). |
| Handle Node version bump PRs | Skipped and documented, like Gradle wrapper PRs. |
| Support Bun or Deno | Not chosen in Q2. |
| Download Node at run time, or add Node 20 | Rule 6. Node 20 is end of life. |
| Add an Apache-only benchmark variant for the JVM | Decided against (Q23). |
| Add a third ecosystem | "Later" in features.md. |

## Acceptance criteria

The review agent checks every criterion and reports it as *met*, *not met*, or *can't verify*, with evidence.

| ID | Criterion | Covers | How to verify |
|---|---|---|---|
| AC1 | `./gradlew build` passes | all | Run it from the repo root: exit code `0` |
| AC2 | Module `ecosystems:typescript` exists, depends on `core` only (project-wise), and `cli` registers `TypeScriptAdapter` | 1 | `settings.gradle.kts`, module build file, adapter list in `cli` |
| AC3 | `core` has no project dependencies and no TypeScript-, npm-, or Node-specific code | 1, Don't | `core/build.gradle.kts` has no `project(`; case-insensitive search of `core/src/main` for `typescript`, `tsc`, `npm`, `pnpm`, `yarn`, `node_modules`, `jest`, `vitest`: no matches |
| AC4 | Detection succeeds with `package.json` + `tsconfig.json` at the root and fails without either | 1 | Unit test |
| AC5 | The package manager is detected from each of the three lockfiles, and Yarn classic vs. Berry from the lockfile format | 1 | Unit test |
| AC6 | No lockfile → exit `2`; two lockfiles → exit `2` | 1 | Adapter or CLI tests |
| AC7 | An npm `lockfileVersion: 1` lockfile → exit `2` naming the format | 1, 7 | Adapter test |
| AC8 | A package with a `tsconfig.json` but no `typescript` dependency → exit `2` | 1 | Adapter test |
| AC9 | Node selection: `.nvmrc` `22` → 22; `engines.node` `>=18` → 24; nothing declared → 24; `.nvmrc` `20` → exit `2` | 2 | Unit tests |
| AC10 | `packageManager` `pnpm@<version>` makes the install run that pnpm version through Corepack | 2 | Unit test on the command line or environment; tagged integration test |
| AC11 | `--npmrc` and `--yarnrc` files are installed as user-level config for every package-manager call | 2, 10 | Unit test |
| AC12 | A child process started by the adapter sees neither the token nor any `INPUT_*` variable | 2 | Unit test on the process runner |
| AC13 | The first install uses the frozen command for each manager (`npm ci`, `--frozen-lockfile`, `--immutable`) and no `--ignore-scripts` | 3, Don't | Unit tests on the command lines |
| AC14 | After a fix attempt that edits `package.json`, the install runs unfrozen and the lockfile change is in the same commit | 3 | Fix-loop test with a fake LLM on a temp Git repo, or tagged integration test |
| AC15 | Stage 1 runs the `build` script of `lib` (depended on by `app`) before type checking, and doesn't run `app`'s `build` script | 4 | Unit test on the planned commands for the pnpm workspace fixture |
| AC16 | With project references at the root, stage 1 runs `tsc --build`; otherwise `tsc --noEmit -p` per package with a `tsconfig.json` | 4 | Unit tests |
| AC17 | Stage 2 runs only if stage 1 has no errors | 4 | Unit or integration test |
| AC18 | Jest runs with `jest-junit` from the image path, and Vitest with its `junit` reporter; neither changes the repo's `package.json` | 4, Don't | Command line tests; tagged test checks `git status --porcelain` after verify |
| AC19 | A package whose `test` script calls neither Jest nor Vitest runs `<pm> test`, and a failure becomes one `Diagnostic` without a location | 4 | Unit test |
| AC20 | `runTests` runs only the given test (file plus `-t` filter) in its package | 4 | Unit test on the command line |
| AC21 | `tsc --pretty false` output is parsed into `Diagnostic`s with file, line, column, `TSnnnn` code, and message, with paths relative to the repo root | 4 | Parser test on captured output from a workspace |
| AC22 | The JUnit XML parser is shared by both adapters and doesn't live in `core` | 4 | Module sources; no copy of the parser in `ecosystems:typescript` |
| AC23 | Cluster keys group `TS2307` by module, `TS2305` / `TS2614` / `TS2724` by module and symbol, and `TS2339` by property | 4 | Unit tests |
| AC24 | Under PnP, verify runs `tsc` and the test runner through `yarn` | 5 | Unit test on the command lines; tagged PnP integration test |
| AC25 | Grounding on the PnP fixture returns declarations read from a zip archive | 5, 6 | Tagged integration test |
| AC26 | Type resolution follows `exports` types → `types`/`typings` → `index.d.ts` → `@types/<name>` | 6 | One unit test per step on small fixture packages |
| AC27 | Grounding follows a relative `export * from` to the declaring file | 6 | Unit test |
| AC28 | Grounding uses no Node script; the only Node call in the adapter outside verify and install is the PnP location lookup | 6, Don't | Adapter sources |
| AC29 | `versionScheme` orders `1.0.0-rc.1` < `1.0.0` < `1.0.1` < `1.1.0` < `2.0.0` and derives major/minor/patch | 7 | Unit tests |
| AC30 | Resolved versions are read from each of the four lockfile formats, including the direct dependencies of workspace packages | 7 | Parser tests on fixture lockfiles |
| AC31 | The adapter has no `recipeRunner`, and the generated index has no `npm` entries | 7, Don't | Adapter source; index test or index report |
| AC32 | `ecosystems:typescript` uses `tree-sitter-ng` with the TypeScript, TSX, and JavaScript grammars | 8 | Version catalog and module build file |
| AC33 | Each query category (methods, tests, assertions, skip markers, suppressions, stubs, parse errors) is reported for a TS, a TSX, and a JS sample | 8 | `LanguageSupport` unit tests |
| AC34 | `.only`, `.skip`, `.todo`, `xit`, `xtest`, and `xdescribe` each count as skip markers | 8 | Unit tests |
| AC35 | `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, and `eslint-disable` each count as suppressions; `as any` doesn't | 8, Don't | Unit tests |
| AC36 | Each `tsconfig` blocklist entry (`strict*` / `noImplicit*` to `false`, `skipLibCheck: true`, `noCheck`, `checkJs: false`, new `exclude`) rejects a matching edit | 8 | One guardrail test each |
| AC37 | A change to the `test` script, or a new `testPathIgnorePatterns`, `modulePathIgnorePatterns`, `exclude`, or `passWithNoTests` in `package.json`, `jest.config.*`, or `vitest.config.*`, is rejected | 8 | Guardrail tests |
| AC38 | An edit adding `"moduleResolution": "bundler"` to `tsconfig.json` is not rejected | 8 | Guardrail test |
| AC39 | An edit to a lockfile, `.npmrc`, `.yarnrc.yml`, or a file under `node_modules/`, `.yarn/`, `dist/`, or `**/generated/**` is rejected by the scope rule | 8 | Guardrail tests |
| AC40 | `core`'s scope rule rejects an edit to an untracked, non-ignored file, for any adapter | 9 | Guardrail test in `core` on a temp Git repo |
| AC41 | The tracked-by-Git rule is in its own commit, before the first commit that adds `ecosystems:typescript` | 9 | `git log` |
| AC42 | The Renovate lookup maps `npm` to `npm`, and an `nvm` PR produces no `DependencyChange` and is skipped with exit `0` | 10 | Parser and pipeline tests |
| AC43 | The Dependabot lookup maps `npm_and_yarn` and `npm` to `npm` | 10 | Parser test |
| AC44 | `verify`, `migrate`, `heal`, and `pr` accept `--npmrc` and `--yarnrc` | 10 | CLI tests or `--help` output |
| AC45 | `action/action.yml` has `npmrc` and `yarnrc` inputs | 10 | File content |
| AC46 | The tool Dockerfile adds Node 22 and 24, Corepack, pre-cached pnpm and Yarn, and `jest-junit`, all pinned. It adds no Node 20, no `rewrite-javascript`, no `@openrewrite/rewrite`, and no Moderne CLI | 10, Don't | Dockerfile content |
| AC47 | The setup guide covers the TypeScript requirements, registry setup including `COREPACK_NPM_*`, caching the package manager stores, and the limitations from section 10 | 10 | Docs content |
| AC48 | `docs/licensing.md` records the MSAL finding for OpenRewrite JavaScript and the licences of Corepack, `jest-junit`, Node, and the new grammars | 11 | File content |
| AC49 | The four fixtures from section 12 exist | 12 | `src/test/resources/fixtures/` in `ecosystems:typescript` |
| AC50 | The tagged TypeScript integration tests are not part of `./gradlew build` | 12 | Build configuration excludes the tag from the default test task |
| AC51 | The tagged base/head integration test finds one failure caused by the update and one pre-existing failure | 4, 12 | Run the tagged test task: passes |
| AC52 | A real Renovate PR in a TypeScript test repository got a fix commit and a sticky comment with no manual steps | 12 | Outside the repo: link to the PR, recorded in `docs/features.md` when M9 is set to ✅. Can't verify from the repo alone |
| AC53 | The M6 benchmark corpus has at least three TypeScript cases, reported separately from the JVM | 12 | Benchmark case definitions and report |
| AC54 | `docs/features.md` lists every `core` change made in M9 with its reason | 9, 12 | File content; compare with `git log -- core/` over the M9 commits |
| AC55 | No Nx, Turborepo, Lerna, Bun, or Deno support is added | Don't | Adapter sources: no detection or commands for them |

## Docs to update

- `docs/features.md`: set M9 to ✅ when done, with the link to the end-to-end PR, the list of core changes (section 9 and any other), and the benchmark result.
- `docs/concept.md` section 5: the JS/TS **Run recipe** cell becomes "none: OpenRewrite JS is MSAL and Moderne CLI only (see section 9)". **Verify** becomes "`tsc` (after building linked workspace packages) + Jest/Vitest". **Excluded from editing** adds lockfiles and registry config.
- `docs/concept.md` section 9: already records the MSAL finding (October 2026). Update the module table if a license has changed by then, and point to `docs/licensing.md`.
- `docs/concept.md` section 7: the scope rule also requires files to be tracked by Git.
- `docs/concept.md` section 3: the Renovate and Dependabot lookup tables map npm to `npm`, and Node version bumps to nothing.
- M5 setup guide: the TypeScript requirements and limitations from section 10.
- `AGENTS.md` **Commands**: the command for the tagged TypeScript integration tests, if it differs from the JVM one.
