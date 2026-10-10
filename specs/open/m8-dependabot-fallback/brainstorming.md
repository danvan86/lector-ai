# M8 – Dependabot and fallback · Brainstorming

Milestone: [M8 in features.md](../../../docs/features.md#m8--dependabot-and-fallback-) · Status: **Closed**

**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.

## Already fixed by the project docs

- Dependabot source adapter (`fetch-metadata`, grouped updates) and a fallback adapter that compares manifests.
- Companion-PR mode `heal/<branch>` is the Dependabot default. Branch policy is configurable per source.
- Dependabot-triggered workflows get a read-only token and only Dependabot secrets.
- From M2: the ecosystem is the package namespace (`maven` for every JVM build). Resolved versions are an adapter capability, meant as the building block for the fallback. Dependabot's `gradle` maps to `maven`.
- From M5: `UpdateSource` and `GitHost` ports, trigger checks in the tool (author, same repo, branch prefix, not our own commit), verify first and stop when green, each distinct recipe once, push without force to the source branch, one sticky comment, `ai-assisted` label, configuration through action inputs only.
- From M7: Gradle resolved versions come from the query task, Gradle plugins use marker coordinates `<id>:<id>.gradle.plugin`.

```
PR ─▶ source selection
        ├── author renovate[bot] + renovate/*     ─▶ RenovateSource
        ├── author dependabot[bot] + dependabot/* ─▶ DependabotSource
        └── anything else                          ─▶ FallbackSource (manifestDiff)
```

---

## Round 1

### Q1 · Dependabot metadata

- [x] **A · User's workflow runs `dependabot/fetch-metadata` and passes `updated-dependencies-json` to our action** — *recommended* (concept). Official, structured, covers grouped updates.
- [ ] **B · Parse PR title and body ourselves** — No extra step for users, but fragile and partly duplicates A.
- [ ] **C · Fallback manifest diff only** — No Dependabot-specific code, loses update type and release notes.

**Notes:** A (accepted as recommended)

### Q2 · Grouped updates

- [x] **A · One run for the whole group: recipes in sequence, then one fix loop** — *recommended.* Grouped dependencies usually belong together (e.g. all Spring modules), so errors are related.
- [ ] **B · One run per dependency** — Easier to attribute fixes, but intermediate states often don't build.

**Notes:** A (accepted as recommended)

### Q3 · Companion PR target

```
A (stacked)                         B (supersede)
main ◀── dependabot/x ◀── heal/x     main ◀── heal/x  (bump + fixes)
         (unchanged)                       dependabot/x closed
```

- [x] **A · Stacked: `heal/<branch>` targets the Dependabot branch. Merge the companion PR first, then Dependabot's** — *recommended.* Dependabot keeps managing its PR, and our changes stay separate.
- [ ] **B · Supersede: companion PR targets `main` with bump + fixes; the Dependabot PR is closed** — One PR to review, but Dependabot ignores that version once its PR is closed, so it stops updating it.
- [ ] **C · Companion PR targets `main`, Dependabot PR stays open** — Two PRs with the same bump, confusing.

**Notes:** A (accepted as recommended)

### Q4 · When the Dependabot PR changes

- [x] **A · Force-push the companion branch, keep the same companion PR** — *recommended.* One PR per update, review comments stay in one place (outdated ones are marked).
- [ ] **B · Close and open a new companion PR** (concept wording) — Clean state, but review discussion is scattered across PRs.

**Notes:** A (accepted as recommended)

### Q5 · Fallback: detecting what changed (Maven)

- [x] **A · `mvn dependency:list` on base and head, compare resolved versions** — *recommended.* Catches changes via properties, BOM imports, and parents. Costs two dependency resolutions.
- [ ] **B · Textual diff of `<version>` tags and properties in `pom.xml`** — Fast, misses BOM and parent-driven changes.
- [ ] **C · Effective POM on both sides** — Accurate for declared dependencies, slower, still misses transitive changes.

**Notes:** A (accepted as recommended). Implemented with the adapter's resolved versions from M2 (Maven) and M7 (Gradle) instead of `dependency:list`: same idea, but `dependency:list` misses parents and plugins (M2 spec), and the capability already exists for both build tools.

### Q6 · Release notes for the fallback

- [x] **A · None in M8** — *recommended.* The fix loop still has API grounding. Release notes can come later.
- [ ] **B · GitHub releases of the dependency (repo found via the POM's `<scm>` URL)** — Real notes, read-only metadata lookup (rule 7), extra mapping and network logic.

**Notes:** A (accepted as recommended)

### Q7 · Source selection

- [x] **A · Automatic: PR author + branch prefix, with an optional `source` input to override** — *recommended.* Works without configuration, and an override is there for unusual setups.
- [ ] **B · Always explicit via input** — Predictable, but one more thing to configure.

**Notes:** A (accepted as recommended)

### Q8 · Workflow security for Dependabot PRs

- [x] **A · `pull_request` with the App key stored as a Dependabot secret** — *recommended* (concept). One workflow, works with the existing author/same-repo checks.
- [ ] **B · Two workflows: unprivileged `pull_request` collects data, privileged `workflow_run` does the fixing** — Strongest isolation, but much more complex and slower.

**Notes:** A (accepted as recommended)

### Q9 · Branch policy configuration

- [x] **A · Action input `mode: in-place | companion`, with defaults per source (Renovate in-place, others companion)** — *recommended.* Matches M5's "inputs only" configuration.
- [ ] **B · Per-source config file** — More flexible, a second configuration place.

**Notes:** A (accepted as recommended)

## Round 2

Round 1 accepted every recommendation. The M5 and M7 decisions leave five points open.

### Q10 · Fallback: which version differences become changes

Resolved versions include transitive dependencies. A manual Spring Boot bump changes hundreds of them.

- [x] **A · Every package whose resolved version differs between base and head becomes a `DependencyChange`. The report lists them collapsed (count plus the first few)** — *recommended.* No extra "declared vs. transitive" data needed. Recipe lookup per change already dedupes (M5), and changes that aren't upgrades are skipped by M2 rule 1.
- [ ] **B · Only packages declared in the build (direct dependencies, parents, plugins)** — A short, precise list, but the query has to tell declared from transitive for Maven and Gradle.
- [ ] **C · Only the package with the largest update type** — Simplest, but misses recipes for the others.

**Notes:** A (answered in chat)

### Q11 · Which PRs get the fallback

M5 runs only on PRs from configured bot logins. "Anything else" would include every feature PR that touches a build file, and the tool would add commits or companion PRs nobody asked for.

- [x] **A · Opt-in label: a same-repo PR with the label `lector` (name configurable) runs through the fallback source. The workflow also listens to `labeled`** — *recommended.* Explicit, no surprises on feature branches, no cost for other PRs. Only people with write access can add labels.
- [ ] **B · Every same-repo PR whose resolved versions changed** — No extra step, but two resolutions on every PR and unasked-for changes on feature work.
- [ ] **C · Configured author list, like the bot logins** — Fits M5's check, but doesn't cover the occasional manual dependency PR.

**Notes:** A (answered in chat)

### Q12 · Release notes for Dependabot

`fetch-metadata` has no release notes. Dependabot puts them into the PR body (collapsible "Release notes" / "Changelog" sections).

- [x] **A · Read them from the PR body through `GitHost`, like the Renovate adapter** — *recommended.* Same approach as M5, no extra network calls.
- [ ] **B · None** — Less code, but the fix loop loses grounding that Renovate PRs have.

**Notes:** A (answered in chat)

### Q13 · Where the report goes in companion mode

- [x] **A · Sticky comment on the source PR (as in M5) with a link to the companion PR. The companion PR's description has the same summary. The `ai-assisted` label goes on the companion PR** — *recommended.* Reviewers find the result where the update is, and the label sits on the PR with the AI commits.
- [ ] **B · Everything on the companion PR only** — One place, but a reviewer looking at the red Dependabot PR doesn't see that a fix exists.

**Notes:** A (answered in chat)

### Q14 · Companion PR lifecycle

The companion PR must not outlive its reason.

- [x] **A · The documented workflow also runs on `closed`. The tool closes the companion PR and deletes `heal/<branch>` when the source PR is closed or merged, and when a new run finds the source PR green. Force-push (Q4) only ever targets `heal/*`** — *recommended.* No stale companion PRs. A superseding Dependabot PR has a new branch name and gets its own companion. The old one is closed with the old source PR.
- [ ] **B · Leave cleanup to humans** — Less code, but stale companion PRs pile up.

**Notes:** A (answered in chat)

---

## Decisions

### Round 1

| # | Topic | Decision |
|---|---|---|
| Q1 | Dependabot metadata | User's workflow runs `dependabot/fetch-metadata` and passes `updated-dependencies-json` to our action |
| Q2 | Grouped updates | One run for the whole group: recipes in sequence, then one fix loop (as M5 Q15) |
| Q3 | Companion PR target | Stacked: `heal/<branch>` targets the Dependabot branch |
| Q4 | Source PR changes | Force-push the companion branch, keep the same companion PR |
| Q5 | Fallback detection | Compare resolved versions of base and head, using the adapter's M2/M7 capability |
| Q6 | Fallback release notes | None in M8 |
| Q7 | Source selection | Automatic by PR author and branch prefix, optional `source` input override |
| Q8 | Workflow security | `pull_request` with the App key as a Dependabot secret |
| Q9 | Branch policy | Action input `mode` (`in-place` or `companion`); defaults Renovate in-place, others companion |

### Round 2

| # | Topic | Decision |
|---|---|---|
| Q10 | Fallback changes | Every package whose resolved version differs between base and head; collapsed in the report |
| Q11 | Fallback trigger | Opt-in label `lector` (name configurable) on a same-repo PR; workflow listens to `labeled` |
| Q12 | Dependabot release notes | Read from the PR body through `GitHost` |
| Q13 | Report in companion mode | Sticky comment on the source PR with a link; same summary in the companion PR description; `ai-assisted` on the companion PR |
| Q14 | Companion lifecycle | Tool closes the companion PR and deletes `heal/<branch>` when the source PR is closed or merged, or found green. Force-push only to `heal/*` |
