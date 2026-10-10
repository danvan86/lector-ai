# Retro – M0 Project skeleton

Scope: 2026-10-08 → 2026-10-10 (first retro, so all sessions), 13 sessions in 1 workspace (`victoria`; the main checkout matched but has no sessions in range). Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since 2026-10-08` plus the artifacts above, and the raw transcripts around each signal. Earlier retros read: none.
Times are local (CEST).

The range also covers the M1–M9 brainstorming sessions. They aren't part of M0, but they're the only evidence for the brainstorm-spec workflow, so they count.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- What cost the user the most time: answers written into `brainstorming.md` never reached the agent. That happened in 7 of 9 brainstormings, and every time the user had to answer again or paste the whole file.
- 3 proposals. 6 patterns didn't pass the filter.

## Metrics

Baseline, added after the retro: `session_evidence.py --until "2026-10-10 20:22"`, before this retro's own session.

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 1 / 7 / 9 | – | baseline |
| Autonomy | Interventions (round trips to recover lost brainstorming answers, files moved by hand) | ≈ 9 | – | baseline |
| Efficiency | Active minutes: brainstorm-spec (M0–M9) / no skill (concept, M0 implementation, review, fixes) | 103 / 134 | – | baseline |
| Efficiency | Fix rounds after the review | 1 | – | baseline |
| Cost | Weighted tokens: brainstorm-spec / no skill / other | 4.7M / 4.7M / 1.0M | – | baseline |
| Cost | Startup context (median) | 29k | – | baseline |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`, no `~/.claude/CLAUDE.md`) | 107 lines, ≈ 1.8k tokens | – | baseline |

M0 ran implement, review, and fixes with hand-written prompts, so they show up as `no skill`. From M1 on, implement-spec and review-spec have their own rows. `AGENTS.md` is under 7 % of the startup context. Most of the 29k is system prompt, tools, and the skill list from installed plugins.

## Proposals

### P1 · brainstorm-spec collects answers in chat by default, with the file as the record

