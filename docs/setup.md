# Setup – lector-ai for dependency PRs on GitHub

lector-ai runs as a step in your GitHub Actions workflow. When Renovate or Dependabot opens or updates a PR, or someone
adds the `lector` label to a dependency PR, it builds the PR and compares it with the base commit. If the update broke
something, lector-ai applies the matching OpenRewrite recipe (JVM projects) and lets a local model fix what is left. The fixes are
separate commits: on the Renovate branch itself, or in a companion PR for Dependabot and labelled PRs. A comment on the
PR reports what was done. If the PR builds, nothing changes.

You need four things: a GitHub App that pushes the fixes, a setting in Renovate (if you use it), the workflow below, and
a runner that can run the model.

## 1. GitHub App

lector-ai pushes with a GitHub App token, not with `GITHUB_TOKEN`: pushes made with `GITHUB_TOKEN` don't start
workflow runs, so the PR's checks would never run on the fixes. And in a run that Dependabot started, `GITHUB_TOKEN` can
only read.

1. Create an App under **Settings → Developer settings → GitHub Apps → New GitHub App**, in your organization or
   account. No webhook and no callback URL are needed.
2. Give it these **repository permissions**:
   - **Contents: Read and write**, to push the fix commits and to delete companion branches.
   - **Pull requests: Read and write**, for the report comment, the `ai-assisted` label, and companion PRs.
   - Metadata: Read-only, which GitHub adds on its own.
3. Install the App on the repositories that lector-ai should fix.
4. In each of these repositories (or the organization), store the App's **client ID** as the secret
   `LECTOR_APP_CLIENT_ID` and a **private key** as the secret `LECTOR_APP_PRIVATE_KEY`, twice: as **Actions secrets**
   and as **Dependabot secrets** (Settings → Secrets and variables → Dependabot). A workflow run that Dependabot
   started sees only Dependabot secrets.

The workflow creates a short-lived token from the key with `actions/create-github-app-token`. lector-ai only ever
sees that token, never the private key.

**The bot's name and email.** Every commit lector-ai makes is authored by the App's bot user:

- Name: `<app-slug>[bot]`, e.g. `my-lector[bot]`. The slug is in the App's URL.
- Email: `<user-id>+<app-slug>[bot]@users.noreply.github.com`. Look up the user ID with
  `curl -s https://api.github.com/users/<app-slug>%5Bbot%5D | jq .id`.

## 2. Renovate

Skip this section if you don't use Renovate. Dependabot needs no setting.

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
# labeled starts a run for the opt-in label, closed the cleanup of a companion PR.
on:
  pull_request:
    types: [opened, synchronize, reopened, labeled, closed]

permissions:
  contents: read

# One run per PR. A push by a bot or a person cancels the run that is still working on the old head, and so does closing
# the PR. lector-ai's own push and label must not: that run still has to post its comment. The run its push starts on a
# PR's own branch waits, then skips the bot's own commit. Another label doesn't cancel anything either.
concurrency:
  group: lector-ai-${{ github.event.pull_request.number }}
  cancel-in-progress: >-
    ${{ github.event.sender.login != 'my-lector[bot]' &&
        (github.event.action != 'labeled' || github.event.label.name == 'lector') }}

