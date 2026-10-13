Bumps [org.springframework.boot](https://github.com/spring-projects/spring-boot) from 4.1.0 to 4.1.1.
<details>
<summary>Release notes</summary>
<p><em>Sourced from <a href="https://github.com/spring-projects/spring-boot/releases">org.springframework.boot's releases</a>.</em></p>
<blockquote>
<h2>v4.1.1</h2>
<h2>:warning: Attention Required</h2>
<ul>
<li>Spring Boot's Gradle plugin no longer automatically configures gRPC when the Protobuf plugin is applied. This behavior caused problems for those using Protobuf without gRPC. To opt in to the configuration of gRPC, configure the <code>protobuf</code> extension with the <code>grpc</code> plugin using an empty block. The Spring Boot Gradle plugin will then automatically configure the use of <code>protoc-gen-grpc-java</code> as before. <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50822">#50822</a></li>
</ul>
<h2>:lady_beetle: Bug Fixes</h2>
<ul>
<li>Kafka consumer-specific security protocol is not taken into account <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51369">#51369</a></li>
<li>Structured logging: a failed JSON encode corrupts the next log event written on the same thread <a href="https://redirect.github.com/spring-projects/spring-boot/pull/51156">#51156</a></li>
<li>Micrometer registries pin the application context <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51135">#51135</a></li>
<li>Temporary file is not deleted when ExportedImageTar construction fails <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51132">#51132</a></li>
<li>Metadata annotation processor ignores getter-level <code>@NestedConfigurationProperty</code> for records <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51098">#51098</a></li>
<li>spring-boot-h2-console pulls servlet-api as transitive dependency <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51095">#51095</a></li>
<li>PropertiesLauncher does not log nested archive paths <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51089">#51089</a></li>
<li>Methods that return the result of Map#remove are not declared with a <code>@Nullable</code> return type <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51087">#51087</a></li>
<li>NativeImageResourceProvider flattens Flyway migration paths in subdirectories <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50964">#50964</a></li>
<li>Fix ordering of Kotlinx Serialization CodecCustomizer <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50961">#50961</a></li>
<li>JarFile is not closed when finding main class from archive <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50959">#50959</a></li>
<li>Application-managed JUL bridge handler should only be removed if installed <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50950">#50950</a></li>
<li>CloudFoundry reactive auto-configuration should not require a WebClient.Builder bean to be defined <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50944">#50944</a></li>
<li>Context refresh fails on reactive Cloud Foundry when using Actuator without spring-boot-health <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50942">#50942</a></li>
<li>Resources are not cleaned up when resolving an image that is not yet present in the builder <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50941">#50941</a></li>
<li>GraphQlWebMvcAutoConfiguration should apply customizers in order <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50914">#50914</a></li>
<li>Auto-configured RedisMessageListenerContainer does not use virtual threads when spring.threads.virtual.enabled is true <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50884">#50884</a></li>
<li>Context refresh fails when using Actuator on Jersey without spring-boot-health <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50872">#50872</a></li>
<li>Context refresh fails on Cloud Foundry when using Actuator without spring-boot-health <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50871">#50871</a></li>
<li>IllegalStateException when binding properties to a <code>@Validated</code> class that contains a map whose value type is a wildcard <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50856">#50856</a></li>
<li>High number of connections due to Mongo health indicator <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50852">#50852</a></li>
<li>Inconsistent handling of empty string values of spring.security.oauth2.resourceserver.jwt issuer-uri and jwk-set-uri <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50849">#50849</a></li>
<li>Return type nullability of ApplicationContextAssert's getBean methods does not indicate that bean may be null <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50845">#50845</a></li>
<li>PropertiesWebClientHttpServiceGroupConfigurer has highest precedence, preventing other configurers from being ordered ahead of it <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50843">#50843</a></li>
<li>Exposing gRPC test server port should backoff if gRPC is not present <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50825">#50825</a></li>
<li>JpaBaseConfiguration#entityManagerConfiguration can cause a dependency loop on beans declaring AsyncTaskExecutor <a href="https://redirect.github.com/spring-projects/spring-boot/pull/50801">#50801</a></li>
<li>spring.grpc.server.health.include-overall-health is not taken into account <a href="https://redirect.github.com/spring-projects/spring-boot/pull/50799">#50799</a></li>
<li>Setting 'server.servlet.session.cookie.partitioned' to false still emits the 'Partitioned' cookie attribute <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50790">#50790</a></li>
<li>Managed version of Prometheus Client is not aligned with Micrometer's micrometer-registry-prometheus <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50780">#50780</a></li>
<li>Map properties bound from empty strings fail with ConverterNotFoundException <a href="https://redirect.github.com/spring-projects/spring-boot/pull/50773">#50773</a></li>
<li>Protobuf Common Protos should not be a managed dependency <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50772">#50772</a></li>
<li>An application that depends on spring-boot-security-oauth2-resource-server may fail to start with a ClassNotFoundException when Reactor is on the classpath but WebFlux is not <a href="https://redirect.github.com/spring-projects/spring-boot/issues/50764">#50764</a></li>
<li>W3CHeaderParser's decoding is not compliant with RFC 3986 <a href="https://redirect.github.com/spring-projects/spring-boot/pull/50650">#50650</a></li>
</ul>
<h2>:notebook_with_decorative_cover: Documentation</h2>
<ul>
<li>Description of spring.graphql.websocket.connection-init-timeout does not render correctly in the reference guide <a href="https://redirect.github.com/spring-projects/spring-boot/pull/51348">#51348</a></li>
<li>spring.profiles.group should have a 'spring-profile-name' hint provider <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51284">#51284</a></li>
<li>Remove reference to removed InfluxDB auto-configuration <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51176">#51176</a></li>
<li>Use JacksonJsonSerde in Kafka Streams documentation <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51161">#51161</a></li>
<li>Document alternatives to HttpMessageConverters <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51129">#51129</a></li>
<li>Fix stale type reference for OTLP logging transport metadata <a href="https://redirect.github.com/spring-projects/spring-boot/pull/51119">#51119</a></li>
<li>Metadata for spring.test.mockmvc.htmlunit.url declares the wrong type <a href="https://redirect.github.com/spring-projects/spring-boot/issues/51115">#51115</a></li>
</ul>
<!-- raw HTML omitted -->
</blockquote>
<p>... (truncated)</p>
</details>
<details>
<summary>Commits</summary>
<ul>
<li><a href="https://github.com/spring-projects/spring-boot/commit/6fdf67ea1552691e932604d4bf67a5e08ff0b0ea"><code>6fdf67e</code></a> Release 4.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/fde599b28b40932e4ea6194399d89245923a01e7"><code>fde599b</code></a> Upgrade to Spring Pulsar 2.0.7</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/9daa58f7d98b82bf16febcb91943ce267c220a50"><code>9daa58f</code></a> Upgrade to Spring HATEOAS 3.1.2</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/353993ebc1d7b1ceccbb981b0439a42ba122a816"><code>353993e</code></a> Upgrade to Spring Data Bom 2026.0.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/24ba596bc4fa000614834016452435c834fdeda2"><code>24ba596</code></a> Upgrade to Spring Session 4.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/5cb5c294d1f4780e78d010a964049e47c074e4d6"><code>5cb5c29</code></a> Upgrade to Spring Security 7.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/4adc8eb130c4e0c688ed85316f385f4e04d47679"><code>4adc8eb</code></a> Upgrade to Spring LDAP 4.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/4d9c19cbc0589f57a7265052b507bf4b961082ac"><code>4d9c19c</code></a> Upgrade to Spring Kafka 4.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/f30f6120c4f6f8f873ac56cba478ae1f011c276c"><code>f30f612</code></a> Upgrade to Spring Integration 7.1.1</li>
<li><a href="https://github.com/spring-projects/spring-boot/commit/b930283d97e92eb24da1de3721cdd41625266dea"><code>b930283</code></a> Upgrade to Spring gRPC 1.1.1</li>
<li>Additional commits viewable in <a href="https://github.com/spring-projects/spring-boot/compare/v4.1.0...v4.1.1">compare view</a></li>
</ul>
</details>
<br />


[![Dependabot compatibility score](https://dependabot-badges.githubapp.com/badges/compatibility_score?dependency-name=org.springframework.boot&package-manager=gradle&previous-version=4.1.0&new-version=4.1.1)](https://docs.github.com/en/github/managing-security-vulnerabilities/about-dependabot-security-updates#about-compatibility-scores)

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
- `@dependabot ignore this major version` will close this PR and stop Dependabot creating any more for this major version (unless you reopen the PR or upgrade to it yourself)
- `@dependabot ignore this minor version` will close this PR and stop Dependabot creating any more for this minor version (unless you reopen the PR or upgrade to it yourself)
- `@dependabot ignore this dependency` will close this PR and stop Dependabot creating any more for this dependency (unless you reopen the PR or upgrade to it yourself)


</details>