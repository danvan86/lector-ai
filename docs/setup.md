# Setup – lector-ai for Renovate PRs on GitHub

lector-ai runs as a step in your GitHub Actions workflow. When Renovate opens or updates a PR, it builds the PR and
compares it with the base commit. If the update broke something, lector-ai applies the matching OpenRewrite recipe and
lets a local model fix what is left. The fixes are pushed to the Renovate branch as separate commits, and a comment on
the PR reports what was done. If the PR builds, nothing changes.

You need four things: a GitHub App that pushes the fixes, a Renovate setting, the workflow below, and a runner that can
run the model.

## 1. GitHub App

lector-ai pushes with a GitHub App token, not with `GITHUB_TOKEN`: pushes made with `GITHUB_TOKEN` don't start
workflow runs, so the PR's checks would never run on the fixes.

1. Create an App under **Settings → Developer settings → GitHub Apps → New GitHub App**, in your organization or
   account. No webhook and no callback URL are needed.
2. Give it these **repository permissions**:
   - **Contents: Read and write**, to push the fix commits.
   - **Pull requests: Read and write**, for the report comment and the `ai-assisted` label.
   - Metadata: Read-only, which GitHub adds on its own.
3. Install the App on the repositories that lector-ai should fix.
4. In each of these repositories (or the organization), store the App's **client ID** as the variable
   `LECTOR_APP_CLIENT_ID` and a **private key** as the secret `LECTOR_APP_PRIVATE_KEY`.

The workflow creates a short-lived token from the key with `actions/create-github-app-token`. lector-ai only ever
sees that token, never the private key.

**The bot's name and email.** Every commit lector-ai makes is authored by the App's bot user:

- Name: `<app-slug>[bot]`, e.g. `my-lector[bot]`. The slug is in the App's URL.
- Email: `<user-id>+<app-slug>[bot]@users.noreply.github.com`. Look up the user ID with
  `curl -s https://api.github.com/users/<app-slug>%5Bbot%5D | jq .id`.

## 2. Renovate

Add the bot's email to `gitIgnoredAuthors` in your Renovate configuration:

```json
{
  "gitIgnoredAuthors": ["123456+my-lector[bot]@users.noreply.github.com"]
}
```

Without it, Renovate sees lector-ai's commits as someone else's changes and stops updating the branch.

Nothing else changes in Renovate. lector-ai reads the update table and the release notes from Renovate's PR body,
including grouped PRs (monorepo groups and the like).

## 3. Workflow

Save this as `.github/workflows/lector-ai.yml` and fill in the bot's name (twice) and email:

```yaml
name: lector-ai

# Only pull_request (concept 8.1): the PR's code must never run with the base repository's secrets.
on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read

# One run per PR. A push by Renovate cancels the run that is still working on the old head. lector-ai's own push must
# not: that run still has to post its comment. The run its push starts waits, then skips the bot's own commit.
concurrency:
  group: lector-ai-${{ github.event.pull_request.number }}
  cancel-in-progress: ${{ github.event.sender.login != 'my-lector[bot]' }}

jobs:
  heal:
    # Renovate's PRs from this repository only. lector-ai checks both again, and skips its own commits.
    if: github.event.pull_request.user.login == 'renovate[bot]' && github.event.pull_request.head.repo.full_name == github.repository
    runs-on: ubuntu-latest
    timeout-minutes: 120
    services:
      # The model, with its weights in the image. lector-ai reaches it at http://ollama:11434.
      ollama:
        image: ghcr.io/danvan86/lector-ollama:qwen2.5-coder-7b
        options: >-
          --health-cmd "ollama list"
          --health-interval 5s
          --health-retries 30
    steps:
      - id: app-token
        uses: actions/create-github-app-token@v3
        with:
          client-id: ${{ vars.LECTOR_APP_CLIENT_ID }}
          private-key: ${{ secrets.LECTOR_APP_PRIVATE_KEY }}
      # The PR's head commit, not the merge commit that pull_request checks out by default. lector-ai fetches the base
      # commit itself, and pushes with the App token.
      - uses: actions/checkout@v7
        with:
          ref: ${{ github.event.pull_request.head.sha }}
          persist-credentials: false
      - uses: danvan86/lector-ai/action@v0.1.0
        with:
          token: ${{ steps.app-token.outputs.token }}
          bot-name: my-lector[bot]
          bot-email: 123456+my-lector[bot]@users.noreply.github.com
```

What happens on each run:

| Situation | What lector-ai does | Check |
|---|---|---|
| Not Renovate's PR, from a fork, not a `renovate/` branch, or the head commit is lector-ai's own | Nothing | green |
| The PR builds and its tests pass, or every failure is pre-existing or flaky | Nothing; updates its comment if there is one | green |
| The update broke something | Recipe and fix commits, pushed to the branch; the comment; the `ai-assisted` label | green if nothing the update caused is left, red otherwise |
| Renovate pushed while lector-ai was working | Nothing is pushed; the new head gets its own run | green |
| lector-ai itself failed (configuration, network, a timeout) | Nothing is pushed | red, exit code 2 |