jobs:
  heal:
    # Renovate's and Dependabot's PRs, and PRs with the label lector, from this repository only. lector-ai checks all
    # of it again, and skips its own commits. Another label doesn't start a run.
    if: >-
      github.event.action != 'closed' &&
      github.event.pull_request.head.repo.full_name == github.repository &&
      (github.event.action != 'labeled' || github.event.label.name == 'lector') &&
      (contains(fromJSON('["renovate[bot]", "dependabot[bot]"]'), github.event.pull_request.user.login) ||
       contains(github.event.pull_request.labels.*.name, 'lector'))
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
      # What Dependabot updated, read from its commit. Only on Dependabot's PRs. It refuses once the head commit isn't
      # Dependabot's (lector-ai's own commit in place, the merged companion PR, a push by a person): Dependabot no longer
      # owns the branch then, and lector-ai skips the run instead of failing it.
      - id: metadata
        if: github.event.pull_request.user.login == 'dependabot[bot]'
        continue-on-error: true
        uses: dependabot/fetch-metadata@v3
      - id: app-token
        uses: actions/create-github-app-token@v3
        with:
          client-id: ${{ secrets.LECTOR_APP_CLIENT_ID }}
          private-key: ${{ secrets.LECTOR_APP_PRIVATE_KEY }}
      # The PR's head commit, not the merge commit that pull_request checks out by default. lector-ai fetches the base
      # commit itself, and pushes with the App token.
      - uses: actions/checkout@v7
        with:
          ref: ${{ github.event.pull_request.head.sha }}
          persist-credentials: false
      - if: steps.metadata.outcome != 'failure'
        uses: danvan86/lector-ai/action@v0.1.0
        with:
          token: ${{ steps.app-token.outputs.token }}
          bot-name: my-lector[bot]
          bot-email: 123456+my-lector[bot]@users.noreply.github.com
          dependabot-metadata: ${{ steps.metadata.outputs.updated-dependencies-json }}

  cleanup:
    # A closed or merged PR: lector-ai closes its companion PR, if it opened one. No model and no checkout needed.
    if: >-
      github.event.action == 'closed' &&
      github.event.pull_request.head.repo.full_name == github.repository &&
      (contains(fromJSON('["renovate[bot]", "dependabot[bot]"]'), github.event.pull_request.user.login) ||
       contains(github.event.pull_request.labels.*.name, 'lector'))
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - id: app-token
        uses: actions/create-github-app-token@v3
        with:
          client-id: ${{ secrets.LECTOR_APP_CLIENT_ID }}
          private-key: ${{ secrets.LECTOR_APP_PRIVATE_KEY }}
      - uses: danvan86/lector-ai/action@v0.1.0
        with:
          token: ${{ steps.app-token.outputs.token }}
          bot-name: my-lector[bot]
          bot-email: 123456+my-lector[bot]@users.noreply.github.com
```

The bot logins in the two `if:` conditions and the label `lector` match the action's defaults. If you change
`bot-logins` or `fallback-label`, change them there too.

What happens on each run:

| Situation | What lector-ai does | Check |
|---|---|---|
| Not a bot's PR and no `lector` label, from a fork, or the head commit is lector-ai's own | Nothing | green |
| The PR builds and its tests pass, or every failure is pre-existing or flaky | Nothing; updates its comment if there is one, closes its companion PR if there is one | green |
| The update broke a Renovate PR | Recipe and fix commits, pushed to the branch; the comment; the `ai-assisted` label | green if nothing the update caused is left, red otherwise |
| The update broke a Dependabot PR or a labelled PR | Recipe and fix commits in the companion PR; the comment on the PR, with a link; the `ai-assisted` label on the companion PR | green if nothing the update caused is left, red otherwise |
| Renovate pushed while lector-ai was working | Nothing is pushed; the new head gets its own run | green |
| A Dependabot PR whose head commit isn't Dependabot's: lector-ai's own in place, the merged companion PR, a person's push | Nothing: `fetch-metadata` refuses, and the lector-ai step is skipped | green |
| The PR was closed or merged | Closes its companion PR and deletes the companion branch | green |
| lector-ai itself failed (configuration, network, a timeout), or can't tell what a PR changes | Nothing is pushed; the comment says why if it got that far | red, exit code 2 |

After lector-ai pushes to a PR's own branch, the run on its own commit skips, so its check on the PR's latest commit is
green even when something is left. Your own build workflow runs on that commit too, and the comment lists what is left:
those are what to look at before merging. The red check of exit code 1 stays on the commit lector-ai worked on.

lector-ai never merges, approves, or requests reviews.

### Dependabot and companion PRs

Dependabot stops rebasing a PR once someone else has pushed to its branch, and `@dependabot rebase` or `recreate`
throws foreign commits away. So lector-ai leaves Dependabot's branch alone: it puts its commits onto a branch
`heal/<Dependabot's branch>`, on top of the PR's head, and opens a **companion PR** from it into Dependabot's branch.
The report comment stays on Dependabot's PR and links the companion PR; the companion PR's description has the same
summary.

