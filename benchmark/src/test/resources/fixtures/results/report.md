# Benchmark 2026-10-12

Tool commit `0123456` · model `qwen2.5-coder:7b` · runner GitHub-hosted ubuntu-24.04, 4 CPUs, 16 GB · 2026-10-12

3 cases, 1 invalid: jackson-3, excluded from the numbers.

## 90/10 verdict

The hypothesis **doesn't hold**: on 4 green recipe + fix-loop runs, the recipe commits changed 47.5% of the lines on average, the fix commits the rest (holds at 90.0% or more).

## Green rate per mode

Green: no failure caused by the update is left, and tests ran. Per repetition over the valid cases, then the mean and the spread over the repetitions. Pre-existing, flaky, and unattributed failures after the runs are tracked, but never the tool's job.

| Mode | Runs | Green rate | Spread | Pre-existing / flaky / unattributed |
|---|---|---|---|---|
| Recipe only | 2 | 50.0% | 50.0–50.0% | 1 / 1 / 0 |
| Fix loop only | 6 | 33.3% | 0.0–50.0% | 0 / 0 / 0 |
| Recipe + fix loop | 4 | 100.0% | 100.0–100.0% | 0 / 0 / 0 |

## Cases

Each cell: green runs of the runs with a result. Green after the recipe alone: 1 of 2 valid cases. Green after recipe + fix loop: 2 in at least one run, 2 in every run.

| Case | Recipe only | Fix loop only | Recipe + fix loop |
|---|---|---|---|
| guava-21 | 0/1 | 2/3 | 2/2 |
| boot-3 | 1/1 | 0/3 | 2/2 |
| jackson-3 | invalid | invalid | invalid |

## Fix loop

Attempts and accepted fixes are means per run. Cut requests: fix requests that left diagnostics out because they didn't fit into the model's context.

| Mode | Attempts | Accepted fixes | Cut requests | Stop reasons |
|---|---|---|---|---|
| Fix loop only | 2.5 | 0.5 | 1 | no_progress 4, nothing_left 2 |
| Recipe + fix loop | 0.8 | 0.8 | 0 | nothing_left 4 |

## Guardrail rejections

Over the 18 attempts of both fix-loop modes. An attempt can break more than one rule.

| Rule | Rejected attempts | Rate |
|---|---|---|
| diff-size | 2 | 11.1% |
| test-deletion | 1 | 5.6% |

## Runtime

Mean minutes per run.

| Mode | Total | First verify | Recipe | Fix loop |
|---|---|---|---|---|
| Recipe only | 8.6 | 3.5 | 5.1 | 0.0 |
| Fix loop only | 15.3 | 3.5 | 0.0 | 11.8 |
| Recipe + fix loop | 11.3 | 3.5 | 5.1 | 2.7 |

## Manual review

2 of 3 reviewed green runs (of 6 in the fix-loop modes) would be accepted unchanged (66.7%). Reviewer: A. Reviewer.

| Case | Mode | Repetition | Accepted unchanged | Reason |
|---|---|---|---|---|
| guava-21 | Fix loop only | 1 | yes | Uses MoreObjects.firstNonNull, as the release notes say |
| guava-21 | Recipe + fix loop | 1 | yes | Same fix as in fix loop only |
| boot-3 | Recipe + fix loop | 2 | no | Catches the new exception instead of migrating the call |

## Errors and missing runs

Not counted in the numbers.

| Case | Mode | Repetition | Status | Reason |
|---|---|---|---|---|
| guava-21 | Recipe + fix loop | 3 | error | Ollama is not reachable at http://localhost:11434: java.net.ConnectException |
| boot-3 | Recipe + fix loop | 3 | missing | no result |
