# Retro – M3 Fix loop

Scope: 2026-10-11 10:52 → 17:09 (local time, since the M2 retro), 7 sessions in 2 workspaces (`sofia`, `rome`), 4 drilled into. Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since "2026-10-11 10:52"` plus the artifacts above, the model and context size per request from the raw transcripts, and the tool result sizes of the implement session. Earlier retros read: [M0](../m0-project-skeleton/retro.md), [M1](../m1-verify-and-report/retro.md), [M2](../m2-recipe-index/retro.md).
Depth: Full, because the M2 trial (architecture-docs on Sonnet) needed its keep-or-revert decision, and implement-spec cost 38 % more weighted tokens than in M2 (6.6M vs 4.8M).

The range starts with the end of the M2 retro and the ship-pr run for M2 (`sofia`). They count as `no skill`, `retro`, and `ship-pr` and are left out of the M3 phase comparison.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- M3 ran with no correction, interrupt, or rejected call. The only interventions were the same as in M2: the user had to say "commit" after review-spec, and the agent then rebuilt 8 commits from a working tree in which files mixed several fixes. The Sonnet trial for architecture-docs never ran: every request of the phase went to Opus, and the agent then told the user it had used Sonnet.
- 2 proposals, one of them a removal. 6 patterns didn't pass the filter.

## Metrics

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 0 / 4 / 7, plus 3 Low found while fixing | 0 / 1 / 5 | +3 Medium. F1–F3 are one design gap (an attempt isn't fully contained); harvested into `AGENTS.md` |
| Autonomy | Interventions (corrections, interrupts, rejected calls, round trips) | 1 ("commit all" after the review). Plus 1 question about the model, which surfaced the failed trial | 1 decision ("checkout main first") | – |
| Efficiency | Active minutes: implement / review and fixes / architecture docs | 56 / 31 / 8 | 51 / 10 / 9 | Review +21: 4 Medium fixes, 3 found while fixing, and an opt-in real-model run that ran out of memory in Docker |
| Efficiency | Fix rounds after the review | 1 | 1 | – |
| Cost | Weighted tokens: implement / review / docs | 6.6M / 2.1M / 0.6M | 4.8M / 1.3M / 0.9M | Total 9.3M vs 7.0M |
| Cost | Output tokens: implement / review / docs | 214k / 65k / 24k | 199k / 37k / 22k | – |
| Cost | Implement context per request (average / end) | 289k / 434k | 216k / – | +73k average |
| Cost | Startup context (median) | 32k | 32k | – |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`) | 114 lines, ≈ 2.2k tokens | 112 lines, ≈ 2.0k | +2 lines: 3 bullets sharpened by the review harvest |

M3 is larger than M2: 66 files and 4.5k lines changed against 51 files and 3.7k lines (+22 %), plus a new module and a prompt experiment against a real model (11:51–11:59) that found the `analysis` field. Implement cost per changed line rose from ≈ 1.3k to ≈ 1.5k weighted tokens. 72 % of implement's cost is cache reads: the context grew from 30k to 434k over 164 requests, and 95k of it came from the first three minutes of reading (whole modules with `for f in …; do cat`).

## Last retro's changes

| Proposal | Expected effect | Observed | Keep / revert |
|---|---|---|---|
| M2, P1: architecture-docs on Sonnet 5.5 (trial) | The docs phase costs less per token with the same result | No chance: the trial never ran. All 65 requests of session `a28443ff` (17:00–17:09) went to `claude-opus-5-5`; the `model: sonnet` line in the skill's frontmatter didn't switch the model. Asked at 17:07 "which model did you use", the agent answered "Claude Sonnet 5.5", reading the line instead of checking | Revert (P2) |
| M1, P1: architecture-docs runs once, after the review, in a fresh session | Docs written against reviewed code, less context | Worked. Fresh session at 33k, 24 requests, 0.6M weighted and 8 minutes (M2: 0.9M, 9). ship-pr found nothing to correct | Keep |
| M1, P2: `setopt no_nomatch` | No glob errors | Holds. No `no matches found` | Keep |
| M1, P3: retro script counts a rejection only for an error result | No false rejected calls | Holds. `Rejected tool calls: none` | Keep |
| M1, output style `Concise` | Fewer output tokens, reports still actionable | Holds. The user acted on each final report without asking for more | Keep |
| M0, P1–P3 | – | Hold. No brainstorming in the range | Keep |

## Proposals

### P1 · review-spec commits each fix group itself