- **Merge the companion PR first, then Dependabot's PR.** The companion PR goes into Dependabot's branch, so its fixes
  then arrive with the update.
- When Dependabot updates its PR, lector-ai rebuilds the companion branch from the new head and force-pushes it. The
  companion PR stays the same, so the review discussion stays in one place. If the new head gets no fixes, lector-ai
  closes the companion PR of the old head instead: merging it would bring the old head back.
- Once the companion PR is merged, Dependabot's branch has a head that isn't Dependabot's, so later runs on that PR
  skip (see the table above). Merge Dependabot's PR next.
- When Dependabot's PR is closed or merged, or a later run finds it green, lector-ai closes the companion PR and deletes
  its branch, also a branch whose companion PR was merged already. A superseding Dependabot PR has a new branch, so it gets a companion PR of its own.
- The `heal/` branches are lector-ai's: it force-pushes them and deletes them. Don't push your own work there.

**In-place for Dependabot.** With `mode: in-place`, lector-ai pushes onto Dependabot's branch instead, like for
Renovate. Then Dependabot no longer rebases that PR: resolve conflicts yourself, or close the PR and let Dependabot open
a new one. A head commit that isn't Dependabot's is skipped (see the table above), so lector-ai works on each head
Dependabot pushes, once.

`dependabot/fetch-metadata` reads what Dependabot updated, every dependency of a grouped update included. Without its
output, a run on a Dependabot PR fails with exit code 2. lector-ai fixes `maven` and `gradle` updates;
Dependabot's other ecosystems are skipped. The release notes come from Dependabot's PR body.

### Other dependency PRs: the opt-in label

For a manual version bump, a Snyk PR, or any other bot, add the label **`lector`** to the PR. lector-ai then compares
the resolved versions of the PR's base and head and treats every difference as a change,
transitive dependencies included. This catches changes made through properties, BOM imports, and parents. The fixes go
into a companion PR, as for Dependabot. There are no release notes for these PRs.

Only people with write access can add labels, so nobody else can start a run this way. The label works on PRs from
this repository only, not on forks. If lector-ai can't read the resolved versions of either side, it changes nothing
and says why in the comment.

**Options.** The action's inputs, besides `token`, `bot-name`, and `bot-email`:

| Input | Default | Meaning |
|---|---|---|
| `bot-logins` | `renovate[bot],dependabot[bot]` | The bots whose PRs are fixed, comma-separated, e.g. for a self-hosted Renovate App |
| `dependabot-metadata` | none | The `updated-dependencies-json` output of `dependabot/fetch-metadata`, as in the workflow above |
| `mode` | the source's | `in-place` or `companion`; by default Renovate's PRs are fixed in place, Dependabot's and labelled PRs in a companion PR |
| `source` | automatic | `renovate`, `dependabot`, or `fallback` (the label): which source must take the PR. By default Renovate is tried first, then Dependabot, then the label. The source's own checks still apply. |
| `fallback-label` | `lector` | The label that opts any other dependency PR in |
| `ollama-url` | `http://ollama:11434` | The Ollama service; the default is the `ollama` service above |
| `model` | `qwen2.5-coder:7b` | The model, which must be in the Ollama image |
| `max-attempts` | `20` | Fix attempts in total |
| `max-attempts-per-cluster` | `3` | Fix attempts per group of errors with one cause |
| `max-attempts-without-progress` | `2` | Attempts in a row without progress before the loop stops |
| `time-limit` | `30` | Minutes before the fix loop stops |
| `max-changed-lines` | `50` | Lines one attempt may change before it is rejected |
| `maven-settings` | none | A Maven settings file in the workspace, e.g. for a mirror; credentials in it can come from `MAVEN_*` variables |
| `gradle-init` | none | A Gradle init script in the workspace, passed to every Gradle run, e.g. to point the repositories at a mirror; credentials can come from `GRADLE_*` and `ORG_GRADLE_PROJECT_*` variables |
| `npmrc` | none | A user-level `.npmrc` in the workspace for npm, pnpm, and Yarn classic, e.g. for a registry; credentials can come from `NODE_AUTH_TOKEN`, `NPM_TOKEN`, and `NPM_CONFIG_*` variables |
| `yarnrc` | none | A user-level `.yarnrc.yml` in the workspace for Yarn Berry, e.g. for a registry; credentials can come from `YARN_*` variables |

