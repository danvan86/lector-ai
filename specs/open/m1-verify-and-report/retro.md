# Retro – M1 Verify and report

Scope: 2026-10-10 20:22 → 22:40 (local time, since the M0 retro), 10 sessions in 1 workspace (`victoria`), 6 drilled into. Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since "2026-10-10 20:22"` plus the artifacts above, the per-session context sizes from the raw transcripts, and a search of all sessions for zsh `(eval)` errors. Earlier retros read: [M0](../../done/m0-project-skeleton/retro.md).
Depth: Full, because `review.md` has a High finding (F1). The summary also listed 2 rejected tool calls, which turned out to be false positives (P3).

The range also covers harness work after M0 shipped: the retro skill, the ship-pr skill, the PR for M0, and a question about which model suits which spec. It counts as `no skill` and `skill-creator`.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- M1 needed almost no user input: no corrections, no interrupts, one decision (F3). The biggest avoidable cost was context carried into later work: the architecture docs were written at the end of the implement session, so each of their 14 requests re-read about 290k tokens.
- 3 proposals. 4 patterns didn't pass the filter.

## Metrics

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 1 / 2 / 4 | 1 / 7 / 9 | −10. Gate findings: 3 → 1 (Low) |
| Autonomy | Interventions (corrections, interrupts, rejected calls, round trips) | 0. Plus 1 decision (F3, valuable) and 1 commit blocked by the hook in a harness session | ≈ 9 | −9 |
| Efficiency | Active minutes: implement / review and fixes / architecture docs / harness work | 29 / 14 / 14 / 63 | M0 implement, review, and fixes were 134 (no skill); brainstorm 103 | Not comparable phase by phase |
| Efficiency | Fix rounds after the review | 1 | 1 | – |
| Cost | Weighted tokens: implement / review / docs / harness work (no skill + skill-creator) | 3.2M / 1.7M / 0.8M / 3.4M | brainstorm 4.7M / no skill 4.7M / other 1.0M | Total 9.2M vs 10.4M |
| Cost | Startup context (median) | 32k | 29k | +3k (more skills in the list) |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`) | 112 lines, ≈ 2.0k tokens (with this review's 2 bullets) | 107 lines, ≈ 1.8k | +5 lines |

M1 is the first milestone that ran through implement-spec and review-spec, so the phases have no earlier row to compare with. M1 is larger than M0 (70 files, 3.3k lines), yet the implement, review, and docs phases together took 57 active minutes and 5.7M weighted tokens. The review phase started at 88k context instead of about 32k, because it ran in the session that had just read all of M2–M9's specs for the model question.

## Last retro's changes

| Proposal | Expected effect | Observed | Keep / revert |
|---|---|---|---|
| M0, P1: brainstorm-spec asks in chat | No pasted files, answers in one pass | No chance yet: no brainstorming in this range | Keep |
| M0, P2: probe every file set and the task graph | Fewer gate findings in the review | Worked. `progress.md` probes main, test, and script sources for ktlint, `System.out.println` for detekt, and the task graph. Gate findings fell from 3 (M0 F10, F11, F16) to 1 Low (F6: `printf`/`format`/`append`, alternate routes not probed) | Keep |
| M0, P3: `setopt no_equals` | No lost output from `echo =====` | Worked. No `===== not found` in any session after 20:57; it was hit 8 times in the 2 days before | Keep |

## Proposals

### P1 · implement-spec stops writing the architecture docs; they're written once, after the review

**Problem.** implement-spec step 6 runs the architecture-docs skill (write, then check) at the end of the implement session. Each of its requests re-reads the whole implementation context. Then the review fixes change the code, so the docs have to be checked again after the review anyway, which is where the workflow order (and the ship-pr description) already puts architecture-docs.
Evidence:
- M1, `baa40342`, 22:09–22:21: architecture-docs inside implement-spec, 14 requests at 276k–307k context each, 541k weighted, 13 active minutes.
- M1, `aeac7280`, 22:38–22:40: architecture-docs after the review in a fresh session (Sonnet 5.5), peak context 85k, 297k weighted. It still found 3 claims that the review fixes had made stale.
- The user wasn't sure where docs fit: "Dont we have a skill for creating documentation?" (`aeac7280`, 22:38), and "Does the /review-spec skill also check if the acceptance-criteria are met?" (`d4140540`, 22:32).

**Lever.** Skill (removal), plus a next-step line in two reports.

**Change.** `~/.claude/skills/implement-spec/SKILL.md` → step 6 and step 8; `~/.claude/skills/review-spec/SKILL.md` → Report. +2 / −1 lines.

```diff
 - Update the concept doc if the architecture or scope changed.
-- Document what was built in `docs/architecture/` with the architecture-docs skill (write, then check), so the as-built docs match the code.
+- Don't write `docs/architecture/` here. architecture-docs runs after review-spec, in a fresh session, so it documents the reviewed code once.
```

```diff
 - The commit list and the next action you're offering: a review with review-spec if the final check is green, otherwise what still fails.
+  Suggest a fresh session for it: the review shouldn't carry this session's context.
```

```diff
 - **AGENTS.md:** each bullet added and the finding it came from, plus what was deliberately left out and why.
+- **Next:** architecture-docs for the milestone, in a fresh session, then retro and ship-pr.
```

**Expected effect.** About 0.5M weighted tokens and 12 active minutes less per milestone (the in-session docs run), and docs that are written against the reviewed code. architecture-docs still writes and checks every milestone, so quality holds.

Outcome: **Accepted.** implement-spec step 6 no longer runs architecture-docs, and its report suggests a fresh session for the review. review-spec's report names the next step: architecture-docs in a fresh session, then retro and ship-pr. No spec or template referenced the old step.

### P2 · Turn off zsh's `nomatch` for the agent's shell

**Problem.** In zsh, an unquoted glob that matches no file aborts the command: `grep -rn X --include=*.kt .` fails with `no matches found: --include=*.kt`, and a following `| wc -l` prints `0`. That looks like a valid "no match". Bash passes the pattern on, so the agent's habit is right everywhere else.
Evidence (7 times in 6 sessions):
- `6b29942d`, 10-08 19:27: `README*`.
- `42e9079c`, 10-09 21:49: `--include=*.kt` in the M0 acceptance checks.
- `514f1069`, 10-10 11:03: `--include=*.md`, with `2>/dev/null`, so the search silently returned nothing. 14:37: `*.hprof`.
- `cf5dfc53`, 10-10 20:21: `--include=*.kts`.
- `d7a337fa`, 10-10 21:07: a session-id glob.
- `d78c66a4`, 10-10 22:30, M1 review: the AC35 check `grep -rn "DependencyChange" --include=*.kt . | wc -l` printed `0` without running grep. The reviewer re-ran it quoted.

**Lever.** Shell option, the same way as M0's P3, which worked.

**Change.** `~/.zshrc`, one line. Interactive shells then pass an unmatched glob on, like bash.

```diff
 setopt no_equals   # Claude Code's Bash tool: `echo =====` must not abort the command
+setopt no_nomatch  # and an unmatched glob (`--include=*.kt`) is passed on, like in bash
```

Verify in a new session: `ls /nonexistent/*.x; echo ok` prints the `ls` error and `ok`.

**Expected effect.** No `no matches found` errors, and no false "nothing found" from a grep that never ran, in every project on this machine.

Outcome: **Accepted.** `setopt no_nomatch` added to `~/.zshrc`. A fresh interactive shell lists `nonomatch`, and `ls /nonexistent/*.x; echo ok` prints the `ls` error and `ok`. The running session still uses its old snapshot, so verify once in the next session.

### P3 · The retro script counts a rejection only when the tool result is one

**Problem.** `session_evidence.py` flags a tool result as rejected if its text contains a marker such as "permission denied" anywhere. Reading the script itself (which lists those markers) counts as a rejection. Rejected calls are a triage signal for a full retro, so a false one costs a full retro.
Evidence:
- `d7a337fa`, 21:39: `Read session_evidence.py` reported as rejected; the read succeeded.
- `f66764fd`, 21:34: `cat retro/scripts/session_evidence.py` reported as rejected; the command succeeded.
- Both are the only 2 rejected calls in this range's summary.

**Lever.** Script.

**Change.** `~/.claude/skills/retro/scripts/session_evidence.py`, the `tool_result` branch. +1 / −1 lines.

```diff
-                elif any(marker in body for marker in REJECTED):
+                elif block.get("is_error") and any(marker in body[:300] for marker in REJECTED):
```

Verify: rerun `--since "2026-10-10 20:22"`; the summary shows `Rejected tool calls: none`.

**Expected effect.** Rejected calls in the summary are real ones, so triage doesn't go full on a false signal.

Outcome: **Accepted.** The script now counts a rejection only for an error result whose start has a marker. Real rejections carry `is_error` (checked in older transcripts). Rerun for this range: `Rejected tool calls: none`.

## Also changed, at the user's request

- **Output style `Concise`** in `~/.claude/settings.json` (`"outputStyle": "Concise"`), for every project. Responses lead with the result and leave out narration and recaps. Errors, failing tests, and explanations the user asks for stay complete. No instruction text in `AGENTS.md` or `CLAUDE.md`. Next retro: compare output tokens per phase and check that reports still carry what's needed to act.

## Not proposed

- **Review run in the same session as an unrelated question** (`d78c66a4`: the model question read M2–M9's specs, then `/review-spec m1` started at 88k context, ≈ 0.3M weighted extra): one occurrence, and it's the user's choice of session. P1's next-step lines suggest a fresh session.
- **The commit hook checks the workspace's branch, not the repo being committed to** (`f66764fd`, 21:35: a commit in `~/.claude/skills` was blocked because the workspace was on a detached HEAD): one occurrence, and the retro normally runs on the feature branch. Revisit if it blocks again.
- **zsh doesn't split unquoted variables** (`baa40342`, 22:21: `for c in "clean build …"; ./gradlew $c` became one task name): one occurrence, and the agent noticed it right away.
- **Sonnet 5.5 for architecture-docs**: one good run (`aeac7280`, check mode found 3 stale claims). Not enough for a default. Worth running by hand again at M2 and deciding in its retro, together with the Sonnet/Opus split per spec from `d78c66a4`.
- **F1 (unexpected exception exits `1`)**: the review harvested the rule into `AGENTS.md`. Covered.

## What worked

- implement-spec's final check and gate probes: the review found no failing gate and only one Low gate finding, and the clean build passed on the first try after the fixes.
- review-spec's "run the real artifact on bad input" found F1 and F3, the two contract findings, with a real probe each.
- Asking only the one real decision (F3) and fixing the rest without a round trip: one question, answered in one word.
