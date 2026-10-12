# Retro – M5 Renovate in CI

Scope: 2026-10-12 10:24 → 16:03 (local time, since the M4 retro), 5 sessions in 2 workspaces (`irvine`, `naypyidaw`), 2 drilled into (the implement start, the ship-pr and CI fix session). Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since "2026-10-12 10:24"` plus the artifacts above, the first Bash calls of the implement session, `gh pr list --state all`, and `gh run list`. Earlier retros read: [M0](../m0-project-skeleton/retro.md), [M1](../m1-verify-and-report/retro.md), [M2](../m2-recipe-index/retro.md), [M3](../m3-fix-loop/retro.md), [M4](../m4-guardrails/retro.md).
Depth: Full, because `review.md` has a High finding (F1), the user had to report a red CI after ship-pr, and the M4 retro's P2 had no effect.

The range starts with the end of the M4 retro and its ship-pr run (`irvine`, 10:24–11:05). They count as `retro` and `ship-pr` and are left out of the M5 phase comparison. PR #8 was merged at 16:02, before this retro started.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- What cost the user time: the CI of PR #8 failed twice on causes only a GitHub-hosted Linux runner shows (`GITHUB_EVENT_PATH` filling `--event` in a test, a sleep rounded to whole milliseconds). ship-pr had reported before CI ran, so the user found it 33 minutes later and asked for the fix.
- The M4 retro's spec check was skipped: M5 started while PR #6 still changed its spec, and AC37 reached the branch only through an unrelated rebase an hour later (step 8b). Nothing went wrong, by luck.
- 3 proposals. 4 patterns didn't pass the filter.

## Metrics

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 1 / 3 / 5 | 2 / 3 / 3 | −1 High (F1: Ollama images published without the licensing gate). Plus 2 bugs CI found after the review (one test, one in `runProcess`) |
| Autonomy | Interventions (corrections, interrupts, rejected calls, round trips) | 1: "the pipeline failed at the build-step" (15:35). The F4 decision went through the question tool and was answered as recommended | 2 | −1. No "commit" prompt after the docs, no re-explain before the decision |
| Efficiency | Active minutes: implement / review / docs / ship-pr | 68 / 11 / 7 / 27 (≈ 22 of them the CI fix) | 40 / 30 / 14 / – | Implement grew with size (see below) |
| Efficiency | Fix rounds after the review | 1, plus 2 CI rounds after the PR | 2 | – |
| Cost | Weighted tokens: implement / review / docs / ship-pr | 9.4M / 2.0M / 1.3M / 0.8M | 4.2M / 2.0M / 0.6M / – | Docs: 0.9M of it is one general-purpose subagent (the drift check of five docs) |
| Cost | Startup context (median) | 33k | 33k | – |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`) | 123 lines, ≈ 2.7k tokens | 116 lines, ≈ 2.4k | +7 lines (review harvest, base-image versions) |

M5 is twice the size of M4 (78 files, +7.0k lines against 68 files, +3.2k). Implement cost per changed line is ≈ 1.3k weighted tokens, as in M2 and M4.

## Last retro's changes

| Proposal | Expected effect | Observed | Keep / revert |
|---|---|---|---|
| M4, P1: review-spec asks each decision with an example, through the question tool | No re-explain round trip; decisions committed in the review session | Worked. F4 asked at 14:38 with options, answered "A (Recommended)", recorded in `review.md` and the concept in `6eb1509`, before ship-pr | Keep |
| M4, P2: implement-spec checks that the spec is current | A milestone never starts on a stale spec | No effect. The implement session (`7d4a079c`) never ran the fetch or the `gh pr list` from step 1; its 7th command already reset the branch onto the unmerged M4 branch (10:27). PR #6, which added AC37 to the M5 spec, was open until 10:55. AC37 arrived at 11:24 with a rebase done for another reason | Replace (P1) |
| M4, P3: architecture-docs commits its doc itself | No "commit" prompt after the docs | Worked. `a096ec1` committed without a prompt | Keep |
| Earlier changes (M0–M3) | – | Hold. No glob errors, no false rejections, review fixes committed by review-spec | Keep |

## Proposals

### P1 · implement-spec runs the base check as a script, before choosing the branch's base

**Problem.** The check is a long bullet at the end of step 1, after step 0 has already chosen the base. In M5 the agent chose the base (the unmerged M4 branch) in its first minute and never reached the check. Stale specs hit two milestones in a row.
Evidence:
- M4 (`16800b22`, 18:49): started from `main` while PR #6 changed its spec; AC31 was missing (M4 retro).
- M5 (`7d4a079c`, 10:26–10:27): no `git fetch`, no `gh pr list`; PR #6 still open and changing the M5 spec. AC37 became step 8b after a rebase at 11:24.

**Lever.** Script in the skill (only its output costs tokens), called at the top of step 0. −1 long bullet in step 1, +1 short bullet in step 0, +1 script of 8 lines.

**Change.** New `~/.claude/skills/implement-spec/scripts/base_check.sh`:

