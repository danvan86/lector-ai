# Licensing

What a lector-ai release distributes, under which licenses, and the state of the check of the Moderne Source Available
License (MSAL). Concept section 9 has the distribution model this check is about. This is a record for the release, not
legal advice.

**MSAL check:** open

The release workflow refuses a version tag until the line above says `done`, together with the outcome below.

## What a release distributes

### Tool image (`ghcr.io/<owner>/lector`)

| Part | License |
|---|---|
| lector-ai (`cli`, `core`, `recipes`, `llm`, `sources`, `git`, `ecosystems:shared`, `ecosystems:jvm`, `ecosystems:typescript`) | Apache License 2.0 |
| Kotlin standard library, JetBrains annotations, kotlinx.serialization, Clikt, Mordant | Apache License 2.0 |
| colormath (Mordant's colors), SLF4J | MIT |
| `maven-artifact`, `plexus-utils` | Apache License 2.0 |
| OpenRewrite Gradle plugin (`org.openrewrite:plugin`, in `rewrite-gradle-plugin/`), with the Kotlin standard library and JetBrains annotations it needs | Apache License 2.0 (the plugin's POM on the Gradle Plugin Portal) |
| JNA (Clikt's terminal detection) | Apache License 2.0 or LGPL 2.1 or later |
| tree-sitter-ng with the Java, Kotlin, TypeScript, TSX, and JavaScript grammars (`io.github.bonede`) | MIT |
| SnakeYAML Engine (`org.snakeyaml:snakeyaml-engine`, pnpm's and Yarn's lockfiles) | Apache License 2.0 |
| The recipe index and its license report: recipe names, the packages and versions they cover, and their artifacts and licenses, generated from `rewrite-recipe-bom` | see the MSAL check |
| Eclipse Temurin JDKs 17, 21, 25 | GPL v2 with the Classpath Exception |
| Apache Maven 3.9 | Apache License 2.0 |
| Node.js 22 and 24 (from the official `node` images: `node`, its headers) | MIT, with the licenses of its bundled dependencies in its `LICENSE` |
| npm, bundled with each Node | Artistic License 2.0 |
| Corepack 0.36 (`docker/lector/node`) | MIT |
| pnpm 12, Yarn 4 (`@yarnpkg/cli-dist`), and Yarn 1, pre-cached by Corepack | MIT (pnpm), BSD-2-Clause (Yarn) |
| jest-junit 17 and its dependencies (`mkdirp`, `strip-ansi`, `ansi-regex`, `uuid`, `xml`) | Apache License 2.0 (jest-junit), MIT (its dependencies) |
| git | GPL v2 |
| Ubuntu 24.04 (the `eclipse-temurin` base) | the licenses of its packages |

No OpenRewrite JavaScript is in the image or the distribution, and neither is the Moderne CLI (M9, see below). No
OpenRewrite recipe JARs are in the image, the distribution, or a pre-filled Maven cache. The organisation's own Maven
or Gradle fetches them when a recipe runs (M2). That includes the OpenRewrite libraries the Gradle plugin loads: the
image ships only the plugin itself, about 100 KB (M7).

### Ollama image (`ghcr.io/<owner>/lector-ollama`)

| Part | License |
|---|---|
| Ollama | MIT |
| Ubuntu 24.04 (the `ollama/ollama` base) | the licenses of its packages |
| NVIDIA CUDA libraries in the `ollama/ollama` image | NVIDIA's license terms; not checked yet |
| `qwen2.5-coder:7b`: Qwen2.5-Coder-7B-Instruct weights | Apache License 2.0 |
| `qwen3-coder:30b`: Qwen3-Coder-30B-A3B-Instruct weights | Apache License 2.0 |

The model licenses are those on the models' Hugging Face pages and in the license layer of the Ollama library images.
The weights are redistributed for the first time with M5: they are baked into the image.

## MSAL check

**Question.** lector-ai is free, Apache 2.0, and self-hosted. Its recipe step runs OpenRewrite recipes, and most
recipes that migrate code (`rewrite-spring`, `rewrite-migrate-java`, `rewrite-testing-frameworks`) are under the MSAL.
Is this distribution model compatible with the MSAL under the conditions in concept section 9?

| Step | State |
|---|---|
| Written confirmation from Moderne of the model in concept section 9 | Not asked yet |
| Legal review of the confirmation and the license text | Not done |
| The recipe index shipped in the tool image: is metadata of MSAL recipes (names, the versions they cover) covered? | Part of the question to Moderne |

**OpenRewrite JavaScript (M9, October 2026).** `org.openrewrite:rewrite-javascript` (Maven Central, 8.90) and
`@openrewrite/rewrite` (npm) are under the Moderne Source Available License. Their JavaScript and TypeScript recipes run
only through the Moderne CLI or the Moderne Platform, and the public recipe catalog has no migration recipes for npm
packages. lector-ai therefore runs no JavaScript recipes: the TypeScript adapter has no recipe runner, the recipe index
has no `npm` entries, and nothing of either artifact or the Moderne CLI is shipped. The question to Moderne above is
about the JVM recipes only.

**Outcome.** None yet. Record it here: the answer from Moderne (or that none came, and when it was asked), the result of
the legal review, and any change to the distribution model it requires. Then set the status line at the top to `done`.
