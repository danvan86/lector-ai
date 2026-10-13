This PR contains the following updates:

| Package | Type | Update | Change | [Age](https://docs.renovatebot.com/merge-confidence/) | [Adoption](https://docs.renovatebot.com/merge-confidence/) | [Passing](https://docs.renovatebot.com/merge-confidence/) | [Confidence](https://docs.renovatebot.com/merge-confidence/) |
|---|---|---|---|---|---|---|---|
| [gradle](https://gradle.org) ([source](https://redirect.github.com/gradle/gradle)) |  | patch | `9.8.0` → `9.8.1` | ![age](https://developer.mend.io/api/mc/badges/age/gradle-version/gradle/9.8.1?slim=true) | ![adoption](https://developer.mend.io/api/mc/badges/adoption/gradle-version/gradle/9.8.1?slim=true) | ![passing](https://developer.mend.io/api/mc/badges/compatibility/gradle-version/gradle/9.8.0/9.8.1?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/gradle-version/gradle/9.8.0/9.8.1?slim=true) |
| [gradle-plugin](https://kotlinlang.org/) ([source](https://redirect.github.com/JetBrains/kotlin)) | devDependencies | patch | `2.4.20` → `2.4.21` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.jetbrains.kotlin:kotlin-gradle-plugin/2.4.21?slim=true) | ![adoption](https://developer.mend.io/api/mc/badges/adoption/maven/org.jetbrains.kotlin:kotlin-gradle-plugin/2.4.21?slim=true) | ![passing](https://developer.mend.io/api/mc/badges/compatibility/maven/org.jetbrains.kotlin:kotlin-gradle-plugin/2.4.20/2.4.21?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.jetbrains.kotlin:kotlin-gradle-plugin/2.4.20/2.4.21?slim=true) |
| [ch.qos.logback:logback-classic](http://logback.qos.ch) ([source](https://redirect.github.com/qos-ch/logback), [changelog](https://logback.qos.ch/news.html)) | dependencies | patch | `1.6.4` → `1.6.5` | ![age](https://developer.mend.io/api/mc/badges/age/maven/ch.qos.logback:logback-classic/1.6.5?slim=true) | ![adoption](https://developer.mend.io/api/mc/badges/adoption/maven/ch.qos.logback:logback-classic/1.6.5?slim=true) | ![passing](https://developer.mend.io/api/mc/badges/compatibility/maven/ch.qos.logback:logback-classic/1.6.4/1.6.5?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/ch.qos.logback:logback-classic/1.6.4/1.6.5?slim=true) |

---

### Release Notes

<details>
<summary>gradle/gradle (gradle)</summary>

### [`v9.8.1`](https://redirect.github.com/gradle/gradle/releases/tag/v9.8.1): 9.8.1

[Compare Source](https://redirect.github.com/gradle/gradle/compare/v9.8.0...v9.8.1)

This is a patch release for Gradle 9.8.0. We recommend using 9.8.1 instead of 9.8.0.

This release addresses three high rated vulnerabilities:

- [Unauthenticated worker-to-daemon channel deserializes untrusted Java objects](https://redirect.github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8)
- [Deserialization of untrusted Java objects before authentication in the client - daemon communication](https://redirect.github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64)
- [Failure to disable repositories failing to establish an SSL connection can expose builds to malicious artifacts](https://redirect.github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5)

The following issues were resolved:

- \[Gradle 9.8.0 complains again about invalid Toolchains on Debian / Ubuntu packaged systems]- ([#&#8203;39358](https://redirect.github.com/gradle/gradle/issues/39358))
- [Gradle 9.8.0 removes root java.util.logging handlers installed by a custom LogManager (breaks Quarkus test log capture)](https://redirect.github.com/gradle/gradle/issues/39359)
- [Dependency resolution regression](https://redirect.github.com/gradle/gradle/issues/39365)

[Read the Release Notes](https://docs.gradle.org/9.8.1/release-notes.html)

We would like to thank the following community members for their contributions to this release of Gradle:
[Aman Gautam](https://redirect.github.com/Gautam-aman),
[Björn Kautler](https://redirect.github.com/Vampire),
[Eng Zer Jun](https://redirect.github.com/Juneezee),
[Hashim Khan](https://redirect.github.com/Hashim1999164),
[Julian Krannich](https://redirect.github.com/jkrannich),
[KBS](https://redirect.github.com/youdie006),
[Labh R Jethe](https://redirect.github.com/itsCodeTide),
[Mark Dodgson](https://redirect.github.com/doddi),
[Maxim](https://redirect.github.com/kroune),
[monkey](https://redirect.github.com/Develop-KIM),
[nataphon-ktsystems](https://redirect.github.com/nataphon-ktsystems),
[Paul King](https://redirect.github.com/paulk-asert),
[Qiu Tian](https://redirect.github.com/qiu-tiandev),
[rg_sandesh](https://redirect.github.com/sandeshgorde),
[Roberto Perez Alcolea](https://redirect.github.com/rpalcolea),
[Sean](https://redirect.github.com/seanxuu),
[Zongle Wang](https://redirect.github.com/Goooler).

#### Upgrade instructions

Switch your build to use Gradle 9.8.1 by updating your wrapper:

```
./gradlew :wrapper --gradle-version=9.8.1 && ./gradlew :wrapper
```

See the Gradle [9.x upgrade guide](https://docs.gradle.org/9.8.1/userguide/upgrading_version_9.html) to learn about deprecations, breaking changes and other considerations when upgrading.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](https://docs.gradle.org/9.8.1/userguide/compatibility.html).

#### Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://redirect.github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://redirect.github.com/gradle).

</details>

<details>
<summary>JetBrains/kotlin (gradle-plugin)</summary>

### [`v2.4.21`](https://redirect.github.com/JetBrains/kotlin/blob/HEAD/ChangeLog.md#2421)

##### Backend. Wasm

- [`KT-89267`](https://youtrack.jetbrains.com/issue/KT-89267) K/Wasm: calling a (T) -> R lambda with an argument that isn't a T traps instead of throwing ClassCastException

##### JVM. Reflection

- [`KT-89280`](https://youtrack.jetbrains.com/issue/KT-89280) NoSuchElementException in argumentsMakeSenseOnlyForMutableContainer for Java non-static inner class of generic outer class
- [`KT-80710`](https://youtrack.jetbrains.com/issue/KT-80710) Reflection: change KCallable representation to avoid dependency on K1

##### JavaScript

- [`KT-89449`](https://youtrack.jetbrains.com/issue/KT-89449) Kotlin/JS: Member names no longer minified in production
- [`KT-89363`](https://youtrack.jetbrains.com/issue/KT-89363) KJS. Regular class with companion can't extend external class

##### Tools. Compiler plugins. Serialization

- [`KT-89275`](https://youtrack.jetbrains.com/issue/KT-89275) Kotlin/Wasm: Invalid Wasm is emitted for a `@Serializable` generic class with a class upper bound

##### Tools. Gradle. Native

- [`KT-89285`](https://youtrack.jetbrains.com/issue/KT-89285) SwiftPM import results in flooded log
- [`KT-86251`](https://youtrack.jetbrains.com/issue/KT-86251) K/N: Flaky linkDebugTestLinuxX64 failure with "truncated or malformed archive" on libstdlib-cache.a

##### Tools. Scripts

- [`KT-89247`](https://youtrack.jetbrains.com/issue/KT-89247) Kotlin Script: NoClassDefFoundError on 2nd execution with transitive dependency from imported script
- [`KT-89220`](https://youtrack.jetbrains.com/issue/KT-89220) JSR-223: generic Java bindings break all subsequent `eval()` calls
- [`KT-88458`](https://youtrack.jetbrains.com/issue/KT-88458) Audit closeable resource usage
- [`KT-88453`](https://youtrack.jetbrains.com/issue/KT-88453) `VirtualFileScriptSource.text` leaks an unclosed file stream — script source file handle held until GC, blocks file deletion on Windows

</details>

---

### Configuration

📅 **Schedule**: (in timezone Europe/Berlin)

- Branch creation
  - "after 1am"
- Automerge
  - At any time (no schedule defined)

🚦 **Automerge**: Disabled by config. Please merge this manually once you are satisfied.

♻ **Rebasing**: Whenever PR becomes conflicted, or you tick the rebase/retry checkbox.

👻 **Immortal**: This PR will be recreated if closed unmerged. Get [config help](https://redirect.github.com/renovatebot/renovate/discussions) if that's undesired.

---

 - [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box

---

This PR was generated by [Mend Renovate](https://mend.io/renovate/). View the [repository job log](https://developer.mend.io/github/kxmpxtxnt/solembum).
<!--renovate-debug:eyJjcmVhdGVkSW5WZXIiOiI0NC4xMjUuMSIsInVwZGF0ZWRJblZlciI6IjQ0LjEzNC4xIiwidGFyZ2V0QnJhbmNoIjoibWFpbiIsImxhYmVscyI6W119-->