```sh
#!/bin/sh
# What the base has that this branch lacks: commits on origin/main, and open PRs that change specs or the concept.
git fetch -q origin || exit 1
echo "## Commits on origin/main not in HEAD"
git log --oneline HEAD..origin/main
echo "## Open PRs that change specs/ or docs/concept"
gh pr list --state open --json number,title,headRefName,files \
  -q '.[] | select(any(.files[]; .path | startswith("specs/") or startswith("docs/concept"))) | "#\(.number) \(.headRefName): \(.title)"'
```

`~/.claude/skills/implement-spec/SKILL.md` → `## 0`, as its first bullet:

```diff
+- **First command of the session:** `sh ~/.claude/skills/implement-spec/scripts/base_check.sh`. If `origin/main` has commits this branch lacks, start from `origin/main`. If an open PR changes this feature's spec or the concept, stop and ask: it usually has to be merged first. Base the branch on an unmerged milestone branch only when the work needs its code, and say so in the report.
```

`## 1`: remove the bullet "Check that the spec is current: …".

**Expected effect.** The script's output appears in the first minute of every implement session; no acceptance criterion reaches a branch after implementing started.

Outcome: **Accepted.** `~/.claude/skills/implement-spec/scripts/base_check.sh` added and run once in this workspace (it listed the PR #8 merge commit, no open spec PR); the step 0 bullet added, the step 1 bullet removed. Uncommitted in `~/.claude/skills`.

### P2 · ship-pr waits for CI and fixes a red build itself

**Problem.** ship-pr ends right after `gh pr create`, so every report so far said nothing about CI, and a red build waits for the user to notice. The local build can't see what only the runner shows (its environment, Linux timing).
Evidence:
- M5 (`b3b3bf7d`): PR opened 15:02 with "no checks reported"; the user wrote at 15:35 "Unfortunatelly the pipeline failed at the build-step". Two red runs followed, each with a runner-only cause (`gh run list`: 13:02Z, 13:37Z failure, 13:45Z success).
- M0–M4: five PRs, none of the reports gave a CI result; those runs happened to be green.

**Lever.** Skill (ship-pr body). +1 section of 4 lines, +1 report line.

**Change.** `~/.claude/skills/ship-pr/SKILL.md`, a new section after `## 5. Push and open the PR`:

```diff
+## 5b. Wait for CI
+
+Watch the PR's checks in the background: `gh pr checks <number> --watch --interval 30`. Green: report. Red: read `gh run view <run> --log-failed`, reproduce the failure locally with the runner's conditions (e.g. `GITHUB_*` variables set, `--rerun` on the test task), fix it test-first as its own `fix:` or `test:` commit, run the build, push, and watch again. After two red rounds, stop and report the cause.
```

Report:

```diff
-- Whether the PR was merged (merge commit) or is open with its CI status.
+- Whether the PR was merged (merge commit) or is open, and its CI result: green, or the cause of the last red run.
```

**Expected effect.** No user round trip for a red CI. Cost: one background watch of ≈ 6 minutes per PR, a few requests.

Outcome: **Accepted.** Section 5b and the report line added to `~/.claude/skills/ship-pr/SKILL.md`, and `CI` added to its flow line. Uncommitted in `~/.claude/skills`.

### P3 · retro starts a branch from `origin/main` when the milestone's PR is already merged

**Problem.** When the retro runs after the merge, its file lands on a merged branch and needs its own PR. The skill doesn't say so. In M3 that PR stayed open for 16 hours and left the M4 and M5 specs stale.
Evidence:
- M3: retro after PR #5 merged → follow-up PR #6, open 2026-10-11 18:54 → 2026-10-12 10:55 (M4 retro).
- M5: PR #8 merged at 16:02, `/retro` at 16:03; this file is on the merged branch.

**Lever.** Skill (retro, step 1 and the commit rule). +1 bullet, 1 line changed.

**Change.** `~/.claude/skills/retro/SKILL.md` → `## 1 · Scope and harness`, after the first bullet:

```diff
+- If the milestone's PR is already merged (`gh pr list --head <branch> --state merged`), the retro can't ride on it: write it on a new branch from `origin/main` (`git fetch -q origin && git switch -c <branch>-retro origin/main`), and ship it with ship-pr right after the decisions, so no later milestone starts without it.
```

Step 8, last bullet:

```diff
-- Don't commit unless the user asks.
+- Don't commit unless the user asks, or the retro is on its own `-retro` branch (step 1): then commit and ship it with ship-pr.
```

**Expected effect.** A retro after the merge is in `main` the same hour; P1's base check never lists an open retro PR at the next milestone's start.

Outcome: **Accepted.** The step 1 bullet and the step 8 line added to `~/.claude/skills/retro/SKILL.md`. Uncommitted in `~/.claude/skills`.

## Not proposed

- **High finding F1** (release images without the licensing gate): found and fixed by the review, as intended. No earlier step skipped a check it has.
- **A testing rule for runner-dependent tests** (`GITHUB_*` in the test environment, rounded sleeps): the fix closes it in code (`executeInProcess` reads no environment, `runProcess` waits in nanoseconds). One occurrence of each; P2 catches the next one.
- **A cheaper model for the docs drift-check subagent** (0.9M weighted of the docs' 1.3M): one run; the M3 retro closed the model question for this phase. Revisit if the subagent becomes the default.
- **`progress.md` "modified since read"** (implement, 11:19): fifth occurrence across retros, still one extra request and no lost edit.

## What worked

- M4's P1: the decision through the question tool, with the recommendation, was answered in one step and committed before ship-pr.
- M4's P3: architecture-docs committed its own result.
- architecture-docs' check mode as a background subagent, parallel to writing the new doc: it found drift in all five older docs (seven modules, the timeout parameters, the commit identity, the moved options) without the main context reading them.

## Retro on the retro

Asked by the user after P1–P3: is the retro skill minimal, which criteria are missing, and what doesn't pay off.

**Track record (M0–M5):** 15 proposals, all accepted, none changed. 13 worked and still hold; 2 had no effect (M2 P1: a `model:` line in a skill's frontmatter; M4 P2: a prose check in implement-spec step 1). Neither could be seen working when it was applied. Retros cost 2.6M of 59.8M weighted tokens up to this session (4.3 %), about 24 active minutes in total.

**Where the 15 proposals came from:** user interventions 5, repeated tool errors 3, the previous retro's outcome 2, Git and PR state 2, the cost table 2, a review finding 1. Of the 15 rows in the patterns table, 6 never matched anything (permission prompts, questions always answered as recommended, `no skill` share, startup context, subagent cost, unused plugins). The script still measures those numbers.

**What doesn't pay off:**
- **Quick/full triage:** 5 of 5 retros since it exists went full. Its triggers include any single intervention, which nearly every milestone has. As a separate section it costs 20 lines and never changes the outcome.
- **The patterns table** (19 lines): the signals that produced proposals are the triage triggers; the fixes are the lever table's "use it for" column.
- **Re-checking every earlier change:** each retro lists "M0–M3 still hold" again. A change that worked once has never regressed.
- **Reading every earlier retro:** 6 files and 725 lines so far, growing by one per milestone. They're needed only to check whether a candidate was rejected or closed before; a grep does that.

**What's missing:**
- **Escaped defects as the quality metric.** Review findings by severity can't tell a weaker implementation from a deeper review (M3 0/4/7, M4 2/3/3, M5 1/3/5). Defects found after the review (red CI on the PR, bugs the user or the next milestone finds) measure the workflow's quality directly. In M5 the two CI bugs showed up only as an autonomy signal.
- **Proof that a change runs.** Both changes without effect were text the agent could skip or that didn't do what was assumed. A must-do step should be a script, hook, or gate, and step 8 should show each change working once (or name the event the next retro looks for).

### R1 · Quality is measured by escaped defects

**Change.** `~/.claude/skills/retro/SKILL.md`, goals table, row 1, "Measured by": `Review findings by severity` → `Escaped defects: red CI on the PR, bugs found by the user or a later milestone. Review findings by severity as context`. Template Metrics row 1 likewise. ±0 lines.

**Expected effect.** A red CI or a late bug becomes a quality signal and a full-retro trigger, not only an intervention.

Outcome: **Accepted.** Goals table row 1 in `~/.claude/skills/retro/SKILL.md` and a new Metrics row in `templates/retro.md`; escaped defects are one of the signals in step 3.

### R2 · A must-do change is a script, hook, or gate, and is seen working once

**Change.** Filter rule 4: `A hook or script beats prose; a skill step beats AGENTS.md.` → `A step the agent must not skip is a script call, hook, or gate, not prose (M2 P1 and M4 P2 had no effect as text). A skill step beats AGENTS.md.` Step 8, first bullet: append `For every change, show it working once (run the script, trigger the hook, check the model of a request) or name the transcript event the next retro checks.` +1 sentence each.

**Expected effect.** No accepted change without effect in the next retros.

Outcome: **Accepted.** Filter rule 4 and the first bullet of the apply step, as described.

### R3 · Cut what never paid off

**Change.** `~/.claude/skills/retro/SKILL.md`:
- Remove `## 5 · Patterns` (19 lines).
- Fold `## 4 · Triage` into step 3 as one bullet: go full on a signal (an intervention, an escaped defect, a tool error that changed course in two sessions, a High finding, a change without effect, or a phase or always-loaded file that grew without the milestone growing); otherwise write Scope, Metrics, and Last retro's changes and stop. Keep the drill-down command. (−10 lines)
- Step 1: read the previous retro only; grep the older ones for a candidate's topic before proposing it.
- Step 3: only the previous retro's changes and open trials; a change that worked once leaves the table.
- Steps 1 and 7: the feature folder is in `specs/open/` or `specs/done/` (ship-pr moves it).

162 → ≈ 130 lines, and fewer files read per retro.

**Expected effect.** The retro reads less (one earlier retro instead of all), writes shorter tables, and decides the same. Watch at M6: a proposal that a deleted pattern row would have caught.

Outcome: **Accepted.** Patterns removed, triage folded into step 3 ("signal" or "no signal"), steps renumbered 1–6, the template's `Depth` line now `Signal`. `SKILL.md` 162 → 128 lines.