There is no configuration file in your repository: everything is in the workflow.

**Maven and Java.** The tool image has JDKs 17, 21, and 25 and Maven 3.9. lector-ai builds with the smallest JDK that is
at least `maven.compiler.release` (or `<java.version>`) of the root POM, 21 if none is set. A project's Maven wrapper
(`mvnw`) is used if it has one. Maven downloads dependencies and recipes from your repositories as usual. For the
API changes the model gets, it also fetches the updated package's old version (its JARs only, no dependencies), the same way.

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

**TypeScript and Node.** A TypeScript project needs a `package.json` and a `tsconfig.json` at the root, exactly one
lockfile, and `typescript` as a dependency of the root or of every package with a `tsconfig.json`. Workspaces (npm,
Yarn, `pnpm-workspace.yaml`) are built and tested as a whole: every package is type-checked and runs its tests. The
supported lockfiles are those of the maintained package managers:

| Package manager | Lockfile | Format |
|---|---|---|
| npm 7 and newer | `package-lock.json` | `lockfileVersion` 2 or 3 |
| pnpm 9 to 12 | `pnpm-lock.yaml` | `lockfileVersion: '9.0'` |
| Yarn 1 (classic) | `yarn.lock` | `# yarn lockfile v1` |
| Yarn 4 (Berry), Plug'n'Play included | `yarn.lock` | `__metadata.version` 8 or newer |

The image has Node 22 and 24. lector-ai runs the one that `.nvmrc` or `.node-version` names (a version, a major,
`lts/*`, or an LTS codename), else the highest one `engines.node` allows, else 24; a project that needs another Node is
refused. The package managers run through Corepack, which honours `packageManager` in `package.json`: pnpm 12, Yarn 4,
and Yarn 1 come with the image, any other pinned version is fetched from the registry. A Yarn 4 project needs
`packageManager` (or a `yarnPath`). The install is frozen (`npm ci`, `--frozen-lockfile`, `--immutable`) and runs the
lifecycle scripts as your CI does. Every process runs with `CI=true`. Type checking uses the project's own `tsc`; Jest
reports through the image's `jest-junit` and Vitest through its JUnit reporter, without a change to your
`package.json`.

Registries: your `.npmrc` and `.yarnrc.yml` apply as they are. For a mirror or a private registry that only CI knows,
pass the `npmrc` or `yarnrc` input, and the token through the step's `env` as `NODE_AUTH_TOKEN` or `NPM_TOKEN`
(`//npm.example.org/:_authToken=${NODE_AUTH_TOKEN}` in an `.npmrc` reads it).
Corepack fetches package managers from npmjs.org: point `COREPACK_NPM_REGISTRY` (and `COREPACK_NPM_TOKEN` or
`COREPACK_NPM_USERNAME` and `COREPACK_NPM_PASSWORD`) at your mirror the same way. The processes get `NODE_AUTH_TOKEN`,
`NPM_TOKEN`, and the `NPM_CONFIG_*`, `npm_config_*`, `YARN_*`, and `COREPACK_*` variables of the step, never the App
token. Lifecycle scripts and tests run the dependencies' code, and these variables reach it: give them a token that can
only read the registry. For GitHub Packages through `NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}`, as setup-node's
examples show, that means a job whose `permissions` grant `packages: read` and nothing it could write with:

