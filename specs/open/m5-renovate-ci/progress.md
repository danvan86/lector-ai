# Progress – M5 Renovate in CI

Spec: [`spec.md`](spec.md)

Status: ⬜ open · 🟨 in progress · ✅ done

Built on the M4 branch (`danvan86/m4-guardrails`): the fix loop runs the guardrails, and the comment reports their
rejections.

| # | Step | Status |
|---|---|---|
| 1 | Core groundwork: scrubbed environment for every process, commit identity, several changes per fix loop, recipe lookup and run split | ✅ |
| 2 | JDK selection in the Maven adapter | ✅ |
| 3 | Ports `UpdateSource` and `GitHost`, and the PR pipeline in `core`: trigger checks, verify first, recipes, fix loop, push, comment, label | ✅ |
| 4 | Renovate adapter in `sources` | ✅ |
| 5 | GitHub adapter in `git`: event, comment, label, fetch, push | ✅ |
| 6 | `lector pr` in `cli`, with end-to-end tests | ✅ |
| 7 | Container images, GitHub Action, release workflow | ✅ |
| 8 | Setup guide and `docs/licensing.md` | ✅ |
| 9 | Quality gates verified | ⬜ |
| 10 | Docs in sync | ⬜ |
