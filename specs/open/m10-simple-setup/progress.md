# Progress – M10 Simple setup

Spec: [`spec.md`](spec.md) · Tests: [`tests.md`](tests.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | The bot's identity from the App's slug: `gitHubAppIdentity` (`git`), `lector pr --app-slug`, one way to name the bot, github.com only | ✅ |
| 2 | The `gitIgnoredAuthors` hint: `UpdateSource.setupHint`, logged by the pipeline once a source takes the PR; Renovate's names the email; the action's `app-slug` input | ✅ |
| 3 | The reusable workflow `.github/workflows/lector.yml`, Dependabot's ignore for the self-pin | ✅ |
| 4 | The build check for the workflow's pins, inputs, and the docs' pins | ✅ |
| 5 | Docs: README and setup guide with the short setup, features, concept, AGENTS | ✅ |
| 6 | Quality gates verified | ✅ |
| 7 | Acceptance criteria checked | ✅ (AC27, AC28 open) |

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

## Quality gates

Final check, each on its own: `ktlintCheck`, `detektMain detektTest`, `test`, `:cli:endToEndTest`, `koverVerify` all green;
`./gradlew clean build --rerun-tasks --no-build-cache --no-configuration-cache` green. 881 unit tests and 24 end-to-end
tests, 0 failures; coverage 100 % lines and branches per module.

Caught on the way: detekt's `ThrowsCount` in the identity lookup and in `PrCommand` (split into small functions), Kover on
a default method in the `UpdateSource` port (made abstract like the port's other members), ktlint's line length in
`ReusableWorkflow`. The new check was probed after its refactor too. The existing gates are unchanged by M10.

## Acceptance criteria

AC1–AC26 met: the frozen tests (AC1–AC9, AC11–AC12), reading the files (AC10, AC13–AC18b, AC21–AC25), and commands
(AC12b: no `stdout` assertion of an existing test changed; AC12c: `core` names no Renovate after the KDoc of
`UpdateSource.name` lost its example; AC19–AC19c: the probes above; AC20: the clean build; AC26: only a task in
`build.gradle.kts`, no dependency or plugin).

## Found in the first real runs (lector-sample-app)

- `dependabot/fetch-metadata` fails with 403 "Resource not accessible by integration" in a private repository when the
  token can't read pull requests. The setup guide's workflow had the same gap since M8 (only `contents: read`). The
  reusable workflow and every documented caller now grant `pull-requests: read` (spec 3 said `contents: read` only).

## Open

- AC27: the run in a test repository on github.com with only the short setup. Needs a published release (blocked by the
  MSAL check in `docs/licensing.md`) or the images and the workflow from a fork.
- AC28: opening the pre-filled App link once (whether `pull_requests=write` is pre-filled; the docs say to check it).
- `docs/architecture/pr-pipeline.md` and `fix-loop.md` (`--app-slug`, the lookup, the hint, the reusable workflow, its
  check): for architecture-docs after the review, as the workflow says.
- The setup guide's caches and registry tokens now need the advanced setup: a reusable workflow can't take the caller's
  environment or extra steps.