```yaml
      - uses: danvan86/lector-ai/action@v0.1.0
        with:
          token: ${{ steps.app-token.outputs.token }}
          bot-name: my-lector[bot]
          bot-email: 123456+my-lector[bot]@users.noreply.github.com
          npmrc: .github/lector.npmrc
        env:
          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
          COREPACK_NPM_REGISTRY: https://npm.example.org
```

The package managers keep their stores in the home directory, `/github/home` in the action's container. Cache the ones
your project uses with a step before lector-ai:

```yaml
      - uses: actions/cache@v6
        with:
          path: |
            ${{ runner.temp }}/_github_home/.npm
            ${{ runner.temp }}/_github_home/.local/share/pnpm/store
            ${{ runner.temp }}/_github_home/.cache/yarn
            ${{ runner.temp }}/_github_home/.yarn/berry/cache
          key: lector-node-${{ hashFiles('**/package-lock.json', '**/pnpm-lock.yaml', '**/yarn.lock') }}
          restore-keys: lector-node-
```

## 4. Runners and models

| Ollama image | Runner | Status |
|---|---|---|
| `lector-ollama:qwen2.5-coder-7b` (4.7 GB of weights, no GPU libraries) | Standard GitHub-hosted `ubuntu-latest`: 2 CPUs and 7 GB for a private repository, 4 CPUs and 16 GB for a public one | The default, and the variant the benchmark measures (M6). Runs on CPU: slow, but it fits. A run on a private repository's runner, two Maven builds and one fix attempt included, took 15 minutes. |
| `lector-ollama:qwen3-coder-30b` (19 GB of weights, with Ollama's GPU libraries) | A runner with more than 24 GB of memory, ideally a GPU | Not measured yet. Add `--gpus all` to the service's `options` on a GPU runner. Pushed only if the registry takes its 19 GB layer. |

A standard runner had about 17 GB of free disk in a measured run, and a service container is pulled before the first
step, so no step can make room for it. The 7B image needs about 10 GB while it is pulled; with the GPU libraries it
needed more than the runner had. Don't add services or a job container that
need the rest.

With the 30B image, set `model: qwen3-coder:30b` in the action's inputs.

## Limitations

- **Maven, Gradle, and TypeScript only**, and only at the repository root. A repository with both a `pom.xml` and Gradle
  build files at the root is refused: lector-ai doesn't pick a build tool. A JVM build with a `package.json` and a
  `tsconfig.json` next to it is built as a JVM project.
- **Gradle:** no Android or Kotlin Multiplatform builds (the image has no Android SDK), no Gradle 7 or older, no build
  without a wrapper. Groovy sources (Spock tests) are never edited: their failures are reported, but the model can only
  fix the Java or Kotlin code they call. Linters bound to `check` (Spotless, Checkstyle, detekt) don't run: verify
  compiles every source set and runs every `Test` task. Test reports are read from `build/test-results/` in each
  project's directory, Gradle's default: with another build directory, failing tests count as a build failure. PRs that
  only update the Gradle wrapper are skipped.
- **TypeScript:** no recipe step (OpenRewrite's JavaScript recipes run only in the Moderne CLI), so the model does all
  the fixing. Only Jest and Vitest report per test: any other `test` script passes or fails as a whole package. The
  package managers' workspaces are used, not Nx, Turborepo, or Lerna task graphs. No Bun or Deno, and no Node older than
  22. PRs that only update Node (`.nvmrc`, `.node-version`, `engines`, `setup-node`) are skipped.
- **GitHub only.**
- **Unsigned commits.** If your branches require signed commits, the push is rejected.
- **The token is not isolated from the build.** Builds and tests run without the token in their environment, but in the
  same container as lector-ai, as the same user. A build plugin from a malicious dependency could still read it from
  the system. The token is short-lived and limited to the App's permissions on the repositories it is installed on.
- **Many transitive changes.** A labelled PR that moves hundreds of transitive dependencies gives the model all of them
  as context. The comment lists the first ten changes without a recipe; the CI log lists every change.
