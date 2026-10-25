# M11 evidence: real tool output and model edits

Copied verbatim from the local comparison runs (2026-10-24, tool commit `2f543e1`, macOS, Maven 3, JDK 21). Tests that use real tool wording take it from here (AGENTS: "Tool output in tests uses the real tool's wording").

## dledger-snakeyaml-2: GPG signing after a correct fix (all models)

```
Attempt 1 on proxy/src/main/java/io/openmessaging/storage/dledger/proxy/util/ConfigUtils.java: Rejected(reason=no progress: 0 build errors, 1 compile errors, 0 test failures -> 1 build errors, 0 compile errors, 0 test failures
Errors after the edits:
- [maven] Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:1.6:sign (sign-artifacts) on project dledger-all: Unable to execute gpg command: Error while executing process. Cannot run program "gpg": error=2, No such file or directory -> [Help 1], violations=[])
The rejected edits:
- proxy/src/main/java/io/openmessaging/storage/dledger/proxy/util/ConfigUtils.java: "import java.io.InputStream;\n\npublic class ConfigUtils {" -> "import java.io.InputStream;\nimport org.yaml.snakeyaml.LoaderOptions;\n\npublic class ConfigUtils {"
- proxy/src/main/java/io/openmessaging/storage/dledger/proxy/util/ConfigUtils.java: "DLedgerProxyConfig config = null;\n        final Yaml yaml = new Yaml(new Constructor(DLedgerProxyConfig.class));" -> "DLedgerProxyConfig config = null;\n        final Yaml yaml = new Yaml(new Constructor(DLedgerProxyConfig.class, new LoaderOptions()));"
```

## dropwizard-flyway-9: unmasked error in the same call chain (30B, correct) vs. error in the edited line (7B, wrong)

### qwen3-coder-30b

```
Attempt 1 on symbol:ignoreFutureMigrations: Rejected(reason=no progress: 0 build errors, 1 compile errors, 0 test failures -> 0 build errors, 1 compile errors, 0 test failures
Errors after the edits:
- [javac] src/main/java/io/dropwizard/flyway/FlywayFactory.java:701: cannot find symbol
  symbol:   method ignoreIgnoredMigrations(boolean)
  location: class org.flywaydb.core.api.configuration.FluentConfiguration, violations=[])
The rejected edits:
- src/main/java/io/dropwizard/flyway/FlywayFactory.java: "              .ignoreFutureMigrations(ignoreFutureMigrations)\n" -> "              .ignoreMigrationPatterns(\"future\")\n"
```

### qwen2.5-coder-7b

```
Attempt 1 on symbol:ignoreFutureMigrations: Rejected(reason=no progress: 0 build errors, 1 compile errors, 0 test failures -> 0 build errors, 1 compile errors, 0 test failures
Errors after the edits:
- [javac] src/main/java/io/dropwizard/flyway/FlywayFactory.java:700: cannot find symbol
  symbol:   method ignoreMissingMigrations(boolean)
  location: class org.flywaydb.core.api.configuration.FluentConfiguration, violations=[])
The rejected edits:
- src/main/java/io/dropwizard/flyway/FlywayFactory.java: "ignoreFutureMigrations(ignoreFutureMigrations)" -> "ignoreMissingMigrations(ignoreFutureMigrations)"
```

The initial error before the attempt (reproduced with `mvn -B clean test-compile` at the case's head `48830e3`, JDK 21, `LANG=C`):

```
[ERROR] /…/src/main/java/io/dropwizard/flyway/FlywayFactory.java:[700,15] cannot find symbol
  symbol:   method ignoreFutureMigrations(boolean)
  location: class org.flywaydb.core.api.configuration.FluentConfiguration
```

Lines 700–703 of `FlywayFactory.java` at the head:

```
              .ignoreFutureMigrations(ignoreFutureMigrations)
              .ignoreIgnoredMigrations(ignoreIgnoredMigrations)
              .ignoreMissingMigrations(ignoreMissingMigrations)
              .ignorePendingMigrations(ignorePendingMigrations)
```

The 30B replaced line 700; the next error is at line 701, which it didn't change (unmasked). The 7B's replacement in line 700 calls another removed method; the error stays in line 700, which it changed.

## pdfbox-3: search text not in the file (30B)

```
Attempt 1 on symbol:load: Rejected(reason=src/main/java/com/example/PdfPages.java: the search text is not in the file, violations=[])
The rejected edits:
- src/main/java/com/example/PdfPages.java: "import java.io.File;\nimport java.io.IOException;\norg.apache.pdfbox.pdmodel.PDDocument;" -> "import java.io.File;\nimport java.io.IOException;\nimport org.apache.pdfbox.pdmodel.PDDocument;\nimport org.apache.pdfbox.pdfparser.PDFParser;"
- src/main/java/com/example/PdfPages.java: "        try (PDDocument document = PDDocument.load(file)) {\n            return document.getNumberOfPages();\n        }" -> "        try (PDDocument document = PDFParser.load(file)) {\n            return document.getNumberOfPages();\n        }"
```

## dropwizard-metrics-mockito-4: search text found twice (14B)

```
Attempt 2 on metrics-core/src/test/java/com/codahale/metrics/TimerTest.java: Rejected(reason=metrics-core/src/test/java/com/codahale/metrics/TimerTest.java: the search text matches 2 times, it must match exactly once, violations=[])
The rejected edits:
- metrics-core/src/test/java/com/codahale/metrics/TimerTest.java: "verifyZeroInteractions(reservoir);" -> "verifyNoInteractions(reservoir);"
- metrics-core/src/test/java/com/codahale/metrics/TimerTest.java: "import org.mockito.Mockito;" -> "import org.mockito.Mockito;\nimport static org.mockito.Mockito.verifyNoInteractions;"
```

## dropwizard-kafka-clients-3: guessed path of a test without a location (30B)

```
Attempt 2 on (no location): Rejected(reason=the edits break the guardrails:
- [scope] io/dropwizard/kafka/integration/DropwizardKafkaIT.java: Git doesn't track the file, so it is generated or ignored, violations=[[scope] io/dropwizard/kafka/integration/DropwizardKafkaIT.java: Git doesn't track the file, so it is generated or ignored])
The rejected edits:
- io/dropwizard/kafka/integration/DropwizardKafkaIT.java: "import kafka.server.BrokerState;" -> "import org.apache.kafka.metadata.BrokerState;"
- io/dropwizard/kafka/integration/DropwizardKafkaIT.java: "kafka.server.BrokerState state = kafkaServer.brokerState();" -> "org.apache.kafka.metadata.BrokerState state = kafkaServer.brokerState();"
```

The file is tracked at `src/test/java/io/dropwizard/kafka/integration/DropwizardKafkaIT.java`.
