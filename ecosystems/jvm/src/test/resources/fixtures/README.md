# Fixture repos

Small Maven projects for the parser tests. Each one breaks in one way.

| Fixture | Breaks with | Captured output |
|---|---|---|
| `java-compile-errors` | javac errors in two independent modules (main and test code) | `maven-output/javac-errors.log` |
| `kotlin-compile-errors` | kotlinc errors (Kotlin 2.x; 1.9 with `-Dkotlin.version=1.9.25`) | `maven-output/kotlinc-errors.log`, `kotlinc-1.9-errors.log`, `kotlinc-crash.log` (Kotlin 1.9 on JDK 25) |
| `missing-package` | an import from a package that doesn't exist, and a symbol from it | `maven-output/javac-missing-package.log` |
| `missing-dependency` | a dependency version that doesn't exist | `maven-output/dependency-resolution.log` |
| `missing-parent` | a parent POM version that doesn't exist | `maven-output/missing-parent.log` |
| `failing-tests` | a surefire test in `lib` and a failsafe test in `app` | `maven-output/test-failures.log`, and the XML reports under `*/target/*-reports/` |

The effective POMs come from `help:effective-pom` (maven-help-plugin 3.5.2):

| Captured output | From |
|---|---|
| `maven-output/effective-pom-spring-boot.xml` | the head of `cli`'s `spring-boot-upgrade` fixture (Spring Boot parent 3.4.1) |
| `maven-output/effective-pom-multi-module.xml` | `failing-tests` |

They are cut down: only the first two entries of each `dependencyManagement` and `pluginManagement` are kept, and
`properties`, `repositories`, `pluginRepositories`, and `reporting` are removed.

## Source fixtures

`language-facts/` holds one Java and one Kotlin file with each construct the guardrails look for (methods, test methods,
assertions, skips, suppressions, stubs). They are only parsed, never compiled.

## How the output was captured

Maven 3.9.12, JDK 25, English locale, run on a copy of the fixture:

```
LC_ALL=en_US.UTF-8 MAVEN_OPTS="-Duser.language=en" mvn -B -fae clean test-compile   # or: verify
```

Then cleaned up: the copy's path replaced with `/work/repo`, download lines and local paths removed, long stack traces
cut to three frames, and `<property>` lines removed from the XML reports.

## Gradle fixtures

Small Gradle builds for the Gradle adapter. They have no `gradlew` and no wrapper JAR: the tests copy the tool's own
wrapper next to the fixture's `gradle/wrapper/gradle-wrapper.properties`, which names the Gradle version.

| Fixture | Build | Breaks with | Captured output |
|---|---|---|---|
| `gradle-groovy` | Groovy DSL, Gradle 8.14.2, Java toolchain 21 | javac errors in two files | `gradle-output/javac-errors.log` |
| `gradle-kotlin-catalog` | Kotlin DSL with a version catalog, Gradle 9.8.1, Kotlin 2.2 | kotlinc errors | `gradle-output/kotlinc-errors.log` |
| `gradle-multi-project` | Kotlin DSL, Gradle 9.8.1, `lib` and `app` with an `integrationTest` suite and a failing `lint` task in `check`, and an included build `conventions` | a failing test in `lib:test` and in `app:integrationTest` | `gradle-output/test-failures.log`, and the XML reports under `gradle-output/test-results/<project>/<task>/` |

The other captures come from copies of these fixtures with one change each:

| Captured output | Fixture and change |
|---|---|
| `build-script-error.log` | `gradle-kotlin-catalog`, an unknown property in `build.gradle.kts` |
| `groovy-script-error.log` | `gradle-groovy`, an unknown property on the `test` task in `build.gradle` |
| `dependency-resolution.log` | `gradle-groovy`, commons-lang3 `99.0.0` |
| `integration-test-compile-error.log` | `gradle-multi-project`, a missing method called from `GreetingIT` |
| `javac-column-zero.log` | `gradle-groovy`, a class that extends a missing class |
| `toolchain-missing.log` | `gradle-groovy`, a Java 11 toolchain (Gradle 8.14.2) |
| `toolchain-missing-8.5.log` | `gradle-groovy`, a Java 11 toolchain on Gradle 8.5 |

### How the Gradle output was captured

JDK 21 runs Gradle, English locale, lector-ai's init script, toolchains from the one JDK only:

```
JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" ./gradlew -I lector.init.gradle --console=plain \
  -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.auto-download=false \
  -Dorg.gradle.java.installations.paths=<JDK 21> lectorCompile --continue   # or: lectorTest --continue
```

`-P` instead of `-D` for Gradle 8. Then cleaned up: the copy's path replaced with `/work/repo`, the `Picked up
JAVA_TOOL_OPTIONS` lines and download progress removed, and `hostname` set to `localhost` in the XML reports.