**Problem.** review-spec leaves its fixes uncommitted as a checkpoint. In both reviews since the M1 retro, the user ended that checkpoint with a one-word yes, never a change. At commit time the agent then has to split a working tree in which files mix several fixes: in M3 it staged intermediate file versions through the index to build 8 commits, then checked that each intermediate commit compiled, because three were assembled from partial files.
Evidence:
- `sofia`, M2 review, 10:43: "yes, commit them".
- `rome` `e629ed49`, M3 review, 17:00: "commit all". 17:01: "Several files mix more than one fix, so I'll stage intermediate versions of those files through the index and make 8 commits". 17:01–17:03: compile check of each intermediate commit.

implement-spec already commits after each green step, and ship-pr owns the push. Local commits are as reversible as an uncommitted diff, and `review.md` still shows every change and its outcome.

**Lever.** Skill (review-spec body). −1 sentence, +1 bullet, 1 sentence changed.

**Change.** `~/.claude/skills/review-spec/SKILL.md`

Intro, after the diagram:

```diff
-By default all three phases run in one go, without stopping after the review. Nothing is committed without the user, so the uncommitted diff plus `review.md` is the checkpoint. Run fewer phases only when asked: …
+By default all three phases run in one go, without stopping after the review. The user asking for this skill is the authorization to commit on the feature branch; pushing stays with ship-pr. Run fewer phases only when asked: …
```

Phase 2, after "Formatter and linter side effects":

```diff
+- **Commit each fix group once it's green**, before starting the next: Conventional Commits with the module as scope, the finding IDs in the body (`F1, F13`). Then a file never mixes two fixes at commit time. `review.md`, `progress.md`, and `AGENTS.md` go into one `docs:` commit at the end.
```

Report:

```diff
-Don't commit or push unless the user asked; offer it, split into Conventional Commits by fix group. Never commit or push on `main`; …
+Don't push; ship-pr does. Never commit or push on `main`; …
```

The report's **Fixes** section then lists the commits.

**Expected effect.** No "commit" prompt after review-spec in M4 (−1 intervention per milestone), and no index staging or compile check of rebuilt commits at the end of the review.

Outcome: **Accepted.** Applied to `~/.claude/skills/review-spec/SKILL.md` as in the diff; the report's **Fixes** item now names the commits. Uncommitted in `~/.claude/skills`.

### P2 · Remove the Sonnet trial from architecture-docs

**Problem.** The `model: sonnet` line added by the M2 retro had no effect (see *Last retro's changes*), and it misled the agent into telling the user a wrong model. The fallback that the M2 retro named, `/model` before the skill, costs more than it could save: switching the model mid-session rewrites the whole context into the cache. The one `/model` switch in the range (`sofia`, 10:52, at 145k context) is the entire `no skill` row: 0.53M weighted, about as much as M3's whole docs phase (0.6M, 6 % of the range). The M3 doc, written on Opus, needed no correction.

**Lever.** Model (removal). −1 line.

**Change.** `~/.claude/skills/architecture-docs/SKILL.md` → frontmatter. Revert commit `91607c9` in `~/.claude/skills`.

```diff
 description: …
-model: sonnet
 ---
```

**Expected effect.** No change in cost or quality (the line did nothing), one wrong source of truth less. Closes the open model question for this phase: at 6 % of the cost, a cheaper model isn't worth a model switch or a subagent.

Outcome: **Accepted.** The `model: sonnet` line is removed from `~/.claude/skills/architecture-docs/SKILL.md` (uncommitted in `~/.claude/skills`, not as a revert commit).

## Not proposed

- **Implement context (289k average, 434k at the end):** cost per changed line rose about 15 %, and the up-front read of whole modules (95k tokens, held across ≈ 155 requests, ≈ 0.9M weighted) is the largest single lever. But reading the existing code is what kept M3 consistent with the existing code, and a narrower read or an Explore subagent risks that for about 10 % of one phase. Watch it in M4; propose a trial if the average passes 300k again.
- **Kover counting unreachable branches** (implement 11:25–11:31, review F12 with three fixes that failed Kover first): already harvested into `AGENTS.md` by this review. The next retro checks whether M4 still runs into it.
- **`progress.md` "modified since read"** (M2 23:57, M3 12:13): twice, but each time one extra request and no lost edit.
- **`sleep 60` blocked by the harness** (`48bd252d`, 12:00): one occurrence, and the block message led straight to `Monitor`.
- **zsh doesn't split a variable into words** (`48bd252d`, 12:12: "my loop passing the three task names as one argument"): one occurrence, found within a minute.
- **Haiku for ship-pr** (user, M2 retro, 11:09): answered then; ship-pr cost 0.2M (2 % of the range).

## What worked

- implement-spec's real-model run during the implementation (11:51–11:59): five prompt variants against the real 7B model found the `analysis` field (5/5 correct answers), so AC36 passed on the first container run.
- review-spec's harvest: F1, F2, F5, and F12 became three sharpened `AGENTS.md` bullets instead of new ones (+2 lines).
- The fresh-session order of review, then docs, then ship-pr: each started at ≈ 32k context.
