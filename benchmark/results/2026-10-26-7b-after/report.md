# Benchmark 2026-10-26

Tool commit `f1fdba6` · model `qwen2.5-coder:7b` · runner GitHub-hosted ubuntu24, 4 CPUs, 15 GB, CPU only · 2026-10-26

27 cases, 2 invalid: powertools-lambda-events-3-11-2, assertj-mockito-5, excluded from the numbers.

The fix requests carry no release notes. `lector pr` passes the notes of a Renovate PR's body, so for those cases the fix-loop numbers are a lower bound.

# JVM (`maven`)

## 90/10 verdict

The hypothesis **doesn't hold**: on 3 green recipe + fix-loop runs, the recipe commits changed 0.0% of the lines on average, the fix commits the rest (holds at 90.0% or more).

## Green rate per mode

Green: no failure caused by the update is left, and tests ran. Per repetition over the valid cases, then the mean and the spread over the repetitions. Pre-existing, flaky, and unattributed failures after the runs are tracked, but never the tool's job.

| Mode | Runs | Green rate | Spread | Pre-existing / flaky / unattributed |
|---|---|---|---|---|
| Recipe only | 22 | 0.0% | 0.0–0.0% | 115 / 0 / 28 |
| Fix loop only | 60 | 6.7% | 5.0–10.0% | 345 / 0 / 0 |
| Recipe + fix loop | 60 | 5.0% | 5.0–5.0% | 345 / 0 / 0 |

## Cases

Each cell: green runs of the runs with a result. Green after the recipe alone: 0 of 22 valid cases. Green after recipe + fix loop: 2 in at least one run, 0 in every run.

| Case | Recipe only | Fix loop only | Recipe + fix loop |
|---|---|---|---|
| dledger-snakeyaml-2 | 0/1 | 0/3 | 0/3 |
| microservice-api-spring-boot-4 | 0/1 | 0/3 | 0/3 |
| deckard-spring-boot-4 | 0/1 | 0/3 | 0/3 |
| javaparser-junit-6 | 0/1 | – | – |
| dropwizard-config-providers-kiwi-5 | 0/1 | 0/3 | 1/3 |
| exec-maven-plugin-testing-harness | 0/1 | 0/3 | 0/3 |
| ayza-mockito-4-2 | 0/1 | 0/3 | 0/3 |
| assertj-commons-collections4-4-5 | 0/1 | 0/3 | 0/3 |
| java-web-security-hibernate-7 | 0/1 | 0/3 | 0/3 |
| kiwi-kiwi-bom-1-1 | 0/1 | 0/3 | 0/3 |
| dropwizard-kafka-clients-3 | 0/1 | 0/3 | 0/3 |
| dropwizard-flyway-9 | 0/1 | 0/3 | 0/3 |
| kiwi-test-prod-deps | 0/1 | 0/3 | 0/3 |
| dropwizard-metrics-mockito-4 | 0/1 | 0/3 | 0/3 |
| powertools-lambda-events-3-11-2 | invalid | invalid | invalid |
| dropwizard-flyway-7 | 0/1 | 0/3 | 0/3 |
| petclinic-spring-boot-3 | 0/1 | – | – |
| petclinic-spring-boot-4 | 0/1 | 0/3 | 0/3 |
| easy-random-datafaker | 0/1 | 0/3 | 0/3 |
| easy-rules-jexl-3-3 | 0/1 | 0/3 | 0/3 |
| vavr-assertj-3-26 | 0/1 | 0/3 | 0/3 |
| assertj-mockito-5 | invalid | invalid | invalid |
| pdfbox-3 | 0/1 | 1/3 | 0/3 |
| snakeyaml-2 | 0/1 | 3/3 | 2/3 |

## Fix loop

Attempts and accepted fixes are means per run. Cut requests: fix requests that left diagnostics out because they didn't fit into the model's context.

| Mode | Attempts | Accepted fixes | Cut requests | Stop reasons |
|---|---|---|---|---|
| Fix loop only | 2.1 | 0.2 | 0 | model_unanswered 2, no_progress 54, nothing_left 4 |
| Recipe + fix loop | 2.1 | 0.2 | 0 | model_unanswered 6, no_progress 51, nothing_left 3 |

## Guardrail rejections

Over the 251 attempts of both fix-loop modes. An attempt can break more than one rule.

| Rule | Rejected attempts | Rate |
|---|---|---|
| diff-size | 1 | 0.4% |
| parse-errors | 6 | 2.4% |
| test-assertions | 2 | 0.8% |

## Runtime

Mean minutes per run.

| Mode | Total | First verify | Recipe | Fix loop |
|---|---|---|---|---|
| Recipe only | 1.4 | 1.2 | 0.1 | 0.0 |
| Fix loop only | 9.2 | 1.0 | 0.0 | 8.2 |
| Recipe + fix loop | 9.1 | 1.0 | 0.1 | 8.0 |

# TypeScript (`npm`)