**Problem.** The workflow expects the user to answer by moving the `x` in `brainstorming.md`. In this setup (Conductor's file viewer) the edits didn't reach the disk, and new rounds didn't show up in the viewer. Step 2 of the skill catches it ("check that the file actually changed"), but only after a round trip: the agent asks "what happened?", and the user re-answers or pastes the whole file.
Evidence:
- M0, session `6b29942d`, 10-08 19:48: file unchanged, agent asks. 19:53 "The new questions do not show up when I click on the brainstorming.md-file." 19:56 "It doesnt work with closing and reopening the file."
- M1, `6b29942d`, 10-09 08:17: file unchanged although the user had written notes. The user pasted the whole file into the question tool's "Other" field.
- M2, `d14e36f9`, 12:43, and M4, 15:27: file unchanged, whole file pasted into "Other" both times.
- M5, `d14e36f9`, 19:29, and M9, `c6684346`, 21:04: the user sent the answered file as a pasted attachment instead.
- M7, `db6871fe`, 20:54: file unchanged, agent asks.
- M9, `c6684346`, 21:12: "lets answer the questions here as i again cant see the updated file."
- In contrast, every follow-up round asked in chat went through in one pass. M9 round 2 (13 questions in 4 calls) took 3 minutes, 21:15–21:18.

**Change.** `~/.claude/skills/brainstorm-spec/SKILL.md` → Step 2, plus the "How to answer" line in `templates/brainstorming.md` and in the repo's `specs/README.md`. +6 / −7 lines.

```diff
 ## Step 2 · Collect answers
 
-The user answers by moving the `x` in the file, adding notes, or answering in chat.
-
-When the user says they answered:
-
-1. **Check that the file actually changed** (content and modification time). Editors and preview panes sometimes don't save, or show a stale copy.
-2. If it is unchanged, **ask** whether all recommended options were accepted or whether the edits got lost. Don't assume either.
-3. **Fallback: ask in chat** with the ask-user-question tool (`AskUserQuestion` or the host's equivalent). Use at most 4 questions per call. Put the recommended option first, with "(Recommended)" in its label, and copy the trade-off into each option's description. Then write the answers into the file yourself.
+Right after writing a round, **ask its questions in chat** with the ask-user-question tool (`AskUserQuestion` or the host's equivalent). The file is the record the user can read in full; the chat is where they answer. Editor and preview panes don't reliably save or refresh, so don't wait for edits to the file.
+
+- At most 4 questions per call, as many calls as the round needs. Put the question id in the header (`Q3 · Cache`), the recommended option first with "(Recommended)" in its label, and the trade-off in each option's description.
+- Write the answers into the file yourself, including free-text answers as **Notes**.
+- If the user says they answered in the file instead, check that it changed (content and modification time). If it didn't, say so and ask in chat. Don't assume all recommended.
```

```diff
-**How to answer:** the recommended option is preselected with `[x]`. Move the `x` to choose another one. Write anything else under **Notes**.
+**How to answer:** the agent asks these questions in chat; this file has the full options and records the answers. The recommended option is preselected with `[x]`.
```

**Expected effect.** In M1's next round and every later brainstorming, the user answers once, in chat, and nobody pastes a file. The check from the old step 2 stays for the case where the user edits the file anyway.

Outcome: **Accepted.** brainstorm-spec Step 2 now asks each round in chat right after writing it and keeps the file check as a fallback. The flow diagram and the template's "How to answer" line match, and so does `specs/README.md`. Closed brainstormings are records and keep their old line.

### P2 · implement-spec probes every file set and the wiring of each gate

**Problem.** implement-spec proves each gate with one violation in one file (`progress.md`: all three probes in `Main.kt`). The review then found three gate holes that a probe per file set or a look at the task graph would have caught. review-spec already has these checks, but they come one phase too late.
Evidence:
- `review.md` F10, review session `42e9079c` (10-09 21:55): `System.out.println` passes detekt (alternate route), and `buildSrc/**/*.kts` isn't linted.
- `review.md` F16, found in the fix session `514f1069` (10-10): the root project had no `check` task, so its `ktlintKotlinScriptCheck` never ran in `build`. The review had assumed it did.
- `review.md` F11: plain `detekt` ran next to `detektMain`/`detektTest`, so every finding was reported twice. The task graph would have shown it.

**Change.** `~/.claude/skills/implement-spec/SKILL.md` → step 4, "prove each gate actually fails the build". +2 / −1 lines.

```diff
-1. Add a small, deliberate violation (bad formatting, a forbidden call, an untested function).
+1. Add a small, deliberate violation (bad formatting, a forbidden call, an untested function). Probe every file set the gate should cover, not one file: main and test sources, module build scripts, root scripts, build logic (`buildSrc`). For a forbidden call, also try its alternate routes (`System.out.println` next to `println`).
 2. Run the build and confirm it fails **on that gate** with the expected rule.
 3. Revert with `git checkout -- <file>` or by deleting the probe file, then confirm `git status` is clean.
+
+Then check the wiring in the task graph (`./gradlew build --dry-run`, `make -n`): every gate task is part of the aggregate command, and runs once.
```

**Expected effect.** M1 adds modules (`sources`, `ecosystems/jvm`) and fixture repos. The implementer finds an unlinted or uncovered file set before the review, so the review has fewer gate findings and no extra fix round for them.

Outcome: **Accepted.** implement-spec step 4 now probes every file set and alternate route per gate, then checks the task graph for wiring and single runs.

### P3 · Turn off zsh's `=word` expansion for the agent's shell

**Problem.** The Bash tool runs zsh. Agents print separators with `echo =====`, which zsh reads as `=cmd` expansion: `(eval):1: ===== not found`. That aborts the whole command, so everything after the separator is lost and the agent runs a second command to get it.
Evidence:
- `db6871fe`, 10-09 20:28: "Read M4 and M5 spec requirements", the M5 part never printed.
- `c6684346`, 10-09 21:18: "Read spec template and M7 spec", re-read the M7 spec with `Read`.
- `514f1069`, 10-10 14:39: "Read existing spec skills for style", re-ran a narrower command.
- `d7a337fa`, 10-10 20:34: "Read both architecture skills", same error.
- This retro run hit it twice as well.

**Change.** `~/.zshrc`, one line. Claude Code's shell snapshot copies non-default shell options (its `# Shell Options` section), so the Bash tool picks it up. Interactive shells lose `=cmd` expansion, which is rarely used. +1 / −0 lines, no prose in any prompt.

```diff
+setopt no_equals   # Claude Code's Bash tool: `echo =====` must not abort the command
```

Verify in a new session: `echo =====; echo ok` prints both lines. If the option doesn't reach the snapshot, the fallback is one line in `~/.claude/CLAUDE.md`: "The shell is zsh: quote separators (`echo '====='`)."

**Expected effect.** No lost output and no repeated reads from separator lines, in every project on this machine.

Outcome: **Accepted.** `setopt no_equals` added to `~/.zshrc`. A fresh interactive shell lists `noequals` under `setopt`, which is what the shell snapshot copies, and `echo =====; echo ok` prints both lines. The running session still uses its old snapshot. Verify once in the next session; if it still fails there, fall back to the `~/.claude/CLAUDE.md` line.

## Not proposed

- **Most rounds answered "as recommended"** (M3, M6, M7, M8 completely; "Accept all recommended" in M7): the questions still mattered. The user deviated with real decisions in M0 (package name, Dependabot), M1 (test failures not caused by the update), M2 (downgrades), M5 (Q5), M6 (GPU runner), and M9 (license), and nobody could tell in advance which question would hit. Dropping questions costs more than it saves.
- **Root cause of the stale viewer in Conductor:** not a workflow cause and not confirmed. All brainstormings happened while `specs/` was untracked; whether that matters is a guess. Worth a Conductor bug report, independent of P1.
- **`progress.md` and `review.md` written to the repo root** and moved by hand (`514f1069`, 10-10 11:03): one occurrence, and implement-spec and review-spec now put both files next to the spec.
- **Long hand-written prompts for implement, review, and fix** (`42e9079c` 21:42 and 21:55, `514f1069` 14:31): already turned into implement-spec and review-spec, which the user created right after. Covered.
- **Markdown edited through `python3 - <<'EOF'` scripts** (37 times): one script dropped a separator, which the agent fixed right away (`d14e36f9`, 15:23). No user correction, no repeated cost. Not a workflow cause.
- **Response cut off mid-stream** (`db6871fe`, 20:50): one-off platform issue.

## What worked

- Gate probes and end-to-end tests paid off: the e2e tests caught that `clikt-core` broke the exit-code and stderr contract (F14), and every gate was shown to fail on a violation.
- brainstorm-spec's "check that the file actually changed" step caught lost edits five times. Without it, wrong decisions would have gone into the specs. P1 keeps it.
- Acceptance criteria with a "How to verify" column let the review check all 27 criteria with evidence and name the one it couldn't verify (AC18).
