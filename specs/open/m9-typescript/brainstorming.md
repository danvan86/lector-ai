# M9 – Second ecosystem: TypeScript · Brainstorming

Milestone: [M9 in features.md](../../../docs/features.md#m9--second-ecosystem-typescript-) · Status: **Closed**

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the concept

- TypeScript adapter in `ecosystems:typescript`: `tsc --noEmit` + tests, grounding from `.d.ts` files.
- Without a recipe if OpenRewrite JS isn't usable (licensing first).
- Done when a TypeScript repo runs through without changes to `core`. If `core` had to change, that's the lesson.

```
TypeScriptAdapter
  detect          package.json + tsconfig.json
  verify          install → tsc --noEmit → test runner
  ground          .d.ts of the updated package
  language        tree-sitter TypeScript/TSX + queries
  recipeRunner    none (for now)
```

## Already fixed by earlier milestones

- From M1: two verify stages (compile first, then tests), paths relative to the repo root, JUnit XML for test failures, build errors without a location become `Diagnostic`s, base-commit attribution in a separate worktree, exit codes `0` / `1` / `2`.
- From M2: the ecosystem is the package namespace. The adapter provides `versionScheme` and, optionally, `recipeRunner` and resolved versions. Recipe artifacts are never shipped.
- From M3: edits only touch files that exist. After a build-file edit, resolved versions are read and any downgrade rejects the attempt. The adapter says which files are build files.
- From M4: `LanguageSupport` via `tree-sitter-ng`. Scope, blocklist, and globs live in the adapter. Git-ignored files are rejected by `core`. A file that can't be checked is not accepted.
- From M5/M7: tool error (exit `2`) when the project needs a toolchain that isn't in the image. Configuration through action inputs only. Pass-through config files like `--maven-settings` and `--gradle-init`.
- From M8: the fallback source uses resolved versions (base vs. head). There is no `manifestDiff` capability.

---

## Round 1

### Q1 · Recipes for TypeScript

- [ ] **A · No recipe: verify + fix loop only** — *recommended.* Proves the "recipe is optional" path, and avoids the OpenRewrite JS licensing and Moderne CLI questions.
- [x] **B · OpenRewrite JavaScript** — Consistent with the JVM, but newer, partly tied to the Moderne CLI, license to check.
- [ ] **C · Official codemods where packages ship them (e.g. framework upgrade codemods)** — Real value for some packages, but a different, non-indexed recipe mechanism.

**Notes:** Go with B. But check the license first. → License checked, see Q10.

### Q2 · Package managers

- [ ] **A · npm and pnpm, detected from the lockfile** — *recommended.* Covers most projects. Yarn later if needed.
- [x] **B · npm, pnpm, and Yarn (classic + Berry)** — Full coverage, Yarn Berry (PnP) complicates `.d.ts` lookup.
- [ ] **C · npm only** — Simplest, misses a large share of repos.

**Notes:** B

### Q3 · Node.js version

- [x] **A · Several Node LTS versions in the image, chosen via `.nvmrc` / `engines` / `packageManager`, default to the newest LTS** — *recommended.* Same approach as JDKs in M5.
- [ ] **B · One Node LTS** — Smaller image, breaks projects pinned to other versions.

**Notes:** A

### Q4 · Tests

- [x] **A · Detect Jest or Vitest and run them with a JUnit XML reporter** — *recommended.* Reuses the JUnit XML parser from the JVM side. Other runners: only exit code and console output.
- [ ] **B · `npm test`, exit code only** — Works everywhere, no file/line for failures.
- [ ] **C · Type checking only in M9** — Fastest, but "green" wouldn't mean tests pass.

**Notes:** A

### Q5 · Grounding from `.d.ts`

- [x] **A · tree-sitter extracts the relevant declarations from the package's types entry** — *recommended.* No extra runtime, reuses `LanguageSupport`. Symbols come from the error and the imports.
- [ ] **B · Small Node script using the TypeScript compiler API** — Resolves re-exports and type aliases correctly, but adds a script in another runtime (the "glue" case from concept 2.2).
- [ ] **C · Include the whole `.d.ts`** — Trivial, but often far too large for the context window.

**Notes:** A

### Q6 · Monorepos (workspaces)

- [ ] **A · Single-package repos only in M9** — *recommended.* Proves the abstraction first. Workspaces bring multiple `tsconfig`s and install edge cases.
- [x] **B · npm/pnpm workspaces from the start** — Many real TypeScript repos are monorepos, so coverage would be higher.

**Notes:** B. With Q2 = B, this includes Yarn workspaces.

### Q7 · `package.json` edits and the lockfile

- [x] **A · `package.json` is editable; after an edit, reinstall and update the lockfile** — *recommended.* Mirrors build-file edits on the JVM. The install is a read-only registry fetch (rule 7).
- [ ] **B · `package.json` read-only in M9** — Safer, but blocks fixes like adding a split-out `@types` package.

**Notes:** A

### Q8 · Which suppressions the guardrails block

- [x] **A · `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `eslint-disable*`** — *recommended.* Clear-cut suppressions, no false positives.
- [ ] **B · A plus new `as any` and `as unknown as` casts** — Catches the most common TypeScript cheat, but sometimes legitimate.

**Notes:** A

### Q9 · Editable scope

- [x] **A · Only files tracked by Git, minus known generated patterns (`dist/`, `build/`, `*.generated.ts`, OpenAPI clients)** — *recommended.* "Tracked by Git" is a strong, language-neutral signal. It may even move into `core` for all adapters.
- [ ] **B · Glob list per adapter only** — Explicit, but misses project-specific generated folders.

**Notes:** A. Where "tracked by Git" lives: see Q21.

---

## Round 2

### Follows from round 1 and earlier milestones (not asked)

- **Ecosystem namespace `npm`** for all three package managers, the same way `maven` covers Gradle (M2, M7). Renovate's `npm` manager and Dependabot's npm ecosystem map to `npm`. I believe `fetch-metadata` reports it as `npm_and_yarn`; check. `versionScheme` is npm semver.
- **Node version bumps** (Renovate `nvm` / `node-version`, `engines` only) map to no ecosystem and are skipped, like Gradle wrapper PRs (M7 Q18).
- **Node in the image:** 22 and 24. 26 is added once it becomes LTS (I believe late October 2026). Selection: `.nvmrc` / `.node-version`, then `engines.node` (the highest image version that satisfies the range), then the newest LTS. A version that isn't in the image → tool error (exit `2`), like JDKs in M5. Q3 mentions `packageManager`. That field pins the package manager, not Node, so it belongs to Q12.
- **Install:** the first install is frozen (`npm ci`, `pnpm install --frozen-lockfile`, `yarn install --frozen-lockfile` / `--immutable`). After a `package.json` edit, the install may update the lockfile, and the lockfile change goes into the same commit. Lockfiles are outside `editableScope`, so the model never edits them directly (Q7).
- **Workspaces:** all workspace packages are type-checked and tested, because green means the full suite (concept section 1). The package managers' own workspace support is used. Nx, Turborepo, and Lerna task graphs are not.
- **Base-commit attribution** (M1) needs its own install in the base worktree. It only runs when tests fail.
- **Suppressions (Q8)** go into the TypeScript suppression queries. **Skip markers:** `.skip`, `xit` / `xdescribe` / `xtest`, `.todo`, and `.only` (it skips every other test).
- **Diagnostics:** `tsc --pretty false` output (`file(line,col): error TSxxxx: …`). Cluster keys come from the error code plus the missing module or symbol.

### Q10 · Recipes after the license check (follow-up to Q1)

What I found (I'm not a lawyer, so treat this as input for the legal check, not a verdict):

- **License:** both halves of OpenRewrite JavaScript are under the **Moderne Source Available License** (MSAL). That covers the Java side (`org.openrewrite:rewrite-javascript`, Maven Central) and the Node side (`@openrewrite/rewrite` 8.93.0 on npm). It is not Apache 2.0.
- **MSAL terms:** internal use on your own code is allowed. Not allowed: *"distribute the Software … in a manner that makes the functionality of the Software directly or indirectly available to third parties"*, and offering *"a product or service, the value of which derives to any extent from the value of the Software"*. Amazon Q Code Transformer is a named example. Shipping it in our image, or building a product on it, falls under that.
- **Runner:** OpenRewrite's docs say JS/TS recipes *"are only supported via the Moderne CLI or the Moderne Platform"*. There is no Maven, Gradle, or npm runner. The Moderne CLI is a closed, commercial tool. I'm not sure about its exact license terms for private repos.
- **Value:** the public catalog has six JS recipes, all dependency housekeeping: add, change, remove, and upgrade an npm dependency, upgrade a transitive dependency, and change an import. I found no library migration recipes (e.g. React or Express majors). For a PR that already bumped the version, a recipe would add almost nothing.

Q1 = B therefore runs into three problems: the license, the Moderne CLI, and a catalog that doesn't help.

- [x] **A · No recipe in M9. Record the finding in `docs/licensing.md` and concept section 9, and revisit when a runner exists outside the Moderne CLI** — *recommended.* Proves the "recipe is optional" path (concept section 4). No MSAL code and no closed tool in the image. Costs nothing today, because the catalog has no migration recipes.
- [ ] **B · Moderne CLI in the tool image** — Runs the recipes as intended. Adds a closed, commercial dependency to an image we distribute, plus MSAL recipes, and still gains nearly nothing for dependency PRs.
- [ ] **C · Our own runner on `rewrite-javascript` and the `rewrite-rpc` server from `@openrewrite/rewrite`, fetched at run time like recipe JARs (M2)** — No Moderne CLI, and no MSAL code in our image. But it builds on an internal, undocumented RPC protocol, and the MSAL "value derives from" clause is a bigger risk here than in M2.

**Notes:** A (answered in chat)

### Q11 · Yarn Berry Plug'n'Play (follow-up to Q2)

With PnP there is no `node_modules`. Packages live in zip archives, and `tsc` and the test runners only work through Yarn (Yarn patches TypeScript for PnP).

- [x] **A · Support PnP: run every tool through `yarn` and read `.d.ts` files from the zip archives. A one-line `yarn node` call to the PnP API finds where the package is** — *recommended.* Full coverage, as chosen in Q2. The JDK reads zip files natively. It costs one tiny Node call (the glue case from concept 2.2) and fixture tests for the PnP layout.
- [ ] **B · Force `nodeLinker: node-modules` for our run (`YARN_NODE_LINKER`)** — One code path for all managers. But our build is no longer the project's build: PnP's strict checks (e.g. undeclared dependencies) disappear, so a fix can pass for us and fail in the project's CI.
- [ ] **C · PnP → tool error (exit `2`) in M9. Berry with `nodeLinker: node-modules` and Yarn classic are supported** — Simplest, documented as a limitation. Misses Yarn's default mode.

**Notes:** A (answered in chat)

### Q12 · Package manager versions (`packageManager`)

`packageManager` in `package.json` pins the manager version (e.g. `pnpm@9.12.0`). Lockfile formats differ between majors (pnpm 8 vs. 9, Yarn 3 vs. 4). As far as I know, Node no longer bundles Corepack from Node 25 on.

- [x] **A · Corepack installed in the image (pinned). It honours `packageManager`. The current major of npm, pnpm, and Yarn is pre-cached in the image. Any other pinned version is fetched from the configured registry at run time. Without `packageManager`: the image defaults. npm comes from the selected Node** — *recommended.* Respects the project's choice, like the Maven and Gradle wrappers. The fetch is a read-only registry lookup (rule 7) and goes through the user's registry config (Q13).
- [ ] **B · Fixed npm, pnpm, and Yarn versions in the image, `packageManager` ignored** — No run-time fetch. But it rewrites lockfiles in another format and fails on older majors.
- [ ] **C · Like A, but no run-time fetch: a pinned version that isn't in the image → tool error** — Fully offline. Many repos pin exact patch versions, so this would fail often.

**Notes:** A (answered in chat)

### Q13 · Registry config and credentials

Regulated environments install from an internal registry (Artifactory, Nexus) with credentials. npm, pnpm, and Yarn classic read `.npmrc`. Yarn Berry reads only `.yarnrc.yml`.

- [x] **A · The project's own `.npmrc` / `.yarnrc.yml` as they are, plus optional `--npmrc <file>` and `--yarnrc <file>` installed as the user-level config for the run. Each has an action input** — *recommended.* Mirrors `--maven-settings` and `--gradle-init` (M1, M7). Credentials stay in the user's secret store.
- [ ] **B · Environment variables only (`NPM_CONFIG_*`, `YARN_NPM_*`)** — No new options, but users have to learn each manager's variable names, and Yarn classic and Berry differ.

**Notes:** A (answered in chat)

### Q14 · Install scripts and secrets

A new package version can run `postinstall` code, and the tests execute dependency code anyway. That is true for Maven plugins and JUnit tests too, but npm makes it more visible. If the GitHub App token is in the environment, that code can read it.

- [x] **A · Run lifecycle scripts as the project's CI does. Every child process (install, `tsc`, tests, and Maven/Gradle too) gets a scrubbed environment: no token and no `lector` inputs, only an allowlist (`PATH`, `HOME`, registry variables, `CI`, …)** — *recommended.* Builds behave like the project's own CI. The token can't leak to dependency code. This is a cross-cutting fix that M5 should have had. I'd add it to the M5 spec as well.
- [ ] **B · A plus `--ignore-scripts`** — Less code runs. But the project's own `postinstall` / `prepare` steps (codegen, Prisma) are skipped too, and the compile errors that follow would count as caused by the update (M1: compile errors always count).
- [ ] **C · Leave the environment as it is** — Simplest, but dependency code can read the push token.

**Notes:** A (answered in chat)

### Q15 · How tests are run (follow-up to Q4)

Vitest has a built-in `junit` reporter. Jest needs the separate `jest-junit` package, and most projects don't have it.

- [x] **A · Call the detected runner directly through the package manager (`<pm> exec jest`, `<pm> exec vitest run`) with the project's config and our reporter flags. `jest-junit` is pinned in the image and passed by absolute path, so the repo isn't changed. `runTests` filters by test file and name. Other runners: `<pm> test`, exit code and console only** — *recommended.* Exact reporter control, and single tests can be rerun for flaky and base checks (M1). Check `jest-junit`'s license (I believe Apache 2.0).
- [ ] **B · Always `<pm> test -- <reporter flags>`** — Respects custom test scripts. But the flags land on whatever the script runs first (e.g. `lint && jest`), and rerunning single tests becomes guesswork.

**Notes:** A (answered in chat)

### Q16 · Type check in workspaces (follow-up to Q6)

In many monorepos, package `app` imports workspace package `lib` through `lib`'s build output (`"types": "dist/index.d.ts"`). `tsc` on `app` then fails until `lib` is built. Compile errors always count as caused by the update (M1), so a missing build step would show up as false update breakage.

- [x] **A · First run the `build` script (if present) of every workspace package that another workspace package depends on, in dependency order. Then run `tsc --noEmit -p` for each package that has a `tsconfig.json`. If the root `tsconfig.json` uses project references, run `tsc --build` instead** — *recommended.* Builds only what type checking needs. App builds like `next build` are skipped. Build output is gitignored or outside `editableScope`.
- [ ] **B · Run every package's `build` script, then type-check** — Simplest rule, but slow (full app builds) and it does more than verifying needs.
- [ ] **C · No build step: `tsc --build` for project references, otherwise `tsc --noEmit -p` per package** — Fastest. Gives false compile errors in repos that link workspace packages through `dist/`.

**Notes:** A (answered in chat)

### Q17 · `tsconfig.json` and `package.json` edits (build-file blocklist)

On the JVM, build files are editable but test-disabling lines are blocked (M4, M7). The TypeScript equivalent of "make it green by turning checks off" is in `tsconfig.json`.

- [x] **A · `package.json` and `tsconfig*.json` are build files and editable. The blocklist rejects added or changed lines that turn checks off: `strict` / `strict*` / `noImplicit*` set to `false`, `skipLibCheck: true`, `noCheck`, `checkJs: false`, new `exclude` entries. It also rejects edits to the `test` script and to Jest/Vitest settings that skip tests (`testPathIgnorePatterns`, `exclude`, `passWithNoTests`), also in `jest.config.*` / `vitest.config.*`** — *recommended.* Allows real fixes (`moduleResolution: "bundler"` for packages with `exports`, adding `types`) and blocks the obvious cheats.
- [ ] **B · `tsconfig*.json` read-only, `package.json` editable with the test-script blocklist** — Simpler blocklist, but blocks legitimate compiler-option fixes that some majors require.
- [ ] **C · Both editable, no blocklist** — Simplest, and the easiest cheat for the model.

**Notes:** A (answered in chat)

### Q18 · Grammars and JavaScript files

A TypeScript repo also contains `.js` / `.mjs` / `.cjs` files (configs, scripts, plain JS sources). The guardrails can only check a file that has a grammar (M4).

- [x] **A · TypeScript, TSX, and JavaScript grammars from `tree-sitter-ng`. All of `.ts/.tsx/.mts/.cts/.js/.jsx/.mjs/.cjs` are editable and guarded** — *recommended.* Covers config files like `vitest.config.js` and mixed repos. Check that `tree-sitter-ng` ships all three grammars for Linux and macOS (I believe it does).
- [ ] **B · TypeScript and TSX only. JavaScript files outside `editableScope`** — One grammar fewer, but fixes in JS config files or plain-JS sources aren't possible.

**Notes:** A (answered in chat)

### Q19 · Where grounding finds the types (follow-up to Q5)

Modern packages declare types through `exports` conditions and re-export from barrel files. Older ones use `types` or `typings`. Many have no types and rely on `@types/<name>`.

- [x] **A · Resolve in this order: the `types` condition of the imported subpath in `exports`, then `types` / `typings`, then `index.d.ts`, then `@types/<name>`. Follow relative re-exports (`export * from './x'`) inside the package. Pick declarations by the symbols in the error and the failing file's imports, up to the M3 grounding size limit** — *recommended.* Matches how `tsc` resolves types for most packages, with no Node script.
- [ ] **B · Only `types` / `typings` plus `@types/<name>`, no re-exports** — Simpler, but finds nothing useful for most modern packages, which re-export everything from a barrel file.

**Notes:** A (answered in chat)

### Q20 · Resolved versions (needed for M3's downgrade check and M8's fallback)

- [x] **A · Parse the lockfile (`package-lock.json` v2/v3, `pnpm-lock.yaml`, Yarn classic and Berry `yarn.lock`) for the direct dependencies of every workspace package. Formats of maintained manager majors only. Older formats → tool error** — *recommended.* No install needed, works the same on base and head. Four small parsers with fixture tests.
- [ ] **B · Ask the package manager (`npm ls --json`, `pnpm list --json`, `yarn info --json`)** — No parsers of our own. But some commands need an installed tree (an extra install on the base for the fallback), and every manager's output differs anyway.

**Notes:** A (answered in chat)

### Q21 · Where "tracked by Git" lives (follow-up to Q9)

`core` already rejects Git-ignored files (M4). "Tracked by Git" is just as language-neutral, and it also catches generated output that isn't ignored. `editableScope()` returns globs, so an adapter would have to return a list of tracked paths.

- [x] **A · Add "only files tracked by Git" to the core scope rule for all adapters, as one small commit before the TypeScript adapter. Recorded in `features.md` as a deliberate core change, not one TypeScript forced** — *recommended.* One check next to the existing Git-ignored check, and the JVM benefits too. The "done when" stays honest, because the change is named.
- [ ] **B · Only in the TypeScript adapter (`editableScope` returns the tracked paths)** — No core change, but the adapter runs Git, the JVM doesn't get it, and the check is duplicated if it moves later.

**Notes:** A (answered in chat)

### Q22 · Proof (done when)

- [x] **A · Tagged real-toolchain fixture tests: npm single package with Jest, a pnpm workspace with Vitest and a `dist/`-linked package, Yarn classic, and Yarn Berry PnP. Plus a GitHub TypeScript test repo with Renovate end-to-end, a few TypeScript cases in the M6 benchmark, and a written list of every `core` change (expected: only Q21)** — *recommended.* Covers each package manager and the workspace build case, proves the M5 flow, and gives TypeScript its own benchmark numbers (concept section 11).
- [ ] **B · Fixture tests only** — Faster, but doesn't prove the flow on GitHub or measure anything.

**Notes:** A (answered in chat)

### Q23 · Measuring the MSAL dependency (licensing follow-up, affects M6)

`rewrite-spring`, `rewrite-migrate-java`, and `rewrite-testing-frameworks` are all under MSAL (checked on Maven Central). The core OpenRewrite libraries, `rewrite-maven-plugin`, `rewrite-java-dependencies`, and `rewrite-recipe-bom` are Apache 2.0.

- [ ] **A · Add an Apache-only variant to the M6 benchmark** — *recommended.* Turns the license risk into a number. Costs one extra benchmark configuration.
- [x] **B · No, decide on legal grounds only** — Less benchmark work, but the business decision is made without knowing what the MSAL recipes actually contribute.

**Notes:** B (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Recipes | OpenRewrite JavaScript, if the license allows it. License checked → Q10 |
| Q2 | Package managers | npm, pnpm, and Yarn (classic + Berry). PnP handling → Q11 |
| Q3 | Node.js | Several LTS versions in the image, selected per repo, default newest LTS |
| Q4 | Tests | Jest and Vitest with JUnit XML; other runners exit code + console only. Invocation → Q15 |
| Q5 | Grounding | tree-sitter extracts declarations from the package's types entry. Resolution → Q19 |
| Q6 | Monorepos | Workspaces from the start (npm, pnpm, Yarn). Type check → Q16 |
| Q7 | `package.json` | Editable; reinstall and update the lockfile after an edit |
| Q8 | Suppressions | `@ts-ignore`, `@ts-expect-error`, `@ts-nocheck`, `eslint-disable*` |
| Q9 | Editable scope | Tracked by Git, minus generated patterns. Placement → Q21 |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q10 | Recipes | No recipe in M9 (`recipeRunner` absent). OpenRewrite JS is MSAL and only runs through the Moderne CLI; finding goes into `docs/licensing.md` and concept section 9. Replaces Q1 |
| Q11 | Yarn PnP | Supported: tools run through `yarn`, `.d.ts` read from zip archives, package location via one `yarn node` call to the PnP API |
| Q12 | Package manager versions | Corepack in the image, honours `packageManager`; current majors pre-cached, other pinned versions fetched from the configured registry |
| Q13 | Registry config | Project's `.npmrc` / `.yarnrc.yml` as they are, plus `--npmrc` and `--yarnrc` with action inputs |
| Q14 | Scripts and secrets | Lifecycle scripts run. Every child process of every adapter gets an allowlisted environment without the token or `lector` inputs. To be added to M5 as well |
| Q15 | Test invocation | Runner called directly via the package manager with our reporter flags; `jest-junit` pinned in the image, passed by absolute path; other runners `<pm> test`, exit code only |
| Q16 | Workspace type check | `build` script of depended-on workspace packages first (dependency order), then `tsc --noEmit -p` per package, or `tsc --build` with project references |
| Q17 | Config edits | `package.json` and `tsconfig*.json` editable as build files, with a blocklist for check-loosening options and test-skipping changes |
| Q18 | Grammars | TypeScript, TSX, and JavaScript; all TS/JS extensions editable and guarded |
| Q19 | Types lookup | `exports` types condition → `types`/`typings` → `index.d.ts` → `@types/<name>`, following relative re-exports |
| Q20 | Resolved versions | Lockfile parsing for npm, pnpm, Yarn classic and Berry; older formats → tool error |
| Q21 | Tracked by Git | Core scope rule for all adapters, as a separate commit, recorded as a deliberate core change |
| Q22 | Proof | Tagged real-toolchain fixtures (npm + Jest, pnpm workspace + Vitest with `dist/` link, Yarn classic, Yarn Berry PnP), GitHub test repo with Renovate end-to-end, TS cases in the M6 benchmark, list of every core change |
| Q23 | MSAL measurement | No Apache-only benchmark variant; the MSAL question is decided on legal grounds |
