# Progress – M10 Simple setup

Spec: [`spec.md`](spec.md) · Tests: [`tests.md`](tests.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | The bot's identity from the App's slug: `gitHubAppIdentity` (`git`), `lector pr --app-slug`, one way to name the bot, github.com only | ✅ |
| 2 | The `gitIgnoredAuthors` hint: `UpdateSource.setupHint`, logged by the pipeline once a source takes the PR; Renovate's names the email; the action's `app-slug` input | ✅ |
| 3 | The reusable workflow `.github/workflows/lector.yml`, Dependabot's ignore for the self-pin | ✅ |
| 4 | The build check for the workflow's pins, inputs, and the docs' pins | ✅ |
| 5 | Docs: README and setup guide with the short setup, features, concept, AGENTS | ⬜ |
| 6 | Quality gates verified | ⬜ |
| 7 | Acceptance criteria checked | ⬜ |

## Notes

- **Port change in `core`:** `UpdateSource.setupHint(identity)`, abstract like the port's other members. The text comes from
  the source; `core` only logs it, so it names no bot (rule 1). A default method would leave Kotlin's `DefaultImpls`
  uncovered.
- **Frozen test changed with consent:** T-A3 matched the request log wrongly (see `tests.md`).
- **The reusable workflow** was linted once by hand with actionlint (Docker, not part of the build, Q9). Its only findings:
  `client-id` on `actions/create-github-app-token@v3`, which actionlint's bundled metadata doesn't know yet; v3 documents it.
- **`checkReusableWorkflow`** lives in `buildSrc` (`ReusableWorkflow`): a script-level function can't be serialized by
  the configuration cache. Probed: a wrong action pin, a wrong Ollama version, a missing input, a different default, and
  a docs pin each fail it with a message naming the mismatch; `20` against `"20"` passes.
