# Retro – M2 Recipe index and OpenRewrite run

Scope: 2026-10-10 22:40 → 2026-10-11 10:52 (local time, since the M1 retro), 5 sessions in 2 workspaces (`victoria`, `sofia`), 1 drilled into. Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since "2026-10-10 22:40"` plus the artifacts above and the M2 implement session in detail. Earlier retros read: [M0](../../done/m0-project-skeleton/retro.md), [M1](../../done/m1-verify-and-report/retro.md).
Depth: Full, because implement-spec cost 50 % more weighted tokens than in M1 (4.8M vs 3.2M) and the M1 retro left a model question open for this one. The drill-down found no harness cause for the cost (see Summary).

The range also contains the M1 retro's apply step and the ship-pr run for M1 (both in `victoria`, before M2 started). They are left out of the M2 metrics.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- M2 ran with no correction, interrupt, or rejected call. The user stepped in once, on a question only they could answer (M1 wasn't in the workspace's branch: "Please checkout main first"). Implement cost more than M1 because the milestone was new ground, not because of harness waste: 23:09–23:14 went into a prototype of OpenRewrite's recipe scanning (49 JARs, an SLF4J classpath failure, one wrong approach reverted) that fixed the generator design before any code was written.
- 1 proposal, a trial. 4 patterns didn't pass the filter.

## Metrics

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 0 / 1 / 5 | 1 / 2 / 4 | No High. The Medium (F1) was a missing build check between two versions that Dependabot bumps separately; fixed with `checkRewritePluginVersion` |
| Autonomy | Interventions (corrections, interrupts, rejected calls, round trips) | 0. Plus 1 decision (M1 dependency) and 1 retried write (`progress.md` changed since read, 23:57) | 0. Plus 1 decision | – |
| Efficiency | Active minutes: implement / review and fixes / architecture docs | 51 / 10 / 9 | 29 / 14 / 14 | Implement +22. The review shows 10 active minutes over a 2.5 h span (08:07–10:43), mostly waiting |
| Efficiency | Fix rounds after the review | 1 | 1 | – |
| Cost | Weighted tokens: implement / review / docs | 4.8M / 1.3M / 0.9M | 3.2M / 1.7M / 0.8M | Total 7.0M vs 5.7M. Implement +1.6M, review −0.4M |
| Cost | Output tokens: implement / review / docs | 199k / 37k / 22k | not measured | Baseline for the Concise style |
| Cost | Startup context (median) | 32k | 32k | – |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`) | 112 lines, ≈ 2.0k tokens | 112 lines, ≈ 2.0k | – |

M2 is a different size from M1: a new module with a build-time generator, a Maven plugin integration, and a real OpenRewrite run (51 files, 3.7k lines changed, against 70 files and 3.3k lines in M1). Implement averaged a context of ≈ 216k tokens per request (31.4M cache-read tokens over 145 requests), which is where most of its 4.8M comes from. The retro rows of the table (0.9M, 9 minutes) mix the end of the M1 retro and the start of this one, so they aren't compared.

## Last retro's changes

| Proposal | Expected effect | Observed | Keep / revert |
|---|---|---|---|
| M1, P1: architecture-docs runs once, after the review, in a fresh session | −0.5M weighted tokens and −12 active minutes per milestone, docs written against reviewed code | Worked, in time more than in tokens. implement-spec ran no docs (`progress.md`: "`docs/architecture/` is written after the review"). The docs ran once in a fresh session: 27 requests at ≈ 100k context each (M1's in-session run: 276k–307k), 9 active minutes against 14. Weighted tokens are 0.88M against 0.84M in M1, because M2's run was a full write of a larger document, not a check. No claim was stale after the review fixes | Keep |
| M1, P2: `setopt no_nomatch` | No `no matches found` errors and no false "nothing found" | Worked. No such error in any session of the range (2 Bash errors in all, neither a glob) | Keep |
| M1, P3: retro script counts a rejection only for an error result | No false rejected calls | Worked. Summary: `Rejected tool calls: none` | Keep |
| M1, output style `Concise` | Fewer output tokens, reports still carry what's needed to act | Worked. The user acted on first replies ("yes, commit them", "commit this and then /retro"), with no request for more detail. Baseline output tokens are in Metrics | Keep |
| M0, P1–P3 (brainstorm in chat, gate probes, `no_equals`) | – | Holds. No `===== not found`. No brainstorming in the range | Keep |

## Proposals

### P1 · Trial: architecture-docs runs on Sonnet 5.5 for M3

**Problem.** The M1 retro left one open question: whether architecture-docs needs the strongest model. The evidence so far is one Sonnet 5.5 run (M1, check mode after the review: 297k weighted, peak context 85k, found 3 stale claims) and one Opus 5.5 run (M2, write then check: 878k weighted, 27 requests, found nothing to correct in its own text). The work is reading code and comparing it with claims, plus a few paragraphs of prose in the repo's voice, which is mechanical more than judgment. The two runs aren't comparable (check against write), so this is a trial, not a switch.

**Lever.** Model. Trial for the architecture-docs phase of M3, until the next retro.

**Change.** `~/.claude/skills/architecture-docs/SKILL.md` → frontmatter. +1 line.

```diff
 ---
 name: architecture-docs
 description: …
+model: sonnet
 ---
```

If the skill frontmatter doesn't switch the model in this Claude Code version, the fallback is `/model` before invoking the skill. Verify in a new session by looking at the model of the first request of the phase in `session_evidence.py`.

**Expected effect.** The architecture-docs phase costs less per token with the same result. Keep it if `/architecture-docs` check mode finds no stale claim in M3's review or ship-pr, and the doc needs no correction from the user. Revert with the one line if the next retro shows a wrong claim that the check missed.

Outcome: **Accepted.** `model: sonnet` added to the frontmatter of `~/.claude/skills/architecture-docs/SKILL.md` (uncommitted in `~/.claude/skills`). Whether the frontmatter switches the model couldn't be checked in this session; verify at M3 with the model of the phase's first request, and fall back to `/model` if it doesn't.

## Not proposed

- **The workspace wasn't based on a branch with M1** (`f2b9b14b`, 23:06: the agent asked how to proceed, the user answered "checkout main first"): one occurrence, and the question was the right one: the user decided in one word. A rule in implement-spec to check for the previous milestone would save one round trip a milestone at most.
- **Implement context averaging 216k per request:** it follows from reading the M1 code, the spec, and the brainstorming, then writing many files in one session. A subagent for the prototype step would cut part of it, but one milestone isn't enough evidence for it. Watch the number in M3 (`Metrics`).
- **zsh's `path` variable is tied to `PATH`** (this session, 10:49: `path="${l%%#*}"` inside a loop emptied `PATH` and `sed` wasn't found): one occurrence, found and rerun within a minute. Not a harness gap.
- **`progress.md` written while the user's editor had it open** (`f2b9b14b`, 23:57: "File has been modified since read"): one occurrence, and the agent read the file again and wrote it. No lost edit.

## What worked

- The prototype step in implement-spec's first minutes: it settled the BOM and plugin pair (3.37.0 with 6.46.1, because 3.38.0's artifacts weren't downloadable yet), where the license and coordinates come from, and one dead end (a recipe whose class needs SLF4J), all before the first commit. The review then found no design finding, only the build check (F1) and cleanup.
- review-spec's real runs of the artifact and the gates: the Medium (F1) came from asking what Dependabot does to two versions that must match, a case no test could have shown.
- The review and docs phases each started in a fresh session, as the M1 retro suggested: review at a normal startup context, docs at ≈ 100k instead of ≈ 290k.
