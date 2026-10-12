Bumps the maven group with 4 updates: commons-io:commons-io, [com.google.guava:guava](https://github.com/google/guava), [com.squareup.okio:okio-jvm](https://github.com/lysine-dev/okio) and io.swagger.core.v3:swagger-annotations.

Updates `commons-io:commons-io` from 2.21.0 to 2.22.0

Updates `com.google.guava:guava` from 33.4.8-jre to 33.7.1-jre
<details>
<summary>Release notes</summary>
<p><em>Sourced from <a href="https://github.com/google/guava/releases">com.google.guava:guava's releases</a>.</em></p>
<blockquote>
<h2>33.7.1</h2>
<h3>Maven</h3>
<pre lang="xml"><code>&lt;dependency&gt;
  &lt;groupId&gt;com.google.guava&lt;/groupId&gt;
  &lt;artifactId&gt;guava&lt;/artifactId&gt;
  &lt;version&gt;33.7.1-jre&lt;/version&gt;
  &lt;!-- or, for Android: --&gt;
  &lt;version&gt;33.7.1-android&lt;/version&gt;
&lt;/dependency&gt;
</code></pre>
<h3>Jar files</h3>
<ul>
<li><a href="https://repo1.maven.org/maven2/com/google/guava/guava/33.7.1-jre/guava-33.7.1-jre.jar">33.7.1-jre.jar</a></li>
<li><a href="https://repo1.maven.org/maven2/com/google/guava/guava/33.7.1-android/guava-33.7.1-android.jar">33.7.1-android.jar</a></li>
</ul>
<p>Guava requires <a href="https://github.com/google/guava/wiki/UseGuavaInYourBuild#what-about-guavas-own-dependencies">one runtime dependency</a>, which you can download here:</p>
<ul>
<li><a href="https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.3/failureaccess-1.0.3.jar">failureaccess-1.0.3.jar</a></li>
</ul>
<h3>Javadoc</h3>
<ul>
<li><a href="https://guava.dev/releases/33.7.1-jre/api/docs/">33.7.1-jre</a></li>
<li><a href="https://guava.dev/releases/33.7.1-android/api/docs/">33.7.1-android</a></li>
</ul>
<h3>JDiff</h3>
<ul>
<li><a href="https://guava.dev/releases/33.7.1-jre/api/diffs/">33.7.1-jre vs. 33.7.0-jre</a></li>
<li><a href="https://guava.dev/releases/33.7.1-android/api/diffs/">33.7.1-android vs. 33.7.0-android</a></li>
<li><a href="https://guava.dev/releases/33.7.1-android/api/androiddiffs/">33.7.1-android vs. 33.7.1-jre</a></li>
</ul>
<h3>Changelog</h3>
<p>Guava 33.7.1 removes the <code>Multi-Release</code> line from our jar manifest, fixing an issue under Java 9 and 10 that was introduced to <code>guava-jre</code> in <a href="https://github.com/google/guava/releases/tag/v33.7.0">version 33.7.0</a>. Sorry for the trouble.</p>
<h2>33.7.0</h2>
<h3>Newly introduced problem for Java 9 and Java 10 only</h3>
<p>Guava 33.7.0 includes a <code>Multi-Release</code> line in its jar manifest, even though it is no longer a multi-release jar. This causes <a href="https://redirect.github.com/google/guava/issues/8614">some problems with tools from Java 9 and Java 10</a>. The issue is fixed in <a href="https://github.com/google/guava/releases/tag/v33.7.1">version 33.7.1</a>. Sorry for the trouble.</p>
<h3>Maven</h3>
<pre lang="xml"><code>&lt;dependency&gt;
  &lt;groupId&gt;com.google.guava&lt;/groupId&gt;
  &lt;artifactId&gt;guava&lt;/artifactId&gt;
  &lt;version&gt;33.7.0-jre&lt;/version&gt;
  &lt;!-- or, for Android: --&gt;
&lt;/tr&gt;&lt;/table&gt; 
</code></pre>
</blockquote>
<p>... (truncated)</p>
</details>
<details>
<summary>Commits</summary>
<ul>
<li>See full diff in <a href="https://github.com/google/guava/commits">compare view</a></li>
</ul>
</details>
<br />

Updates `com.squareup.okio:okio-jvm` from 3.16.4 to 3.18.2
<details>
<summary>Changelog</summary>
<p><em>Sourced from <a href="https://github.com/lysine-dev/okio/blob/main/CHANGELOG.md">com.squareup.okio:okio-jvm's changelog</a>.</em></p>
<blockquote>
<h2>Version 3.18.2</h2>
<p><em>2026-09-04</em></p>
<ul>
<li>Fix: Don't change behavior of base64-encoding. We introduced a bug in Okio 3.18.1 that caused
base64 strings to be encoded without padding, even though the previous and expected behavior is
to encode such strings with padding (<code>=</code> characters). The bug impacts callers who compiled
against Okio releases <code>&lt; 3.18.0</code>, and executed on Okio releases <code>&gt;= 3.18</code>.</li>
</ul>
<h2>Version 3.18.1</h2>
<p><em>2026-07-28</em></p>
<ul>
<li>Fix: Restore binary-compatibility with Okio 3.17.x for Kotlin/Native users. When we introduced
the new <code>base64()</code> function, we inadvertently changed the binary signature! Ugh! This is now
fixed and we've automated binary-compatibility checking for Kotlin/Native going forward.</li>
</ul>
<h2>Version 3.18.0</h2>
<p><em>2026-07-21</em></p>
<ul>
<li>Fix: Use wide character APIs to better support of non-UTF-8 filesystems on Windows.</li>
<li>Fix: Don't crash in <code>AssetFileSystem.exists()</code> when the underlying storage throws a
<code>FileNotFoundException</code>.</li>
<li>Fix: Load WASI paths relative to their <code>preopen</code>. The platform behavior recently changed in
NodeJS, causing our <code>WasiFileSystem</code> to be unable to access files!</li>
<li>New: Optionally ignore whitespace when decoding hexadecimal.</li>
<li>New: Optionally omit padding when encoding Base64.</li>
<li>New: <code>BufferedSource.readUInt()</code>, <code>BufferedSink.writeUInt()</code>, and similar functions for <code>UByte</code>,
<code>UShort</code> and <code>ULong</code>. Also add support for unsigned and little-endian.</li>
<li>New: <code>BufferedSink.utf8Appendable()</code>. Use this to adapt an Okio sink to an <code>Appendable</code>.</li>
<li>New: <code>Source.limit()</code> returns a wrapped source with a strict limit on how many bytes are
returned.</li>
<li>New: <code>ByteString.equals(other, constantTime)</code> for subtle defense against timing attacks.</li>
</ul>
<h2>Version 3.17.0</h2>
<p><em>2026-03-11</em></p>
<ul>
<li>
<p>New: Adjust down the Kotlin stdlib dependency to [Kotlin 2.1.21][kotlin_2_1_21]. Okio is built
with an up-to-date Kotlin compiler (2.2.21), but depends on an older kotlin-stdlib. We're doing
this so you can update Okio and Kotlin independently.</p>
</li>
<li>
<p>Fix: Return the correct timestamp in <code>FileMetadata.createdAtMillis</code> on Kotlin/Native on UNIX
platforms. We were incorrectly using the POSIX <code>ctime</code> (<em>change</em> time) instead of the
<code>birthtime</code>. With this fix Okio now prefers <code>statx()</code> over <code>stat()</code> on native platforms. This
API first appeared in Linux in 4.11 (2017) and Android in API 30 (2020).</p>
</li>
</ul>
</blockquote>
</details>
<details>
<summary>Commits</summary>
<ul>
<li><a href="https://github.com/lysine-dev/okio/commit/9403a094ac98d5a6ba8bad4ec3301399796fad67"><code>9403a09</code></a> Prepare for release 3.18.2.</li>
<li><a href="https://github.com/lysine-dev/okio/commit/5a1c1538b13cf55897d33c05b2fe2e5e2cdeef53"><code>5a1c153</code></a> Fix an unintended behavior change with base64 padding (<a href="https://redirect.github.com/lysine-dev/okio/issues/1869">#1869</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/9f564fd76310120e35b4ffb94b62d6ab2a26207d"><code>9f564fd</code></a> Prepare for release 3.18.1.</li>
<li><a href="https://github.com/lysine-dev/okio/commit/531cf07b8ebe140d85448efd6ddff062c13ef317"><code>531cf07</code></a> Hide some accidentally public symbols (<a href="https://redirect.github.com/lysine-dev/okio/issues/1839">#1839</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/e7ef43d4d5bd421a51daee9d81ed742a702d0675"><code>e7ef43d</code></a> Update GitHub Actions for new secrets and branch (<a href="https://redirect.github.com/lysine-dev/okio/issues/1840">#1840</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/a6730de231a0747359e220eb4b5546a829510d3d"><code>a6730de</code></a> Start enforcing binary compatibility for Kotlin/Native (<a href="https://redirect.github.com/lysine-dev/okio/issues/1838">#1838</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/e75a7e9e7fe144ea74baf3e4acd4f2df0a0d0cc0"><code>e75a7e9</code></a> Update dependency com.android.tools.build:gradle to v9.3.1 (<a href="https://redirect.github.com/lysine-dev/okio/issues/1836">#1836</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/9d21d5cba334fce36d3316f421d7adcd3fb7ef93"><code>9d21d5c</code></a> Fix interchanged docs links for 1.x and 2.x API (<a href="https://redirect.github.com/lysine-dev/okio/issues/1835">#1835</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/bff1dffe5f90e9546b494d4693291a8f72cb07b4"><code>bff1dff</code></a> Track new docs locations from Dokka v2 (<a href="https://redirect.github.com/lysine-dev/okio/issues/1833">#1833</a>)</li>
<li><a href="https://github.com/lysine-dev/okio/commit/3b16d9b9dfe6b27ae198a00b7b6d45dc162cddcf"><code>3b16d9b</code></a> Grant write permission to the publish-website action (<a href="https://redirect.github.com/lysine-dev/okio/issues/1832">#1832</a>)</li>
<li>Additional commits viewable in <a href="https://github.com/lysine-dev/okio/compare/parent-3.16.4...parent-3.18.2">compare view</a></li>
</ul>
</details>
<br />

Updates `io.swagger.core.v3:swagger-annotations` from 2.2.50 to 2.2.55


Dependabot will resolve any conflicts with this PR as long as you don't alter it yourself. You can also trigger a rebase manually by commenting `@dependabot rebase`.

[//]: # (dependabot-automerge-start)
[//]: # (dependabot-automerge-end)

---

<details>
<summary>Dependabot commands and options</summary>
<br />

You can trigger Dependabot actions by commenting on this PR:
- `@dependabot rebase` will rebase this PR
- `@dependabot recreate` will recreate this PR, overwriting any edits that have been made to it
- `@dependabot show <dependency name> ignore conditions` will show all of the ignore conditions of the specified dependency
- `@dependabot ignore <dependency name> major version` will close this group update PR and stop Dependabot creating any more for the specific dependency's major version (unless you unignore this specific dependency's major version or upgrade to it yourself)
- `@dependabot ignore <dependency name> minor version` will close this group update PR and stop Dependabot creating any more for the specific dependency's minor version (unless you unignore this specific dependency's minor version or upgrade to it yourself)
- `@dependabot ignore <dependency name>` will close this group update PR and stop Dependabot creating any more for the specific dependency (unless you unignore this specific dependency or upgrade to it yourself)
- `@dependabot unignore <dependency name>` will remove all of the ignore conditions of the specified dependency
- `@dependabot unignore <dependency name> <ignore condition>` will remove the ignore condition of the specified dependency and ignore conditions


</details>