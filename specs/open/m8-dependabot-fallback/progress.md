# Progress – M8 Dependabot and fallback

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Source selection: sources own their triggers, `source` override, PR labels and state, bot logins default to both bots | ✅ |
| 2 | Dependabot source: `updated-dependencies-json`, ecosystem table, Gradle plugin markers, release notes, missing metadata | ✅ |
| 3 | Fallback source: resolved versions of base and head, every difference, unreadable versions in the comment | ✅ |
| 4 | GitHub adapter: find, create, update, close a PR, delete a branch, force-push only to `heal/*`, label by number | ✅ |
| 5 | Branch policy and companion PRs: `mode`, `heal/<branch>`, force-push and keep, cleanup on closed, merged, green; report | ✅ |
| 6 | Action inputs and setup guide | ✅ |
| 7 | Quality gates verified | 🟨 |
| 8 | Docs in sync | ⬜ |
