# Retro – M4 Guardrails

Scope: 2026-10-11 18:44 → 2026-10-12 10:24 (local time, since the M3 retro), 5 sessions in 2 workspaces (`rome`, `irvine`), 4 drilled into, plus the M3 retro session (`50c72636`) for the decision flow. Artifacts: [`spec.md`](spec.md), [`progress.md`](progress.md), [`review.md`](review.md).
Evidence: `session_evidence.py --since "2026-10-11 18:44"` plus the artifacts above, `git log` of `main` and the M3 branch, and `gh pr view 5` / `6`. Earlier retros read: [M0](../../done/m0-project-skeleton/retro.md), [M1](../../done/m1-verify-and-report/retro.md), [M2](../../done/m2-recipe-index/retro.md), M3 (commit `257ebc3`, not on `main`, see Summary).
Depth: Full, because `review.md` has two High findings (F1, F2), and because the M3 review's decisions never reached the M4 spec, which no earlier step caught.

The range starts with the ship-pr run for M3 (`rome`, 18:48). It counts as `ship-pr` and is left out of the M4 phase comparison.

Outcome per proposal: **Accepted** · **Rejected** (with reason) · **Changed** (with what changed).

## Summary

- **M4 was built against an outdated spec.** The M3 review left three decisions open (D1–D3). They weren't answered in the review but 1.5 h later in the retro session, after ship-pr had opened PR #5. The user merged #5 at 18:48:30; ship-pr pushed the two commits with the answers seconds later, saw the PR was merged, and opened PR #6 for them. #6 is still open. M4 started at 18:49 from `main` without it, so its spec lacks section 5 and AC31 (reject an attempt that removes the PR's own package, D2), and the M4 code doesn't implement it. `main` also lacks the M3 retro, M5's AC37 (D1), and M6's change (D3).
- The decision itself is the weak link: in both reviews the user had to ask for it in another form before answering ("answer the 3 questions as multiple choice. in chat", "explain the problem exactly to me using a easy example"). Once asked well, the M4 answer was the most valuable input of the milestone (the `get → fetch` rename case that changed the symbol rule).
- 3 proposals. 3 patterns didn't pass the filter.

**Action outside the harness, before shipping M4:** merge PR #6, rebase this branch on `main` (the M4 spec conflicts: both sides edit it), and implement AC31 test-first. That's product work for review-spec or implement-spec, not part of this retro.

## Metrics

| Goal | Metric | This retro | Previous | Change |
|---|---|---|---|---|
| Quality | Review findings High / Medium / Low | 2 / 3 / 3 | 0 / 4 / 7 (+3 Low while fixing) | +2 High: F1 (pre-existing placeholders rejected), F2 (blocklist saw only `pom.xml`); F2 harvested into `AGENTS.md`. Plus one missed criterion (AC31) that no review could see |
| Autonomy | Interventions (corrections, interrupts, rejected calls, round trips) | 2: a re-explain request before the decision (08:22), "commit" after architecture-docs (10:24). Plus 1 valuable decision (08:33) | 1 ("commit all" after the review) | +1. No "commit" after the review anymore |
| Efficiency | Active minutes: implement / review and fixes / architecture docs | 40 / 30 / 14 | 56 / 31 / 8 | −16 implement |
| Efficiency | Fix rounds after the review | 2 (F1–F8, then the F5 redesign after the decision) | 1 | +1, caused by the decision, not by a failed fix |
| Cost | Weighted tokens: implement / review / docs | 4.2M / 2.0M / 0.6M | 6.6M / 2.1M / 0.6M | Total 6.8M vs 9.3M |
| Cost | Output tokens: implement / review / docs | 181k / 86k / 20k | 214k / 65k / 24k | – |
| Cost | Implement context per request (average) | 231k | 289k | −58k, under the 300k mark the M3 retro set |
| Cost | Startup context (median) | 33k | 32k | – |
| Cost | Always-loaded files (`AGENTS.md` + `CLAUDE.md`) | 116 lines, ≈ 2.4k tokens | 114 lines, ≈ 2.2k | +2 lines from the review harvest |

M4 is smaller than M3 (68 files, 3.2k lines against 66 files, 4.5k lines), so the lower implement cost is mostly size: ≈ 1.3k weighted tokens per changed line, as in M2 (M3: 1.5k).

## Last retro's changes

| Proposal | Expected effect | Observed | Keep / revert |
|---|---|---|---|
| M3, P1: review-spec commits each fix group itself | No "commit" prompt after the review | Worked. `e77d45ae` committed F1–F8 in 5 `fix:` commits plus one `docs:` commit (08:13–08:19), and the F5 redesign in two more (08:47). The user only answered the decision | Keep |
| M3, P2: remove the Sonnet trial from architecture-docs | No change in cost, no wrong model claim | Holds. All 26 docs requests on Opus, 0.6M as in M3 | Keep |
| M3, not proposed: watch implement context, trial if the average passes 300k | – | 231k, no trial needed | – |
| M1, P1 – P3, output style, M0, P1 – P3 | – | Hold. No glob or `=====` errors, no false rejections, reports acted on directly | Keep |

## Proposals

### P1 · review-spec asks each open decision with a concrete example, as a question, before its report

