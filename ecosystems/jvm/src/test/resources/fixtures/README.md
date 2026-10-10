# Fixture repos

Small Maven projects for the parser tests. Each one breaks in one way.

| Fixture | Breaks with | Captured output |
|---|---|---|
| `java-compile-errors` | javac errors in two independent modules (main and test code) | `maven-output/javac-errors.log` |
| `kotlin-compile-errors` | kotlinc errors (Kotlin 2.x; 1.9 with `-Dkotlin.version=1.9.25`) | `maven-output/kotlinc-errors.log`, `kotlinc-1.9-errors.log`, `kotlinc-crash.log` (Kotlin 1.9 on JDK 25) |
| `missing-dependency` | a dependency version that doesn't exist | `maven-output/dependency-resolution.log` |
| `missing-parent` | a parent POM version that doesn't exist | `maven-output/missing-parent.log` |
| `failing-tests` | a surefire test in `lib` and a failsafe test in `app` | `maven-output/test-failures.log`, and the XML reports under `*/target/*-reports/` |

## How the output was captured

Maven 3.9.12, JDK 25, English locale, run on a copy of the fixture:

```
LC_ALL=en_US.UTF-8 MAVEN_OPTS="-Duser.language=en" mvn -B -fae clean test-compile   # or: verify
```

Then cleaned up: the copy's path replaced with `/work/repo`, download lines and local paths removed, long stack traces
cut to three frames, and `<property>` lines removed from the XML reports.
