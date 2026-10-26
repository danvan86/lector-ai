# Local model comparison and fix-loop log analysis (2026-10-24)

Mac M3 Pro, native Ollama (Metal), fix-loop-only, 1 repetition, tool commit 2f543e1. The runs' logs stayed local; the excerpts the tests need are in [evidence.md](evidence.md).

| Case | 7B | 14B | 30B |
|---|---|---|---|
| snakeyaml-2 | green | red (parse error, same edit twice) | green |
| pdfbox-3 | green | green | red (broke import line in search) |
| dledger-snakeyaml-2 | red* | red* | red* |
| dropwizard-flyway-9 | red | green (removed the calls) | red* |
| dropwizard-flyway-7 | red (missing import) | green | green |
| dropwizard-kafka-clients-3 | partial (2 tests) | partial (1 test) | partial (1 test) |
| dropwizard-metrics-mockito-4 | red | partial (7 -> 3) | red |
| easy-rules-jexl-3-3 | red | red | red |
| green | 2/8 | 3/8 | 2/8 |

`*` = a correct fix was rejected by the tool.

## Findings

1. Correct fixes thrown away by the progress check (Cluster.kt Progress, count-based, build errors first):
   - dledger (all 3 models): the right fix cleared the compile error; `mvn verify` then reached maven-gpg-plugin
     (`Cannot run program "gpg"`): 1 compile error -> 1 build error counts as worse.
   - flyway-9 (30B): the right fix for line 702 uncovered the next removed method in the same fluent chain (line 701);
     javac reports one error per chain, so the count stayed 1.
2. Build errors are never checked against the base commit (Attribution.kt:48/51: `buildProblems` always
   `causedByUpdate`). An environment problem (gpg) can never be attributed away. `verify` has no `-Dgpg.skip`.
3. 12 of 16 retries were byte-identical to the rejected attempt before (temperature 0.3, "Don't repeat them" ignored).
   Each costs a full build and counts toward `attemptsWithoutProgress = 2`, which ends the loop.
4. Apply-failure feedback is too vague to correct from:
   - "the search text is not in the file" names no edit and shows no real text (pdfbox 30B, easy-rules 7B, kafka 7B
     editing YAML it was never shown).
   - "matches 2 times" names no lines (metrics 14B, repeated identically).
   - "Git doesn't track the file" when the model guessed a path (kafka 30B/14B: `io/dropwizard/...IT.java` without
     `src/test/java/`): the failing test had no location, so no snippet and no real path was sent.
5. Missing imports: flyway-7 7B had the right fix but no import of `MigrateResult`; the retry was identical. The class
   is unique in the new JAR; a deterministic hint (FQN) would fix it.
6. Budget: runs ended after ~1 minute of a 30-minute budget because 2 attempts without progress stop the loop.
7. Local only: macOS locale de_DE made forked javac write German messages (metrics case), a German warning counted as an
   error. The tool image sets LANG=C.UTF-8, so CI is unaffected.