After lector-ai pushes, the run on its own commit skips, so its check on the PR's latest commit is green even when
something is left. Your own build workflow runs on that commit too, and the comment lists what is left: those are what
to look at before merging. The red check of exit code 1 stays on the commit lector-ai worked on.

lector-ai never merges, approves, or requests reviews.

**Options.** The action's inputs, besides `token`, `bot-name`, and `bot-email`:

| Input | Default | Meaning |
|---|---|---|
| `bot-logins` | `renovate[bot]` | The bots whose PRs are fixed, comma-separated, e.g. for a self-hosted Renovate App |
| `ollama-url` | `http://ollama:11434` | The Ollama service; the default is the `ollama` service above |
| `model` | `qwen2.5-coder:7b` | The model, which must be in the Ollama image |
| `max-attempts` | `20` | Fix attempts in total |
| `max-attempts-per-cluster` | `3` | Fix attempts per group of errors with one cause |
| `max-attempts-without-progress` | `2` | Attempts in a row without progress before the loop stops |
| `time-limit` | `30` | Minutes before the fix loop stops |
| `max-changed-lines` | `50` | Lines one attempt may change before it is rejected |
| `maven-settings` | none | A Maven settings file in the workspace, e.g. for a mirror; credentials in it can come from `MAVEN_*` variables |
| `gradle-init` | none | A Gradle init script in the workspace, passed to every Gradle run, e.g. to point the repositories at a mirror; credentials can come from `GRADLE_*` and `ORG_GRADLE_PROJECT_*` variables |

There is no configuration file in your repository: everything is in the workflow.

**Maven and Java.** The tool image has JDKs 17, 21, and 25 and Maven 3.9. lector-ai builds with the smallest JDK that is
at least `maven.compiler.release` (or `<java.version>`) of the root POM, 21 if none is set. A project's Maven wrapper
(`mvnw`) is used if it has one. Maven downloads dependencies and recipes from your repositories as usual.

**Gradle and Java.** A Gradle build needs its wrapper (`gradlew` and `gradle/wrapper/`) committed, and Gradle 8 or newer.
lector-ai always runs the wrapper, so the image has no Gradle: the wrapper downloads the distribution its
`distributionUrl` names, so point that URL at your mirror if you need one. Gradle runs on the JDK that
`gradle/gradle-daemon-jvm.properties` asks for, otherwise on the newest of 17, 21, and 25 that your Gradle version
supports. Toolchains resolve from the same three JDKs only, never by auto-detection or a download: a toolchain for
another Java version (8, 11) is a tool error. The project's own repositories provide dependencies, recipes, and the
OpenRewrite libraries; the OpenRewrite Gradle plugin itself comes with the image.

The Gradle user home is `/github/home/.gradle` in the action's container, which is `${{ runner.temp }}/_github_home/.gradle`
on the runner. Cache it to skip the downloads of the distribution and the dependencies, with a step before lector-ai:

```yaml
      - uses: actions/cache@v6
        with:
          path: |
            ${{ runner.temp }}/_github_home/.gradle/caches
            ${{ runner.temp }}/_github_home/.gradle/wrapper
          key: lector-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/*.versions.toml') }}
          restore-keys: lector-gradle-
```

## 4. Runners and models

| Ollama image | Runner | Status |
|---|---|---|
| `lector-ollama:qwen2.5-coder-7b` (4.7 GB of weights) | Standard GitHub-hosted `ubuntu-latest` (4 CPUs, 16 GB) | The default, and the variant the benchmark measures (M6). Runs on CPU: slow, but it fits. |
| `lector-ollama:qwen3-coder-30b` (19 GB of weights) | A runner with more than 24 GB of memory, ideally a GPU | Not measured yet. Add `--gpus all` to the service's `options` on a GPU runner. Pushed only if the registry takes its 19 GB layer. |

With the 30B image, set `model: qwen3-coder:30b` in the action's inputs.

## Limitations

- **Maven and Gradle only**, and only at the repository root. A repository with both a `pom.xml` and Gradle build files
  at the root is refused: lector-ai doesn't pick a build tool.
- **Gradle:** no Android or Kotlin Multiplatform builds (the image has no Android SDK), no Gradle 7 or older, no build
  without a wrapper. Groovy sources (Spock tests) are never edited: their failures are reported, but the model can only
  fix the Java or Kotlin code they call. Linters bound to `check` (Spotless, Checkstyle, detekt) don't run: verify
  compiles every source set and runs every `Test` task. Test reports are read from `build/test-results/` in each
  project's directory, Gradle's default: with another build directory, failing tests count as a build failure. PRs that
  only update the Gradle wrapper are skipped.
- **GitHub only**, and **Renovate only.** Dependabot and other PRs come with M8.
- **Unsigned commits.** If your Renovate branches require signed commits, the push is rejected.
- **The token is not isolated from the build.** Builds and tests run without the token in their environment, but in the
  same container as lector-ai, as the same user. A build plugin from a malicious dependency could still read it from
  the system. The token is short-lived and limited to the App's permissions on the repositories it is installed on.
- **In-place only.** The fixes always go onto the Renovate branch; there are no companion PRs yet.