**Problem.** review-spec reports open decisions as prose with options. In both reviews since the M2 retro, the user couldn't answer them in that form, and in M3 they went unanswered until another session, which is how D1–D3 missed `main` (Summary).
Evidence:
- M3 review (`e629ed49`), 17:03: D1–D3 in the report. The user said "commit all" and moved on. Answered at 17:40 in the retro session: "accept. And answer the 3 questions as multiple choice. in chat", then via the question tool at 18:41.
- M4 review (`e77d45ae`), 08:22: "About the decision explain the problem exactly to me using a easy example. Then I will decide what to do". The example (08:23) led to the user's counter-case at 08:33 and a better rule.

**Lever.** Skill (review-spec body). +1 bullet, 1 line changed.

**Change.** `~/.claude/skills/review-spec/SKILL.md`

Phase 1, after "Then sort the findings: …":

```diff
+- **Ask each decision so it can be answered at once.** Per decision: the problem as a small concrete example (a few lines of code or a scenario: what the model does, what happens), then the options, each with what it means in that example, and a recommendation. Ask with the question tool if there is one, one question per decision, before the report, not only in it. Record the answers in `review.md` and carry them into the spec, concept, and later specs in the closing `docs:` commit.
```

Report:

```diff
-- **Needs your decision:** each open question with its options and a recommendation. This is the only point where the workflow waits for the user.
+- **Needs your decision:** only questions still open after the question tool, each with its example, options, and recommendation. This is the only point where the workflow waits for the user.
```

**Expected effect.** No re-explain or "ask it differently" round trip in M5, and every review decision is committed in the review session, before ship-pr.

Outcome: **Accepted.** Applied as in the diff to `~/.claude/skills/review-spec/SKILL.md` (phase 1 bullet, report line). Uncommitted in `~/.claude/skills`.

### P2 · implement-spec stops if an open PR changes its spec

**Problem.** Nothing checks that the spec on the new branch is the latest one. A spec change in an unmerged PR is invisible from `main`, and M4 implemented the wrong scope (AC31 missing) without any gate noticing. With parallel workspaces this can happen any time a decision lands after a PR opened.
Evidence:
- `irvine` `16800b22`, 18:49: M4 started from `main` while PR #6 (open since 18:54 the day before, still open now) changes `specs/open/m4-guardrails/spec.md`, `docs/concept.md`, and the M5 and M6 specs.
- One occurrence, but measured: one acceptance criterion and two later specs' changes missing from `main` for 16 hours.

**Lever.** Skill (implement-spec, step 1). +1 bullet, one `gh` call per milestone.

**Change.** `~/.claude/skills/implement-spec/SKILL.md` → `## 1. Understand before writing anything`, after the `git status` bullet:

```diff
+- Check that the spec is current: `git fetch -q origin && git log --oneline HEAD..origin/main` (merge `main` first if it's ahead) and `gh pr list --state open --json number,title,files -q '.[] | select(any(.files[]; .path | startswith("specs/") or startswith("docs/concept"))) | "#\(.number) \(.title)"'`. If an open PR changes this feature's spec or the concept, stop and ask: it usually has to be merged first.
```

**Expected effect.** A milestone never starts on a stale spec. Cost: one command at the start of each implement session.

Outcome: **Accepted.** Applied as in the diff to `~/.claude/skills/implement-spec/SKILL.md`, step 1. The `gh` filter was run in this session and lists PR #6. Uncommitted in `~/.claude/skills`.

### P3 · architecture-docs commits its doc itself

**Problem.** architecture-docs ends with "Offer a `docs:` commit", and the user ends the offer with a one-word yes, the same pattern M3's P1 removed from review-spec.
Evidence:
- M2 docs (`sofia`), 10:52: "commit this and then /retro".
- M4 docs (`4bc715f1`), 10:24: "commit".
- (M3's docs session went straight to `/ship-pr`, which committed it.)

**Lever.** Skill (architecture-docs body). 1 line changed.

**Change.** `~/.claude/skills/architecture-docs/SKILL.md`, last section:

```diff
-Don't commit unless the user asked. Offer a `docs:` commit.
+Commit the result as one `docs:` commit on the feature branch (never on `main`); asking for this skill authorizes it. Don't push; ship-pr does.
```

**Expected effect.** −1 intervention per milestone. No quality change: the diff is still in the PR.

Outcome: **Accepted.** Applied as in the diff to `~/.claude/skills/architecture-docs/SKILL.md`, last section. Uncommitted in `~/.claude/skills`.

## Not proposed

- **ship-pr pushed to an already merged PR** (`3044d28e`, 18:48–18:54): ship-pr's "if a PR already exists, push only" doesn't check the PR's state. The agent noticed and opened #6 itself, so the loss came from #6 staying open, which P2 catches. One occurrence.
- **Two High findings in M4 (F1, F2):** product bugs, found and fixed by the review as intended; F2 is now an `AGENTS.md` rule. No sign an earlier harness step skipped a check it has.
- **`progress.md` "modified since read"** (M3 12:13, M4 19:29): one extra request each time, no lost edit. Third and fourth occurrence across retros, still harmless.

## What worked

- M3's P1: review-spec committing its own fixes removed the end-of-review commit round trip and the index staging.
- The decision exchange in the M4 review once it had an example: the user's rename case turned a weak rule ("a diagnostic names the method") into a sound one ("the logic survives").
- ship-pr detecting the merged PR and opening a follow-up instead of losing the commits outright.