## Recipe step

None for `npm`: its ecosystem has no recipes, so only the fix loop runs.

## Green rate per mode

Green: no failure caused by the update is left, and tests ran. Per repetition over the valid cases, then the mean and the spread over the repetitions. Pre-existing, flaky, and unattributed failures after the runs are tracked, but never the tool's job.

| Mode | Runs | Green rate | Spread | Pre-existing / flaky / unattributed |
|---|---|---|---|---|
| Fix loop only | 9 | 0.0% | 0.0–0.0% | 0 / 0 / 0 |

## Cases

Each cell: green runs of the runs with a result. Green after the fix loop: 0 in at least one run, 0 in every run.

| Case | Fix loop only |
|---|---|
| appinsights-detective-inquirer-12-11 | 0/3 |
| commently-octokit-rest-22 | 0/3 |
| zaf-toolbox-react-theming-9 | 0/3 |

## Fix loop

Attempts and accepted fixes are means per run. Cut requests: fix requests that left diagnostics out because they didn't fit into the model's context.

| Mode | Attempts | Accepted fixes | Cut requests | Stop reasons |
|---|---|---|---|---|
| Fix loop only | 2.0 | 0.0 | 0 | no_progress 9 |

## Guardrail rejections

Over the 18 attempts of both fix-loop modes. An attempt can break more than one rule.

| Rule | Rejected attempts | Rate |
|---|---|---|
| parse-errors | 1 | 5.6% |
| scope | 11 | 61.1% |
| symbol-comparison | 1 | 5.6% |

## Runtime

Mean minutes per run.

| Mode | Total | First verify | Recipe | Fix loop |
|---|---|---|---|---|
| Fix loop only | 3.6 | 0.2 | 0.0 | 3.4 |

## Manual review

5 of 7 reviewed green runs (of 7 in the fix-loop modes) would be accepted unchanged (71.4%). Reviewer: Independent review agent (Claude Code general-purpose subagent, claude-opus-5-5), blind to M11 and to the run.

| Case | Mode | Repetition | Accepted unchanged | Reason |
|---|---|---|---|---|
| dropwizard-config-providers-kiwi-5 | Recipe + fix loop | 3 | no | Hides the kiwi 5 default change: pins TLSv1.2 in the test's context so the 'provided context as default' test no longer checks the real default; upstream expects TLSv1.3. |
| pdfbox-3 | Fix loop only | 3 | no | Compiles, but PDFParser.load(File) is @Deprecated in PDFBox 3.0.3 ('use Loader#loadPDF(File) instead'); migrates to a deprecated API instead of Loader.loadPDF(file). |
| snakeyaml-2 | Fix loop only | 1 | yes | Correct minimal SnakeYAML 2 migration: Constructor(Class, LoaderOptions) with default LoaderOptions keeps behaviour; only nit is non-alphabetical import order, no style gate. |
| snakeyaml-2 | Fix loop only | 2 | yes | Correct minimal SnakeYAML 2 migration: Constructor(Class, LoaderOptions) with default LoaderOptions keeps behaviour; only nit is non-alphabetical import order, no style gate. |
| snakeyaml-2 | Fix loop only | 3 | yes | Correct minimal SnakeYAML 2 migration: Constructor(Class, LoaderOptions) with default LoaderOptions keeps behaviour; only nit is non-alphabetical import order, no style gate. |
| snakeyaml-2 | Recipe + fix loop | 1 | yes | Correct minimal SnakeYAML 2 migration: Constructor(Class, LoaderOptions) with default LoaderOptions keeps behaviour; only nit is non-alphabetical import order, no style gate. |
| snakeyaml-2 | Recipe + fix loop | 2 | yes | Correct minimal SnakeYAML 2 migration: Constructor(Class, LoaderOptions) with default LoaderOptions keeps behaviour; only nit is non-alphabetical import order, no style gate. |

## Errors and missing runs

Not counted in the numbers.

| Case | Mode | Repetition | Status | Reason |
|---|---|---|---|---|
| javaparser-junit-6 | Fix loop only | 1 | error | MalformedInputException: Input length = 1 |
| javaparser-junit-6 | Fix loop only | 2 | error | MalformedInputException: Input length = 1 |
| javaparser-junit-6 | Fix loop only | 3 | error | MalformedInputException: Input length = 1 |
| javaparser-junit-6 | Recipe + fix loop | 1 | error | MalformedInputException: Input length = 1 |
| javaparser-junit-6 | Recipe + fix loop | 2 | error | MalformedInputException: Input length = 1 |
| javaparser-junit-6 | Recipe + fix loop | 3 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Fix loop only | 1 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Fix loop only | 2 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Fix loop only | 3 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Recipe + fix loop | 1 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Recipe + fix loop | 2 | error | MalformedInputException: Input length = 1 |
| petclinic-spring-boot-3 | Recipe + fix loop | 3 | error | MalformedInputException: Input length = 1 |
