# Progress – M10 API diff as grounding

Spec: [`spec.md`](spec.md) · Tests: [`tests.md`](tests.md)

Status: ⬜ open · 🟨 in progress · ✅ done

| # | Step | Status |
|---|---|---|
| 1 | Port `apiChanges` with a default, `FixLoop` puts all distinct API changes first, prompt heading `API` (T-D, T-E) | ✅ |
| 2 | API model from class files with the ClassFile API | ✅ |
| 3 | API diff (seven categories) and its rendering, Maven with old JARs in the local repository (T-A) | ✅ |
| 4 | Maven: fetch missing old JARs once per run with `maven-dependency-plugin` (T-B, real Maven) | ✅ |
| 5 | Gradle: old JARs from any model read in the run, kept apart from `isBuildFile` (T-C) | ✅ |
| 6 | Proof cases: red at `cf183a8`, green with M10, in the benchmark corpus | ✅ |
| 7 | Quality gates verified | ⬜ |
| 8 | Docs in sync | ✅ |

## Judgment calls

- **When the old version is looked up (R20).** Only when the errors themselves refer to the group: the classes the
  messages name, the imports tied to a missing symbol or package, or the classes named like a missing symbol, resolved
  to a class or a package of the new JARs. A plain import of an erroring file doesn't count (T-B5). Once looked up,
  every import of the erroring files is diffed, as spec section 3 says.
- **Fetch order (R21).** The change's own artifact first; an artifact Maven can't resolve at the old version (PDFBox 3's
  new `pdfbox-io`) skips only itself; a timeout or a Maven that can't run ends the lookup.
- **Fetch target.** `dependency:get -Dmaven.repo.local=<the adapter's local repository>`, so the fetched JAR lands where
  grounding reads.
- **`ground` never throws (R8).** A JAR that can't be read or a javap that fails to run or times out gives no full API.
- **Build conventions.** `jvmDefault = NO_COMPATIBILITY` (no `DefaultImpls` copy that coverage counts but nothing
  calls); detekt `allowedFunctionsPerInterface: 12` for the port.
- **T-B1 changed with the user's consent:** its snapshot is taken after the adapter's own settings file exists.
- **Moved fields match by name alone.** Fields have no parameters, so a static field moves to the one other class with a
  static field of that name, whatever its type (spec R4 doesn't compare the return type). A search over 44 real release
  pairs showed false moves like `TypeAdapters.NUMBER → JsonToken.NUMBER` (Gson) and `Modifier.ABSTRACT →
  GeneratedJavaParserConstants.ABSTRACT` (JavaParser). Left as specified; worth a look in review or M11.

## Proof on real cases (AC27, AC28, AC28a)

Chosen from a search with the new `ApiDiff` over 44 real release pairs of libraries without a bundled recipe. Fixture
projects written for M10, heads in `danvan86/lector-benchmark-corpus` (branches `case/pdfbox-3`, `case/snakeyaml-2`),
both in `corpus.json`. JDK 25 (Temurin 25.0.1), `maven.compiler.release` 17, Maven 3.9.12, qwen2.5-coder:7b in a local
Ollama. Each run on a fresh clone of the pushed branch:

```
MAVEN_OPTS="-Duser.language=en -Duser.country=US" \
JAVA_OPTS="-Duser.language=en -Dorg.slf4j.simpleLogger.log.gmbh.vanella.lector.llm=debug" \
lector heal . --package <package> --from <from> --to <to> --base HEAD~1 \
  --ollama-url http://localhost:11434 --model qwen2.5-coder:7b
```

`lector` is `cli/build/install/lector/bin/lector`, built once at `cf183a8` (in a scratch worktree) and once on this branch.

### PDFBox 2.0.32 → 3.0.3 (moved member)

Head error: `PdfPages.java:14:46: cannot find symbol / symbol: method load(java.io.File) / location: class
org.apache.pdfbox.pdmodel.PDDocument`. Recipe: `Reason: no recipe matches`.

| | `cf183a8` | M10 |
|---|---|---|
| Exit | 1 | 0 |
| Fix loop | 2 attempts, 0 accepted | 1 attempt, 1 accepted |
| Edit | `PDDocument.load(file)` → `new PDDocument(file)` (twice) | `PDDocument.load(file)` → `PDFParser.load(file)`, plus the import |

AC28a: the grounding sent at `cf183a8` is `javap -public of org.apache.pdfbox:pdfbox 3.0.3:` with one class,
`public class org.apache.pdfbox.pdmodel.PDDocument implements java.io.Closeable { … }`. Neither `PDFParser` nor
`Loader` is in it.

The API changes sent with M10 (shortened):

```
API changes of org.apache.pdfbox:pdfbox from 2.0.32 to 3.0.3

Changed signatures:
- org.apache.pdfbox.pdmodel.PDDocument: (), (org.apache.pdfbox.cos.COSDocument), … → (), (org.apache.pdfbox.cos.COSDocument), …

Moved members:
- org.apache.pdfbox.pdmodel.PDDocument.load(java.io.File) → org.apache.pdfbox.pdfparser.PDFParser.load(java.io.File)
- org.apache.pdfbox.pdmodel.PDDocument.load(java.io.File, java.lang.String) → org.apache.pdfbox.pdfparser.PDFParser.load(java.io.File, java.lang.String)

Removed members:
- org.apache.pdfbox.pdmodel.PDDocument.addSignatureField(…)
- org.apache.pdfbox.pdmodel.PDDocument.load(byte[])
…
- … and 2 more
```

### SnakeYAML 1.33 → 2.3 (changed signature)

Head error: `ConfigLoader.java:[13,54] incompatible types: java.lang.Class<com.example.Config> cannot be converted to
org.yaml.snakeyaml.LoaderOptions`. Recipe: `Reason: no recipe matches`.

| | `cf183a8` | M10 |
|---|---|---|
| Exit | 1 | 0 |
| Fix loop | 2 attempts, 0 accepted | 1 attempt, 1 accepted |
| Edit | `new Yaml(new Constructor(Config.class))` → `new Yaml(new LoaderOptions())` (twice, once without the import) | → `new Yaml(new Constructor(Config.class, new LoaderOptions()))`, plus the import |

AC28a (as amended, R19): the grounding sent at `cf183a8` is the full API of `LoaderOptions`, `Yaml`, and
`Constructor`, which lists `Constructor(java.lang.Class<? extends java.lang.Object>, org.yaml.snakeyaml.LoaderOptions)`
among its constructors. Nothing in it says that `Constructor(Class)` is gone. The API changes sent with M10:

```
API changes of org.yaml:snakeyaml from 1.33 to 2.3

Changed signatures:
- org.yaml.snakeyaml.constructor.Constructor: (), (java.lang.Class), (java.lang.Class, org.yaml.snakeyaml.LoaderOptions), (java.lang.String), … → (java.lang.Class, org.yaml.snakeyaml.LoaderOptions), (java.lang.String, org.yaml.snakeyaml.LoaderOptions), (org.yaml.snakeyaml.LoaderOptions), …
```

One run each, at temperature 0.0 for the first attempt; a benchmark run on GitHub was not made (optional).

## Open

- `docs/architecture/` (fix-loop, gradle-adapter, recipe-migration, benchmark, README) is left to architecture-docs,
  after review-spec, as the workflow says.
