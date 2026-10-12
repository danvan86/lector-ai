renovate/spring-boot
app/renovate
> ℹ️ **Note**
> 
> This PR body was truncated due to platform limits.

This PR contains the following updates:

| Package | Change | [Age](https://docs.renovatebot.com/merge-confidence/) | [Confidence](https://docs.renovatebot.com/merge-confidence/) |
|---|---|---|---|
| [org.springframework.boot:spring-boot-starter-parent](https://spring.io/projects/spring-boot) ([source](https://redirect.github.com/spring-projects/spring-boot)) | `4.0.3` → `4.1.1` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.springframework.boot:spring-boot-starter-parent/4.1.1?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.springframework.boot:spring-boot-starter-parent/4.0.3/4.1.1?slim=true) |

---

> [!WARNING]
> Some dependencies could not be looked up. Check the [Dependency Dashboard](../issues/1) for more information.

---

### Release Notes

<details>
<summary>spring-projects/spring-boot (org.springframework.boot:spring-boot-starter-parent)</summary>

### [`v4.1.1`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.1.1)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v4.1.0...v4.1.1)

#### :warning: Attention Required

- Spring Boot's Gradle plugin no longer automatically configures gRPC when the Protobuf plugin is applied. This behavior caused problems for those using Protobuf without gRPC. To opt in to the configuration of gRPC, configure the `protobuf` extension with the `grpc` plugin using an empty block. The Spring Boot Gradle plugin will then automatically configure the use of `protoc-gen-grpc-java` as before. [#&#8203;50822](https://redirect.github.com/spring-projects/spring-boot/issues/50822)

#### :lady\_beetle: Bug Fixes

- Kafka consumer-specific security protocol is not taken into account [#&#8203;51369](https://redirect.github.com/spring-projects/spring-boot/issues/51369)
- Structured logging: a failed JSON encode corrupts the next log event written on the same thread [#&#8203;51156](https://redirect.github.com/spring-projects/spring-boot/pull/51156)
- Micrometer registries pin the application context [#&#8203;51135](https://redirect.github.com/spring-projects/spring-boot/issues/51135)
- Temporary file is not deleted when ExportedImageTar construction fails [#&#8203;51132](https://redirect.github.com/spring-projects/spring-boot/issues/51132)
- Metadata annotation processor ignores getter-level `@NestedConfigurationProperty` for records [#&#8203;51098](https://redirect.github.com/spring-projects/spring-boot/issues/51098)
- spring-boot-h2-console pulls servlet-api as transitive dependency [#&#8203;51095](https://redirect.github.com/spring-projects/spring-boot/issues/51095)
- PropertiesLauncher does not log nested archive paths [#&#8203;51089](https://redirect.github.com/spring-projects/spring-boot/issues/51089)
- Methods that return the result of Map#remove are not declared with a `@Nullable` return type [#&#8203;51087](https://redirect.github.com/spring-projects/spring-boot/issues/51087)
- NativeImageResourceProvider flattens Flyway migration paths in subdirectories [#&#8203;50964](https://redirect.github.com/spring-projects/spring-boot/issues/50964)
- Fix ordering of Kotlinx Serialization CodecCustomizer [#&#8203;50961](https://redirect.github.com/spring-projects/spring-boot/issues/50961)
- JarFile is not closed when finding main class from archive [#&#8203;50959](https://redirect.github.com/spring-projects/spring-boot/issues/50959)
- Application-managed JUL bridge handler should only be removed if installed [#&#8203;50950](https://redirect.github.com/spring-projects/spring-boot/issues/50950)
- CloudFoundry reactive auto-configuration should not require a WebClient.Builder bean to be defined [#&#8203;50944](https://redirect.github.com/spring-projects/spring-boot/issues/50944)
- Context refresh fails on reactive Cloud Foundry when using Actuator without spring-boot-health [#&#8203;50942](https://redirect.github.com/spring-projects/spring-boot/issues/50942)
- Resources are not cleaned up when resolving an image that is not yet present in the builder [#&#8203;50941](https://redirect.github.com/spring-projects/spring-boot/issues/50941)
- GraphQlWebMvcAutoConfiguration should apply customizers in order [#&#8203;50914](https://redirect.github.com/spring-projects/spring-boot/issues/50914)
- Auto-configured RedisMessageListenerContainer does not use virtual threads when spring.threads.virtual.enabled is true [#&#8203;50884](https://redirect.github.com/spring-projects/spring-boot/issues/50884)
- Context refresh fails when using Actuator on Jersey without spring-boot-health [#&#8203;50872](https://redirect.github.com/spring-projects/spring-boot/issues/50872)
- Context refresh fails on Cloud Foundry when using Actuator without spring-boot-health [#&#8203;50871](https://redirect.github.com/spring-projects/spring-boot/issues/50871)
- IllegalStateException when binding properties to a `@Validated` class that contains a map whose value type is a wildcard [#&#8203;50856](https://redirect.github.com/spring-projects/spring-boot/issues/50856)
- High number of connections due to Mongo health indicator [#&#8203;50852](https://redirect.github.com/spring-projects/spring-boot/issues/50852)
- Inconsistent handling of empty string values of spring.security.oauth2.resourceserver.jwt issuer-uri and jwk-set-uri [#&#8203;50849](https://redirect.github.com/spring-projects/spring-boot/issues/50849)
- Return type nullability of ApplicationContextAssert's getBean methods does not indicate that bean may be null [#&#8203;50845](https://redirect.github.com/spring-projects/spring-boot/issues/50845)
- PropertiesWebClientHttpServiceGroupConfigurer has highest precedence, preventing other configurers from being ordered ahead of it [#&#8203;50843](https://redirect.github.com/spring-projects/spring-boot/issues/50843)
- Exposing gRPC test server port should backoff if gRPC is not present [#&#8203;50825](https://redirect.github.com/spring-projects/spring-boot/issues/50825)
- JpaBaseConfiguration#entityManagerConfiguration can cause a dependency loop on beans declaring AsyncTaskExecutor [#&#8203;50801](https://redirect.github.com/spring-projects/spring-boot/pull/50801)
- spring.grpc.server.health.include-overall-health is not taken into account [#&#8203;50799](https://redirect.github.com/spring-projects/spring-boot/pull/50799)
- Setting 'server.servlet.session.cookie.partitioned' to false still emits the 'Partitioned' cookie attribute [#&#8203;50790](https://redirect.github.com/spring-projects/spring-boot/issues/50790)
- Managed version of Prometheus Client is not aligned with Micrometer's micrometer-registry-prometheus [#&#8203;50780](https://redirect.github.com/spring-projects/spring-boot/issues/50780)
- Map properties bound from empty strings fail with ConverterNotFoundException [#&#8203;50773](https://redirect.github.com/spring-projects/spring-boot/pull/50773)
- Protobuf Common Protos should not be a managed dependency [#&#8203;50772](https://redirect.github.com/spring-projects/spring-boot/issues/50772)
- An application that depends on spring-boot-security-oauth2-resource-server may fail to start with a ClassNotFoundException when Reactor is on the classpath but WebFlux is not [#&#8203;50764](https://redirect.github.com/spring-projects/spring-boot/issues/50764)
- W3CHeaderParser's decoding is not compliant with RFC 3986 [#&#8203;50650](https://redirect.github.com/spring-projects/spring-boot/pull/50650)

#### :notebook\_with\_decorative\_cover: Documentation

- Description of spring.graphql.websocket.connection-init-timeout does not render correctly in the reference guide [#&#8203;51348](https://redirect.github.com/spring-projects/spring-boot/pull/51348)
- spring.profiles.group should have a 'spring-profile-name' hint provider [#&#8203;51284](https://redirect.github.com/spring-projects/spring-boot/issues/51284)
- Remove reference to removed InfluxDB auto-configuration [#&#8203;51176](https://redirect.github.com/spring-projects/spring-boot/issues/51176)
- Use JacksonJsonSerde in Kafka Streams documentation [#&#8203;51161](https://redirect.github.com/spring-projects/spring-boot/issues/51161)
- Document alternatives to HttpMessageConverters [#&#8203;51129](https://redirect.github.com/spring-projects/spring-boot/issues/51129)
- Fix stale type reference for OTLP logging transport metadata [#&#8203;51119](https://redirect.github.com/spring-projects/spring-boot/pull/51119)
- Metadata for spring.test.mockmvc.htmlunit.url declares the wrong type [#&#8203;51115](https://redirect.github.com/spring-projects/spring-boot/issues/51115)
- Caching documentation refers to AutoConfigureCache by its pre-4.0 package [#&#8203;51113](https://redirect.github.com/spring-projects/spring-boot/issues/51113)
- Fix examples in Metadata Format documentation chapter [#&#8203;51102](https://redirect.github.com/spring-projects/spring-boot/issues/51102)
- Improve discoverability of the AOT Cache How-to guides [#&#8203;51000](https://redirect.github.com/spring-projects/spring-boot/issues/51000)
- Table of auto-configured HealthIndicators lists the wrong key for MongoHealthIndicator [#&#8203;50940](https://redirect.github.com/spring-projects/spring-boot/issues/50940)
- Update OpenTelemetryResourceAttributes documentation [#&#8203;50874](https://redirect.github.com/spring-projects/spring-boot/issues/50874)
- Fix `@Value` placeholder syntax in external config docs [#&#8203;50873](https://redirect.github.com/spring-projects/spring-boot/issues/50873)
- Polish gRPC documentation [#&#8203;50863](https://redirect.github.com/spring-projects/spring-boot/pull/50863)
- Fix duplicate word typos in documentation [#&#8203;50850](https://redirect.github.com/spring-projects/spring-boot/issues/50850)
- Fix forwarded headers property in cloud deployment docs [#&#8203;50848](https://redirect.github.com/spring-projects/spring-boot/issues/50848)
- Refer to spring-boot-starter-webmvc, not deprecated spring-boot-starter-web [#&#8203;50847](https://redirect.github.com/spring-projects/spring-boot/issues/50847)
- Fix documentation inconsistencies [#&#8203;50800](https://redirect.github.com/spring-projects/spring-boot/pull/50800)
- Add Spring gRPC properties to Common Application Properties referrence [#&#8203;50789](https://redirect.github.com/spring-projects/spring-boot/pull/50789)
- Document OTLP SSL service connections [#&#8203;50782](https://redirect.github.com/spring-projects/spring-boot/pull/50782)
- Update docs for changes to optional property binding and JPA repositories bootstrap mode [#&#8203;50759](https://redirect.github.com/spring-projects/spring-boot/pull/50759)

#### :hammer: Dependency Upgrades

- Upgrade to ActiveMQ 6.2.9 [#&#8203;51329](https://redirect.github.com/spring-projects/spring-boot/issues/51329)
- Upgrade to [Byte Buddy 1.18.11](https://redirect.github.com/raphw/byte-buddy/releases/tag/byte-buddy-1.18.11) [#&#8203;51003](https://redirect.github.com/spring-projects/spring-boot/issues/51003)
- Upgrade to CycloneDX Maven Plugin 2.9.3 [#&#8203;51185](https://redirect.github.com/spring-projects/spring-boot/issues/51185)
- Upgrade to DB2 JDBC 12.1.5.0 [#&#8203;51005](https://redirect.github.com/spring-projects/spring-boot/issues/51005)
- Upgrade to Elasticsearch Client 9.4.5 [#&#8203;51368](https://redirect.github.com/spring-projects/spring-boot/issues/51368)
- Upgrade to Groovy 5.0.8 [#&#8203;51186](https://redirect.github.com/spring-projects/spring-boot/issues/51186)
- Upgrade to Grpc Java 1.81.1 [#&#8203;51263](https://redirect.github.com/spring-projects/spring-boot/issues/51263)
- Upgrade to [Grpc Java 1.83.1](https://redirect.github.com/grpc/grpc-java/releases/tag/v1.83.1) [#&#8203;51361](https://redirect.github.com/spring-projects/spring-boot/issues/51361)
- Upgrade to [Hibernate 7.4.5.Final](https://redirect.github.com/hibernate/hibernate-orm/releases/tag/7.4.5) [#&#8203;51007](https://redirect.github.com/spring-projects/spring-boot/issues/51007)
- Upgrade to Hibernate Validator 9.1.3.Final [#&#8203;51187](https://redirect.github.com/spring-projects/spring-boot/issues/51187)
- Upgrade to HttpClient5 5.6.4 [#&#8203;51330](https://redirect.github.com/spring-projects/spring-boot/issues/51330)
- Upgrade to HttpCore5 5.4.3 [#&#8203;51010](https://redirect.github.com/spring-projects/spring-boot/issues/51010)
- Upgrade to [Jackson 2 Bom 2.21.5](https://redirect.github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.5) [#&#8203;51011](https://redirect.github.com/spring-projects/spring-boot/issues/51011)
- Upgrade to [Jackson Bom 3.1.5](https://redirect.github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.5) [#&#8203;51012](https://redirect.github.com/spring-projects/spring-boot/issues/51012)
- Upgrade to Jetty 12.1.12 [#&#8203;51302](https://redirect.github.com/spring-projects/spring-boot/issues/51302)
- Upgrade to jOOQ 3.21.7 [#&#8203;51303](https://redirect.github.com/spring-projects/spring-boot/issues/51303)
- Upgrade to [JSpecify 1.0.1](https://redirect.github.com/jspecify/jspecify/releases/tag/v1.0.1) [#&#8203;51189](https://redirect.github.com/spring-projects/spring-boot/issues/51189)
- Upgrade to [Log4j2 2.25.5](https://redirect.github.com/apache/logging-log4j2/releases/tag/rel%2F2.25.5) [#&#8203;51015](https://redirect.github.com/spring-projects/spring-boot/issues/51015)
- Upgrade to Logback 1.5.38 [#&#8203;51016](https://redirect.github.com/spring-projects/spring-boot/issues/51016)
- Upgrade to MariaDB 3.5.10 [#&#8203;51190](https://redirect.github.com/spring-projects/spring-boot/issues/51190)
- Upgrade to Maven Help Plugin 3.5.2 [#&#8203;51018](https://redirect.github.com/spring-projects/spring-boot/issues/51018)
- Upgrade to [Maven Jar Plugin 3.5.1](https://redirect.github.com/apache/maven-jar-plugin/releases/tag/maven-jar-plugin-3.5.1) [#&#8203;51191](https://redirect.github.com/spring-projects/spring-boot/issues/51191)
- Upgrade to [Micrometer 1.17.1](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.17.1) [#&#8203;51251](https://redirect.github.com/spring-projects/spring-boot/issues/51251)
- Upgrade to [Micrometer Tracing 1.7.1](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.7.1) [#&#8203;51252](https://redirect.github.com/spring-projects/spring-boot/issues/51252)
- Upgrade to [MongoDB 5.8.1](https://redirect.github.com/mongodb/mongo-java-driver/releases/tag/r5.8.1) [#&#8203;51360](https://redirect.github.com/spring-projects/spring-boot/issues/51360)
- Upgrade to Native Build Tools Plugin 1.1.4 [#&#8203;50947](https://redirect.github.com/spring-projects/spring-boot/issues/50947)
- Upgrade to Native Build Tools Plugin 1.1.8 [#&#8203;51304](https://redirect.github.com/spring-projects/spring-boot/issues/51304)
- Upgrade to Netty 4.2.17.Final [#&#8203;51305](https://redirect.github.com/spring-projects/spring-boot/issues/51305)
- Upgrade to Oracle Database 23.26.3.0.0 [#&#8203;51193](https://redirect.github.com/spring-projects/spring-boot/issues/51193)
- Upgrade to Pooled JMS 3.2.3 [#&#8203;51196](https://redirect.github.com/spring-projects/spring-boot/issues/51196)
- Upgrade to [Postgresql 42.7.13](https://redirect.github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.13) [#&#8203;51022](https://redirect.github.com/spring-projects/spring-boot/issues/51022)
- Upgrade to Protobuf Java 4.35.1 [#&#8203;51264](https://redirect.github.com/spring-projects/spring-boot/issues/51264)
- Upgrade to Protobuf Maven Plugin 5.1.8 [#&#8203;51331](https://redirect.github.com/spring-projects/spring-boot/issues/51331)
- Upgrade to Pulsar 4.2.4 [#&#8203;51306](https://redirect.github.com/spring-projects/spring-boot/issues/51306)
- Upgrade to [R2DBC MariaDB 1.4.1](https://redirect.github.com/mariadb-corporation/mariadb-connector-r2dbc/releases/tag/1.4.1) [#&#8203;51024](https://redirect.github.com/spring-projects/spring-boot/issues/51024)
- Upgrade to [R2DBC MSSQL 1.0.5.RELEASE](https://redirect.github.com/r2dbc/r2dbc-mssql/releases/tag/v1.0.5.RELEASE) [#&#8203;51025](https://redirect.github.com/spring-projects/spring-boot/issues/51025)
- Upgrade to [R2DBC MySQL 1.4.3](https://redirect.github.com/asyncer-io/r2dbc-mysql/releases/tag/r2dbc-mysql-1.4.3) [#&#8203;51194](https://redirect.github.com/spring-projects/spring-boot/issues/51194)
- Upgrade to [R2DBC Postgresql 1.1.2.RELEASE](https://redirect.github.com/pgjdbc/r2dbc-postgresql/releases/tag/v1.1.2.RELEASE) [#&#8203;51026](https://redirect.github.com/spring-projects/spring-boot/issues/51026)
- Upgrade to [Reactor Bom 2025.0.7](https://redirect.github.com/reactor/reactor/releases/tag/2025.0.7) [#&#8203;51253](https://redirect.github.com/spring-projects/spring-boot/issues/51253)
- Upgrade to [Spring AMQP 4.1.1](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v4.1.1) [#&#8203;51254](https://redirect.github.com/spring-projects/spring-boot/issues/51254)
- Upgrade to [Spring Batch 6.0.5](https://redirect.github.com/spring-projects/spring-batch/releases/tag/v6.0.5) [#&#8203;51255](https://redirect.github.com/spring-projects/spring-boot/issues/51255)
- Upgrade to [Spring Data Bom 2026.0.1](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2026.0.1) [#&#8203;51357](https://redirect.github.com/spring-projects/spring-boot/issues/51357)
- Upgrade to [Spring Framework 7.0.9](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v7.0.9) [#&#8203;51256](https://redirect.github.com/spring-projects/spring-boot/issues/51256)
- Upgrade to [Spring GraphQL 2.0.5](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v2.0.5) [#&#8203;51257](https://redirect.github.com/spring-projects/spring-boot/issues/51257)
- Upgrade to [Spring gRPC 1.1.1](https://redirect.github.com/spring-projects/spring-grpc/releases/tag/v1.1.1) [#&#8203;51171](https://redirect.github.com/spring-projects/spring-boot/issues/51171)
- Upgrade to [Spring HATEOAS 3.1.2](https://redirect.github.com/spring-projects/spring-hateoas/releases/tag/3.1.2) [#&#8203;51358](https://redirect.github.com/spring-projects/spring-boot/issues/51358)
- Upgrade to [Spring Integration 7.1.1](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v7.1.1) [#&#8203;51258](https://redirect.github.com/spring-projects/spring-boot/issues/51258)
- Upgrade to [Spring Kafka 4.1.1](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v4.1.1) [#&#8203;51259](https://redirect.github.com/spring-projects/spring-boot/issues/51259)
- Upgrade to [Spring LDAP 4.1.1](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/4.1.1) [#&#8203;51260](https://redirect.github.com/spring-projects/spring-boot/issues/51260)
- Upgrade to [Spring Pulsar 2.0.7](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v2.0.7) [#&#8203;51328](https://redirect.github.com/spring-projects/spring-boot/issues/51328)
- Upgrade to [Spring Security 7.1.1](https://redirect.github.com/spring-projects/spring-security/releases/tag/7.1.1) [#&#8203;51261](https://redirect.github.com/spring-projects/spring-boot/issues/51261)
- Upgrade to [Spring Session 4.1.1](https://redirect.github.com/spring-projects/spring-session/releases/tag/4.1.1) [#&#8203;51262](https://redirect.github.com/spring-projects/spring-boot/issues/51262)
- Upgrade to [SQLite JDBC 3.53.2.1](https://redirect.github.com/xerial/sqlite-jdbc/releases/tag/3.53.2.1) [#&#8203;51195](https://redirect.github.com/spring-projects/spring-boot/issues/51195)
- Upgrade to [Tomcat 11.0.24](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) [#&#8203;51027](https://redirect.github.com/spring-projects/spring-boot/issues/51027)
- Upgrade to [UnboundID LDAPSDK 7.0.5](https://redirect.github.com/pingidentity/ldapsdk/releases/tag/7.0.5) [#&#8203;51028](https://redirect.github.com/spring-projects/spring-boot/issues/51028)
- Upgrade to WebJars Locator Lite 1.1.4 [#&#8203;51029](https://redirect.github.com/spring-projects/spring-boot/issues/51029)
- Upgrade to [Yasson 3.0.5](https://redirect.github.com/eclipse-ee4j/yasson/releases/tag/3.0.5) [#&#8203;51307](https://redirect.github.com/spring-projects/spring-boot/issues/51307)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;LordKay-sudo](https://redirect.github.com/LordKay-sudo), [@&#8203;MacAlsandair](https://redirect.github.com/MacAlsandair), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;SimonVerhoeven](https://redirect.github.com/SimonVerhoeven), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dhruv-15-03](https://redirect.github.com/dhruv-15-03), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;dmitrysulman](https://redirect.github.com/dmitrysulman), [@&#8203;doehyunbaek](https://redirect.github.com/doehyunbaek), [@&#8203;filiphr](https://redirect.github.com/filiphr), [@&#8203;msridhar](https://redirect.github.com/msridhar), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;ns3154](https://redirect.github.com/ns3154), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;seonwooj0810](https://redirect.github.com/seonwooj0810), [@&#8203;skdas20](https://redirect.github.com/skdas20), [@&#8203;vipi-n](https://redirect.github.com/vipi-n), [@&#8203;vvzvvv](https://redirect.github.com/vvzvvv), [@&#8203;wantaekchoi](https://redirect.github.com/wantaekchoi), [@&#8203;xfocus3](https://redirect.github.com/xfocus3), and [@&#8203;yamamoto-ehhMH](https://redirect.github.com/yamamoto-ehhMH)

### [`v4.1.0`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.1.0)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v4.0.8...v4.1.0)

Full [release notes for Spring Boot 4.1](https://redirect.github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) are available on the wiki.

##### :star: New Features

- Add public constructor to InvalidConfigurationPropertyValueException that accepts a cause [#&#8203;50211](https://redirect.github.com/spring-projects/spring-boot/issues/50211)
- Reduce memory consumption when repeatedly calling WritableJson.toByteArray [#&#8203;49428](https://redirect.github.com/spring-projects/spring-boot/issues/49428)

##### :lady\_beetle: Bug Fixes

- MailSender auto-configuration does not enable hostname verification [#&#8203;50747](https://redirect.github.com/spring-projects/spring-boot/issues/50747)
- Artemis auto-configuration uses a predictable default location for the embedded broker's data [#&#8203;50745](https://redirect.github.com/spring-projects/spring-boot/issues/50745)
- Embedded LDAP SSL should not be enabled when its bundle is empty [#&#8203;50700](https://redirect.github.com/spring-projects/spring-boot/pull/50700)
- InetAddressFilter.externalAddresses does not exclude special purpose addresses from RFC 6890 [#&#8203;50668](https://redirect.github.com/spring-projects/spring-boot/issues/50668)
- NullPointerException in reactor-netty SniProvider and unmapped SSL bundle with RSocket [#&#8203;50645](https://redirect.github.com/spring-projects/spring-boot/issues/50645)
- SSL should not be enabled when a SSL bundle is overridden to an empty string [#&#8203;50635](https://redirect.github.com/spring-projects/spring-boot/issues/50635)
- Test auto-configuration no longer integrates Spring Security with HtmlUnitDriver [#&#8203;50633](https://redirect.github.com/spring-projects/spring-boot/issues/50633)
- Configuration property metadata includes incorrect class references [#&#8203;50632](https://redirect.github.com/spring-projects/spring-boot/issues/50632)
- Docker Compose support does not restore thread interrupt flag when catching InterruptedException [#&#8203;50618](https://redirect.github.com/spring-projects/spring-boot/issues/50618)
- RabbitProperties enables SSL even when spring.rabbitmq.ssl.bundle is overridden to an empty string [#&#8203;50612](https://redirect.github.com/spring-projects/spring-boot/issues/50612)
- NullPointerException in reactor-netty SniProvider when SSL bundle uses client-auth or server truststore without server-name-bundles [#&#8203;50610](https://redirect.github.com/spring-projects/spring-boot/issues/50610)
- SpringJtaPlatform should have been deprecated since 4.1.0-M3 [#&#8203;50592](https://redirect.github.com/spring-projects/spring-boot/issues/50592)
- Layer written outside the output location of '//' exception is thrown when using extract layers in root directory [#&#8203;50510](https://redirect.github.com/spring-projects/spring-boot/issues/50510)
- ConfigurationPropertiesReportEndpoint exposes AOP proxy internals [#&#8203;50417](https://redirect.github.com/spring-projects/spring-boot/issues/50417)
- Created StackTracePrinter instances have no access to the Environment [#&#8203;50414](https://redirect.github.com/spring-projects/spring-boot/issues/50414)
- MappingsEndpoint reports the context's own ID as parentId when a parent exists [#&#8203;50412](https://redirect.github.com/spring-projects/spring-boot/issues/50412)
- Buildpack module does not validate long-to-int casts [#&#8203;50410](https://redirect.github.com/spring-projects/spring-boot/issues/50410)
- Gradle gRPC support fails if protobuf-java dependency is used instead of protobuf-java-util [#&#8203;50405](https://redirect.github.com/spring-projects/spring-boot/pull/50405)
- GraphQL WebSocket support does not configure allowed origins [#&#8203;50394](https://redirect.github.com/spring-projects/spring-boot/issues/50394)
- Spring Boot Loader Does Not Support RSA and EC Signed Jars [#&#8203;50298](https://redirect.github.com/spring-projects/spring-boot/issues/50298)
- Meter registries are not removed from the global registry when the context is closed [#&#8203;50287](https://redirect.github.com/spring-projects/spring-boot/issues/50287)
- DataSourceBuilder cannot derive a DataSource from a lazy connection proxy [#&#8203;50271](https://redirect.github.com/spring-projects/spring-boot/pull/50271)
- Nullable annotations from AbstractErrorController.getErrorAttributes are not aligned with implementation [#&#8203;50266](https://redirect.github.com/spring-projects/spring-boot/issues/50266)
- Bean definitions can be added with an initializer before setAllowBeanDefinitionOverriding is called [#&#8203;50264](https://redirect.github.com/spring-projects/spring-boot/issues/50264)
- EndpointRequest links matcher unnecessarily matches HTTP methods other than GET  [#&#8203;50261](https://redirect.github.com/spring-projects/spring-boot/issues/50261)
- Actuator's '/cloudfoundryapplication' endpoint does not work if restrictive CORS configuration is provided using a bean named corsConfigurationSource [#&#8203;50258](https://redirect.github.com/spring-projects/spring-boot/issues/50258)
- ThreadPoolTaskScheduleBuilder unnecessarily loses precision when configuring await termination time [#&#8203;50234](https://redirect.github.com/spring-projects/spring-boot/issues/50234)
- NimbusJwtDecoder silently accepts unknown values for spring.security.oauth2.resourceserver.jwt.jws-algorithms [#&#8203;50228](https://redirect.github.com/spring-projects/spring-boot/issues/50228)
- Missing dependency management for spring-boot-web-server-test [#&#8203;50224](https://redirect.github.com/spring-projects/spring-boot/issues/50224)
- Spring Batch support for MongoDB modules are not included in dependency management [#&#8203;50223](https://redirect.github.com/spring-projects/spring-boot/pull/50223)
- Apply HTML escaping to timestamp attribute in Whitelabel error page [#&#8203;50216](https://redirect.github.com/spring-projects/spring-boot/issues/50216)
- GrpcServerHealthScheduler is not started in servlet environments [#&#8203;50209](https://redirect.github.com/spring-projects/spring-boot/issues/50209)
- Setting server.servlet.session.cookie.partitioned=true has no effect when using Tomcat [#&#8203;50204](https://redirect.github.com/spring-projects/spring-boot/issues/50204)

##### :notebook\_with\_decorative\_cover: Documentation

- Fix reference to Gradle documentation for module replacement [#&#8203;50647](https://redirect.github.com/spring-projects/spring-boot/issues/50647)
- Document SSL reloading with Let's Encrypt [#&#8203;50630](https://redirect.github.com/spring-projects/spring-boot/issues/50630)
- Remove the use of Optional from Data Neo4j repository examples [#&#8203;50622](https://redirect.github.com/spring-projects/spring-boot/issues/50622)
- Fix typos in documentation [#&#8203;50620](https://redirect.github.com/spring-projects/spring-boot/issues/50620)
- Clarify dependency requirement for Bean Validation support [#&#8203;50614](https://redirect.github.com/spring-projects/spring-boot/issues/50614)
- Document Java 25 requirement for AOT cache [#&#8203;50485](https://redirect.github.com/spring-projects/spring-boot/issues/50485)
- Add links for Java CAS Client Spring Boot Starter [#&#8203;50285](https://redirect.github.com/spring-projects/spring-boot/issues/50285)
- Document known testcontainers lifecycle issues  [#&#8203;50220](https://redirect.github.com/spring-projects/spring-boot/issues/50220)
- Document adding multiple connectors for Jetty [#&#8203;50218](https://redirect.github.com/spring-projects/spring-boot/issues/50218)
- Polish InvalidConfigurationPropertyValueException constructor javadoc [#&#8203;50214](https://redirect.github.com/spring-projects/spring-boot/issues/50214)
- Fix typo in Spring Security OAuth2 client registration documentation [#&#8203;50199](https://redirect.github.com/spring-projects/spring-boot/issues/50199)

##### :hammer: Dependency Upgrades

- Upgrade to ActiveMQ 6.2.6 [#&#8203;50652](https://redirect.github.com/spring-projects/spring-boot/issues/50652)
- Upgrade to Byte Buddy 1.18.10 [#&#8203;50693](https://redirect.github.com/spring-projects/spring-boot/issues/50693)
- Upgrade to [Caffeine 3.2.4](https://redirect.github.com/ben-manes/caffeine/releases/tag/v3.2.4) [#&#8203;50338](https://redirect.github.com/spring-projects/spring-boot/issues/50338)
- Upgrade to Cassandra Driver 4.19.3 [#&#8203;50654](https://redirect.github.com/spring-projects/spring-boot/issues/50654)
- Upgrade to [Couchbase Client 3.11.3](https://docs.couchbase.com/java-sdk/current/project-docs/sdk-release-notes.html) [#&#8203;50576](https://redirect.github.com/spring-projects/spring-boot/issues/50576)
- Upgrade to Elasticsearch Client 9.4.2 [#&#8203;50655](https://redirect.github.com/spring-projects/spring-boot/issues/50655)
- Upgrade to Glassfish JAXB 4.0.9 [#&#8203;50656](https://redirect.github.com/spring-projects/spring-boot/issues/50656)
- Upgrade to Groovy 5.0.6 [#&#8203;50340](https://redirect.github.com/spring-projects/spring-boot/issues/50340)
- Upgrade to Hibernate 7.4.1.Final [#&#8203;50732](https://redirect.github.com/spring-projects/spring-boot/issues/50732)
- Upgrade to [Infinispan 16.1.4](https://redirect.github.com/infinispan/infinispan/releases/tag/16.1.4) [#&#8203;50342](https://redirect.github.com/spring-projects/spring-boot/issues/50342)
- Upgrade to Jackson 2 Bom 2.21.4 [#&#8203;50657](https://redirect.github.com/spring-projects/spring-boot/issues/50657)
- Upgrade to Jackson Bom 3.1.4 [#&#8203;50658](https://redirect.github.com/spring-projects/spring-boot/issues/50658)
- Upgrade to Jakarta Json Bind 3.0.2 [#&#8203;50659](https://redirect.github.com/spring-projects/spring-boot/issues/50659)
- Upgrade to [Jakarta XML Bind 4.0.5](https://redirect.github.com/jakartaee/jaxb-api/releases/tag/4.0.5) [#&#8203;50345](https://redirect.github.com/spring-projects/spring-boot/issues/50345)
- Upgrade to Jaxen 2.0.6 [#&#8203;50710](https://redirect.github.com/spring-projects/spring-boot/issues/50710)
- Upgrade to [Jetty 12.1.10](https://redirect.github.com/jetty/jetty.project/releases/tag/jetty-12.1.10) [#&#8203;50661](https://redirect.github.com/spring-projects/spring-boot/issues/50661)
- Upgrade to Jetty Reactive HTTPClient 4.1.5 [#&#8203;50711](https://redirect.github.com/spring-projects/spring-boot/issues/50711)
- Upgrade to jOOQ 3.21.5 [#&#8203;50712](https://redirect.github.com/spring-projects/spring-boot/issues/50712)
- Upgrade to [Kafka 4.2.1](https://downloads.apache.org/kafka/4.2.1/RELEASE_NOTES.html) [#&#8203;50662](https://redirect.github.com/spring-projects/spring-boot/issues/50662)
- Upgrade to [Kotlin 2.3.21](https://redirect.github.com/JetBrains/kotlin/releases/tag/v2.3.21) [#&#8203;50347](https://redirect.github.com/spring-projects/spring-boot/issues/50347)
- Upgrade to [Lettuce 7.5.2.RELEASE](https://redirect.github.com/redis/lettuce/releases/tag/7.5.2.RELEASE) [#&#8203;50581](https://redirect.github.com/spring-projects/spring-boot/issues/50581)
- Upgrade to [Liquibase 5.0.3](https://redirect.github.com/liquibase/liquibase/releases/tag/v5.0.3) [#&#8203;50582](https://redirect.github.com/spring-projects/spring-boot/issues/50582)
- Upgrade to Logback 1.5.34 [#&#8203;50663](https://redirect.github.com/spring-projects/spring-boot/issues/50663)
- Upgrade to [Maven Enforcer Plugin 3.6.3](https://redirect.github.com/apache/maven-enforcer/releases/tag/enforcer-3.6.3) [#&#8203;50583](https://redirect.github.com/spring-projects/spring-boot/issues/50583)
- Upgrade to [Maven Failsafe Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50664](https://redirect.github.com/spring-projects/spring-boot/issues/50664)
- Upgrade to [Maven Surefire Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50665](https://redirect.github.com/spring-projects/spring-boot/issues/50665)
- Upgrade to [Micrometer 1.17.0](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.17.0) [#&#8203;50559](https://redirect.github.com/spring-projects/spring-boot/issues/50559)
- Upgrade to [Micrometer Tracing 1.7.0](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.7.0) [#&#8203;50560](https://redirect.github.com/spring-projects/spring-boot/issues/50560)
- Upgrade to MongoDB 5.8.0 [#&#8203;50608](https://redirect.github.com/spring-projects/spring-boot/issues/50608)
- Upgrade to [Native Build Tools Plugin 1.1.1](https://redirect.github.com/graalvm/native-build-tools/releases/tag/1.1.1) [#&#8203;50585](https://redirect.github.com/spring-projects/spring-boot/issues/50585)
- Upgrade to [Neo4j Java Driver 6.1.0](https://redirect.github.com/neo4j/neo4j-java-driver/releases/tag/6.1.0) [#&#8203;50586](https://redirect.github.com/spring-projects/spring-boot/issues/50586)
- Upgrade to Netty 4.2.15.Final [#&#8203;50666](https://redirect.github.com/spring-projects/spring-boot/issues/50666)
- Upgrade to [OpenTelemetry 1.62.0](https://redirect.github.com/open-telemetry/opentelemetry-java/releases/tag/v1.62.0) [#&#8203;50588](https://redirect.github.com/spring-projects/spring-boot/issues/50588)
- Upgrade to Oracle Database 23.26.2.0.0 [#&#8203;50667](https://redirect.github.com/spring-projects/spring-boot/issues/50667)
- Upgrade to [Postgresql 42.7.11](https://redirect.github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.11) [#&#8203;50349](https://redirect.github.com/spring-projects/spring-boot/issues/50349)
- Upgrade to [Protobuf Java 4.34.2](https://redirect.github.com/protocolbuffers/protobuf/releases/tag/v4.34.2) [#&#8203;50590](https://redirect.github.com/spring-projects/spring-boot/issues/50590)
- Upgrade to [Protobuf Maven Plugin 5.1.4](https://redirect.github.com/ascopes/protobuf-maven-plugin/releases/tag/v5.1.4) [#&#8203;50589](https://redirect.github.com/spring-projects/spring-boot/issues/50589)
- Upgrade to Pulsar 4.2.2 [#&#8203;50713](https://redirect.github.com/spring-projects/spring-boot/issues/50713)
- Upgrade to [R2DBC MySQL 1.4.2](https://redirect.github.com/asyncer-io/r2dbc-mysql/releases/tag/r2dbc-mysql-1.4.2) [#&#8203;50351](https://redirect.github.com/spring-projects/spring-boot/issues/50351)
- Upgrade to [Reactor Bom 2025.0.6](https://redirect.github.com/reactor/reactor/releases/tag/2025.0.6) [#&#8203;50561](https://redirect.github.com/spring-projects/spring-boot/issues/50561)
- Upgrade to SAAJ Impl 3.0.6 [#&#8203;50714](https://redirect.github.com/spring-projects/spring-boot/issues/50714)
- Upgrade to SLF4J 2.0.18 [#&#8203;50591](https://redirect.github.com/spring-projects/spring-boot/issues/50591)
- Upgrade to [Spring AMQP 4.1.0](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v4.1.0) [#&#8203;50562](https://redirect.github.com/spring-projects/spring-boot/issues/50562)
- Upgrade to [Spring Batch 6.0.4](https://redirect.github.com/spring-projects/spring-batch/releases/tag/v6.0.4) [#&#8203;50563](https://redirect.github.com/spring-projects/spring-boot/issues/50563)
- Upgrade to [Spring Data Bom 2026.0.0](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2026.0.0) [#&#8203;50564](https://redirect.github.com/spring-projects/spring-boot/issues/50564)
- Upgrade to [Spring Framework 7.0.8](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v7.0.8) [#&#8203;50565](https://redirect.github.com/spring-projects/spring-boot/issues/50565)
- Upgrade to [Spring GraphQL 2.0.4](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v2.0.4) [#&#8203;50741](https://redirect.github.com/spring-projects/spring-boot/issues/50741)
- Upgrade to [Spring gRPC 1.1.0](https://redirect.github.com/spring-projects/spring-grpc/releases/tag/v1.1.0) [#&#8203;50566](https://redirect.github.com/spring-projects/spring-boot/issues/50566)
- Upgrade to [Spring HATEOAS 3.1.1](https://redirect.github.com/spring-projects/spring-hateoas/releases/tag/3.1.1) [#&#8203;50567](https://redirect.github.com/spring-projects/spring-boot/issues/50567)
- Upgrade to [Spring Integration 7.1.0](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v7.1.0) [#&#8203;50568](https://redirect.github.com/spring-projects/spring-boot/issues/50568)
- Upgrade to [Spring Kafka 4.1.0](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v4.1.0) [#&#8203;50569](https://redirect.github.com/spring-projects/spring-boot/issues/50569)
- Upgrade to [Spring LDAP 4.1.0](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/4.1.0) [#&#8203;50570](https://redirect.github.com/spring-projects/spring-boot/issues/50570)
- Upgrade to [Spring Pulsar 2.0.6](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v2.0.6) [#&#8203;50571](https://redirect.github.com/spring-projects/spring-boot/issues/50571)
- Upgrade to [Spring RESTDocs 4.0.1](https://redirect.github.com/spring-projects/spring-restdocs/releases/tag/v4.0.1) [#&#8203;50572](https://redirect.github.com/spring-projects/spring-boot/issues/50572)
- Upgrade to [Spring Security 7.1.0](https://redirect.github.com/spring-projects/spring-security/releases/tag/7.1.0) [#&#8203;50573](https://redirect.github.com/spring-projects/spring-boot/issues/50573)
- Upgrade to [Spring Session 4.1.0](https://redirect.github.com/spring-projects/spring-session/releases/tag/4.1.0) [#&#8203;50574](https://redirect.github.com/spring-projects/spring-boot/issues/50574)
- Upgrade to [Spring WS 5.0.2](https://redirect.github.com/spring-projects/spring-ws/releases/tag/v5.0.2) [#&#8203;50575](https://redirect.github.com/spring-projects/spring-boot/issues/50575)
- Upgrade to SQLite JDBC 3.53.2.0 [#&#8203;50715](https://redirect.github.com/spring-projects/spring-boot/issues/50715)
- Upgrade to [Tomcat 11.0.22](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) [#&#8203;50354](https://redirect.github.com/spring-projects/spring-boot/issues/50354)

##### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;Abdlatif-nabgha](https://redirect.github.com/Abdlatif-nabgha), [@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;Kapil-chn7](https://redirect.github.com/Kapil-chn7), [@&#8203;Kimgyuilli](https://redirect.github.com/Kimgyuilli), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;ares333](https://redirect.github.com/ares333), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;eddumelendez](https://redirect.github.com/eddumelendez), [@&#8203;henriquejsza](https://redirect.github.com/henriquejsza), [@&#8203;igormukhin](https://redirect.github.com/igormukhin), [@&#8203;johnnypwong](https://redirect.github.com/johnnypwong), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;leestana01](https://redirect.github.com/leestana01), [@&#8203;mheath](https://redirect.github.com/mheath), [@&#8203;mmoayyed](https://redirect.github.com/mmoayyed), [@&#8203;msridhar](https://redirect.github.com/msridhar), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;vinhhieu21](https://redirect.github.com/vinhhieu21), [@&#8203;vpavic](https://redirect.github.com/vpavic), [@&#8203;won-seoop](https://redirect.github.com/won-seoop), and [@&#8203;zxuhan](https://redirect.github.com/zxuhan)

### [`v4.0.8`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.0.8)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v4.0.7...v4.0.8)

##### :lady\_beetle: Bug Fixes

- Kafka consumer-specific security protocol is not taken into account [#&#8203;51365](https://redirect.github.com/spring-projects/spring-boot/issues/51365)
- Temporary file is not deleted when ExportedImageTar construction fails [#&#8203;51117](https://redirect.github.com/spring-projects/spring-boot/pull/51117)
- spring-boot-h2-console pulls servlet-api as transitive dependency [#&#8203;51094](https://redirect.github.com/spring-projects/spring-boot/issues/51094)
- Methods that return the result of Map#remove are not declared with a `@Nullable` return type [#&#8203;50972](https://redirect.github.com/spring-projects/spring-boot/pull/50972)
- PropertiesLauncher does not log nested archive paths [#&#8203;50968](https://redirect.github.com/spring-projects/spring-boot/pull/50968)
- JarFile is not closed when finding main class from archive [#&#8203;50949](https://redirect.github.com/spring-projects/spring-boot/pull/50949)
- CloudFoundry reactive auto-configuration should not require a WebClient.Builder bean to be defined [#&#8203;50928](https://redirect.github.com/spring-projects/spring-boot/issues/50928)
- Resources are not cleaned up when resolving an image that is not yet present in the builder [#&#8203;50919](https://redirect.github.com/spring-projects/spring-boot/pull/50919)
- Context refresh fails on reactive Cloud Foundry when using Actuator without spring-boot-health [#&#8203;50916](https://redirect.github.com/spring-projects/spring-boot/pull/50916)
- GraphQlWebMvcAutoConfiguration should apply customizers in order [#&#8203;50908](https://redirect.github.com/spring-projects/spring-boot/pull/50908)
- Micrometer registries pin the application context [#&#8203;50886](https://redirect.github.com/spring-projects/spring-boot/pull/50886)
- Context refresh fails when using Actuator on Jersey without spring-boot-health [#&#8203;50858](https://redirect.github.com/spring-projects/spring-boot/pull/50858)
- Context refresh fails on Cloud Foundry when using Actuator without spring-boot-health [#&#8203;50857](https://redirect.github.com/spring-projects/spring-boot/pull/50857)
- IllegalStateException when binding properties to a `@Validated` class that contains a map whose value type is a wildcard [#&#8203;50798](https://redirect.github.com/spring-projects/spring-boot/pull/50798)
- Setting 'server.servlet.session.cookie.partitioned' to false still emits the 'Partitioned' cookie attribute [#&#8203;50781](https://redirect.github.com/spring-projects/spring-boot/pull/50781)
- Application-managed JUL bridge handler should only be removed if installed [#&#8203;50779](https://redirect.github.com/spring-projects/spring-boot/pull/50779)
- Inconsistent handling of empty string values of spring.security.oauth2.resourceserver.jwt issuer-uri and jwk-set-uri [#&#8203;50755](https://redirect.github.com/spring-projects/spring-boot/pull/50755)
- PropertiesWebClientHttpServiceGroupConfigurer has highest precedence, preventing other configurers from being ordered ahead of it [#&#8203;50737](https://redirect.github.com/spring-projects/spring-boot/pull/50737)
- High number of connections due to Mongo health indicator [#&#8203;50734](https://redirect.github.com/spring-projects/spring-boot/pull/50734)
- Return type nullability of ApplicationContextAssert's getBean methods does not indicate that bean may be null [#&#8203;50701](https://redirect.github.com/spring-projects/spring-boot/pull/50701)
- NativeImageResourceProvider flattens Flyway migration paths in subdirectories [#&#8203;50433](https://redirect.github.com/spring-projects/spring-boot/pull/50433)
- Fix ordering of Kotlinx Serialization CodecCustomizer [#&#8203;50428](https://redirect.github.com/spring-projects/spring-boot/pull/50428)
- Metadata annotation processor ignores getter-level `@NestedConfigurationProperty` for records [#&#8203;50096](https://redirect.github.com/spring-projects/spring-boot/issues/50096)

##### :notebook\_with\_decorative\_cover: Documentation

- spring.profiles.group should have a 'spring-profile-name' hint provider [#&#8203;51273](https://redirect.github.com/spring-projects/spring-boot/issues/51273)
- Remove reference to removed InfluxDB auto-configuration [#&#8203;51172](https://redirect.github.com/spring-projects/spring-boot/pull/51172)
- Use JacksonJsonSerde in Kafka Streams documentation [#&#8203;51152](https://redirect.github.com/spring-projects/spring-boot/pull/51152)
- Document alternatives to HttpMessageConverters [#&#8203;51124](https://redirect.github.com/spring-projects/spring-boot/pull/51124)
- Caching documentation refers to AutoConfigureCache by its pre-4.0 package [#&#8203;51111](https://redirect.github.com/spring-projects/spring-boot/pull/51111)
- Metadata for spring.test.mockmvc.htmlunit.url declares the wrong type [#&#8203;51110](https://redirect.github.com/spring-projects/spring-boot/pull/51110)
- Fix examples in Metadata Format documentation chapter [#&#8203;51097](https://redirect.github.com/spring-projects/spring-boot/pull/51097)
- Improve discoverability of the AOT Cache How-to guides [#&#8203;50996](https://redirect.github.com/spring-projects/spring-boot/issues/50996)
- Table of auto-configured HealthIndicators lists the wrong key for MongoHealthIndicator [#&#8203;50931](https://redirect.github.com/spring-projects/spring-boot/pull/50931)
- Update OpenTelemetryResourceAttributes documentation [#&#8203;50867](https://redirect.github.com/spring-projects/spring-boot/pull/50867)
- Fix `@Value` placeholder syntax in external config docs [#&#8203;50859](https://redirect.github.com/spring-projects/spring-boot/pull/50859)
- Refer to spring-boot-starter-webmvc, not deprecated spring-boot-starter-web [#&#8203;50842](https://redirect.github.com/spring-projects/spring-boot/pull/50842)
- Fix forwarded headers property in cloud deployment docs [#&#8203;50841](https://redirect.github.com/spring-projects/spring-boot/pull/50841)
- Fix duplicate word typos in documentation [#&#8203;50738](https://redirect.github.com/spring-projects/spring-boot/pull/50738)

##### :hammer: Dependency Upgrades

- Upgrade to CycloneDX Maven Plugin 2.9.3 [#&#8203;51178](https://redirect.github.com/spring-projects/spring-boot/issues/51178)
- Upgrade to DB2 JDBC 12.1.5.0 [#&#8203;50977](https://redirect.github.com/spring-projects/spring-boot/issues/50977)
- Upgrade to [Elasticsearch Client 9.2.9](https://www.elastic.co/docs/release-notes/elasticsearch/clients/java/9-2-9) [#&#8203;51366](https://redirect.github.com/spring-projects/spring-boot/issues/51366)
- Upgrade to Groovy 5.0.8 [#&#8203;51179](https://redirect.github.com/spring-projects/spring-boot/issues/51179)
- Upgrade to Hibernate 7.2.24.Final [#&#8203;51180](https://redirect.github.com/spring-projects/spring-boot/issues/51180)
- Upgrade to [Jackson 2 Bom 2.21.5](https://redirect.github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.5) [#&#8203;50980](https://redirect.github.com/spring-projects/spring-boot/issues/50980)
- Upgrade to [Jackson Bom 3.1.5](https://redirect.github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.5) [#&#8203;50981](https://redirect.github.com/spring-projects/spring-boot/issues/50981)
- Upgrade to Jetty 12.1.12 [#&#8203;51298](https://redirect.github.com/spring-projects/spring-boot/issues/51298)
- Upgrade to jOOQ 3.19.37 [#&#8203;51299](https://redirect.github.com/spring-projects/spring-boot/issues/51299)
- Upgrade to [JSpecify 1.0.1](https://redirect.github.com/jspecify/jspecify/releases/tag/v1.0.1) [#&#8203;51181](https://redirect.github.com/spring-projects/spring-boot/issues/51181)
- Upgrade to [Log4j2 2.25.5](https://redirect.github.com/apache/logging-log4j2/releases/tag/rel%2F2.25.5) [#&#8203;50984](https://redirect.github.com/spring-projects/spring-boot/issues/50984)
- Upgrade to Logback 1.5.38 [#&#8203;50985](https://redirect.github.com/spring-projects/spring-boot/issues/50985)
- Upgrade to MariaDB 3.5.10 [#&#8203;51182](https://redirect.github.com/spring-projects/spring-boot/issues/51182)
- Upgrade to Maven Help Plugin 3.5.2 [#&#8203;50987](https://redirect.github.com/spring-projects/spring-boot/issues/50987)
- Upgrade to [Micrometer 1.16.7](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.16.7) [#&#8203;51235](https://redirect.github.com/spring-projects/spring-boot/issues/51235)
- Upgrade to [Micrometer Tracing 1.6.7](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.6.7) [#&#8203;51236](https://redirect.github.com/spring-projects/spring-boot/issues/51236)
- Upgrade to Netty 4.2.17.Final [#&#8203;51300](https://redirect.github.com/spring-projects/spring-boot/issues/51300)
- Upgrade to [Postgresql 42.7.13](https://redirect.github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.13) [#&#8203;50989](https://redirect.github.com/spring-projects/spring-boot/issues/50989)
- Upgrade to [R2DBC MariaDB 1.3.1](https://redirect.github.com/mariadb-corporation/mariadb-connector-r2dbc/releases/tag/1.3.1) [#&#8203;50990](https://redirect.github.com/spring-projects/spring-boot/issues/50990)
- Upgrade to [R2DBC MSSQL 1.0.5.RELEASE](https://redirect.github.com/r2dbc/r2dbc-mssql/releases/tag/v1.0.5.RELEASE) [#&#8203;50991](https://redirect.github.com/spring-projects/spring-boot/issues/50991)
- Upgrade to [R2DBC MySQL 1.4.3](https://redirect.github.com/asyncer-io/r2dbc-mysql/releases/tag/r2dbc-mysql-1.4.3) [#&#8203;51183](https://redirect.github.com/spring-projects/spring-boot/issues/51183)
- Upgrade to [R2DBC Postgresql 1.1.2.RELEASE](https://redirect.github.com/pgjdbc/r2dbc-postgresql/releases/tag/v1.1.2.RELEASE) [#&#8203;50992](https://redirect.github.com/spring-projects/spring-boot/issues/50992)
- Upgrade to [Reactor Bom 2025.0.7](https://redirect.github.com/reactor/reactor/releases/tag/2025.0.7) [#&#8203;51237](https://redirect.github.com/spring-projects/spring-boot/issues/51237)
- Upgrade to [Spring AMQP 4.0.5](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v4.0.5) [#&#8203;51238](https://redirect.github.com/spring-projects/spring-boot/issues/51238)
- Upgrade to [Spring Batch 6.0.5](https://redirect.github.com/spring-projects/spring-batch/releases/tag/v6.0.5) [#&#8203;51239](https://redirect.github.com/spring-projects/spring-boot/issues/51239)
- Upgrade to [Spring Data Bom 2025.1.7](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.1.7) [#&#8203;51355](https://redirect.github.com/spring-projects/spring-boot/issues/51355)
- Upgrade to [Spring Framework 7.0.9](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v7.0.9) [#&#8203;51240](https://redirect.github.com/spring-projects/spring-boot/issues/51240)
- Upgrade to [Spring GraphQL 2.0.5](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v2.0.5) [#&#8203;51241](https://redirect.github.com/spring-projects/spring-boot/issues/51241)
- Upgrade to [Spring HATEOAS 3.0.5](https://redirect.github.com/spring-projects/spring-hateoas/releases/tag/3.0.5) [#&#8203;51356](https://redirect.github.com/spring-projects/spring-boot/issues/51356)
- Upgrade to [Spring Integration 7.0.6](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v7.0.6) [#&#8203;51242](https://redirect.github.com/spring-projects/spring-boot/issues/51242)
- Upgrade to [Spring Kafka 4.0.7](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v4.0.7) [#&#8203;51243](https://redirect.github.com/spring-projects/spring-boot/issues/51243)
- Upgrade to [Spring LDAP 4.0.5](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/4.0.5) [#&#8203;51244](https://redirect.github.com/spring-projects/spring-boot/issues/51244)
- Upgrade to [Spring Pulsar 2.0.7](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v2.0.7) [#&#8203;51327](https://redirect.github.com/spring-projects/spring-boot/issues/51327)
- Upgrade to [Spring Security 7.0.7](https://redirect.github.com/spring-projects/spring-security/releases/tag/7.0.7) [#&#8203;51245](https://redirect.github.com/spring-projects/spring-boot/issues/51245)
- Upgrade to [Spring Session 4.0.5](https://redirect.github.com/spring-projects/spring-session/releases/tag/4.0.5) [#&#8203;51246](https://redirect.github.com/spring-projects/spring-boot/issues/51246)
- Upgrade to [Tomcat 11.0.24](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) [#&#8203;50993](https://redirect.github.com/spring-projects/spring-boot/issues/50993)
- Upgrade to [UnboundID LDAPSDK 7.0.5](https://redirect.github.com/pingidentity/ldapsdk/releases/tag/7.0.5) [#&#8203;50994](https://redirect.github.com/spring-projects/spring-boot/issues/50994)
- Upgrade to WebJars Locator Lite 1.1.4 [#&#8203;50995](https://redirect.github.com/spring-projects/spring-boot/issues/50995)
- Upgrade to [Yasson 3.0.5](https://redirect.github.com/eclipse-ee4j/yasson/releases/tag/3.0.5) [#&#8203;51301](https://redirect.github.com/spring-projects/spring-boot/issues/51301)

##### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;LordKay-sudo](https://redirect.github.com/LordKay-sudo), [@&#8203;MacAlsandair](https://redirect.github.com/MacAlsandair), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dhruv-15-03](https://redirect.github.com/dhruv-15-03), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;dmitrysulman](https://redirect.github.com/dmitrysulman), [@&#8203;doehyunbaek](https://redirect.github.com/doehyunbaek), [@&#8203;filiphr](https://redirect.github.com/filiphr), [@&#8203;msridhar](https://redirect.github.com/msridhar), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;seonwooj0810](https://redirect.github.com/seonwooj0810), [@&#8203;vipi-n](https://redirect.github.com/vipi-n), [@&#8203;vvzvvv](https://redirect.github.com/vvzvvv), [@&#8203;wantaekchoi](https://redirect.github.com/wantaekchoi), [@&#8203;xfocus3](https://redirect.github.com/xfocus3), and [@&#8203;yamamoto-ehhMH](https://redirect.github.com/yamamoto-ehhMH)

### [`v4.0.7`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.0.7)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v4.0.6...v4.0.7)

##### :lady\_beetle: Bug Fixes

- MailSender auto-configuration does not enable hostname verification [#&#8203;50746](https://redirect.github.com/spring-projects/spring-boot/issues/50746)
- Artemis auto-configuration uses a predictable default location for the embedded broker's data [#&#8203;50744](https://redirect.github.com/spring-projects/spring-boot/issues/50744)
- NullPointerException in reactor-netty SniProvider and unmapped SSL bundle with RSocket [#&#8203;50640](https://redirect.github.com/spring-projects/spring-boot/pull/50640)
- SSL should not be enabled when a SSL bundle is overridden to an empty string [#&#8203;50634](https://redirect.github.com/spring-projects/spring-boot/issues/50634)
- Docker Compose support does not restore thread interrupt flag when catching InterruptedException [#&#8203;50617](https://redirect.github.com/spring-projects

> ❗ **Important**
> 
> ✂ PR body was truncated to here.


</details>

---

### Configuration

📅 **Schedule**: (UTC)

- Branch creation
  - At any time (no schedule defined)
- Automerge
  - At any time (no schedule defined)

🚦 **Automerge**: Enabled.

♻ **Rebasing**: Whenever PR is behind base branch, or you tick the rebase/retry checkbox.

🔕 **Ignore**: Close this PR and you won't be reminded about this update again.

---

 - [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box

---

This PR was generated by [Mend Renovate](https://mend.io/renovate/). View the [repository job log](https://developer.mend.io/github/piomin/sample-spring-kafka-datamesh).
<!--renovate-debug:eyJjcmVhdGVkSW5WZXIiOiI0NC4xMjUuMSIsInVwZGF0ZWRJblZlciI6IjQ0LjEyNS4xIiwidGFyZ2V0QnJhbmNoIjoibWFzdGVyIiwibGFiZWxzIjpbXX0=-->

