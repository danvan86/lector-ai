> ℹ️ **Note**
> 
> This PR body was truncated due to platform limits.

This PR contains the following updates:

| Package | Change | [Age](https://docs.renovatebot.com/merge-confidence/) | [Confidence](https://docs.renovatebot.com/merge-confidence/) |
|---|---|---|---|
| [org.springframework.boot:spring-boot-maven-plugin](https://spring.io/projects/spring-boot) ([source](https://redirect.github.com/spring-projects/spring-boot)) | `3.1.4` → `3.5.16` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.springframework.boot:spring-boot-maven-plugin/3.5.16?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.springframework.boot:spring-boot-maven-plugin/3.1.4/3.5.16?slim=true) |
| [org.springframework.boot:spring-boot-dependencies](https://spring.io/projects/spring-boot) ([source](https://redirect.github.com/spring-projects/spring-boot)) | `3.1.4` → `3.5.16` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.springframework.boot:spring-boot-dependencies/3.5.16?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.springframework.boot:spring-boot-dependencies/3.1.4/3.5.16?slim=true) |

---

> [!WARNING]
> Some dependencies could not be looked up. Check the [Dependency Dashboard](../issues/17) for more information.

---

### Release Notes

<details>
<summary>spring-projects/spring-boot (org.springframework.boot:spring-boot-maven-plugin)</summary>

### [`v3.5.16`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.16)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.15...v3.5.16)

##### :hammer: Dependency Upgrades

- Upgrade to [Spring AMQP 3.2.12](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v3.2.12) [#&#8203;50818](https://redirect.github.com/spring-projects/spring-boot/issues/50818)
- Upgrade to [Spring Data Bom 2025.0.13](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.0.13) [#&#8203;50819](https://redirect.github.com/spring-projects/spring-boot/issues/50819)
- Upgrade to [Spring Integration 6.5.10](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v6.5.10) [#&#8203;50820](https://redirect.github.com/spring-projects/spring-boot/issues/50820)

### [`v3.5.15`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.15)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.14...v3.5.15)

##### :lady\_beetle: Bug Fixes

- Artemis auto-configuration uses a predictable default location for the embedded broker's data [#&#8203;50743](https://redirect.github.com/spring-projects/spring-boot/issues/50743)
- MailSender auto-configuration does not enable hostname verification [#&#8203;50742](https://redirect.github.com/spring-projects/spring-boot/issues/50742)
- SSL should not be enabled when a SSL bundle is overridden to an empty string [#&#8203;50624](https://redirect.github.com/spring-projects/spring-boot/pull/50624)
- Layer written outside the output location of '//' exception is thrown when using extract layers in root directory [#&#8203;50501](https://redirect.github.com/spring-projects/spring-boot/pull/50501)
- Docker Compose support does not restore thread interrupt flag when catching InterruptedException [#&#8203;50451](https://redirect.github.com/spring-projects/spring-boot/pull/50451)
- RabbitProperties enables SSL even when spring.rabbitmq.ssl.bundle is overridden to an empty string [#&#8203;50429](https://redirect.github.com/spring-projects/spring-boot/pull/50429)
- GraphQL WebSocket support does not configure allowed origins [#&#8203;50391](https://redirect.github.com/spring-projects/spring-boot/issues/50391)
- Buildpack module does not validate long-to-int casts [#&#8203;50382](https://redirect.github.com/spring-projects/spring-boot/pull/50382)
- MappingsEndpoint reports the context's own ID as parentId when a parent exists [#&#8203;50373](https://redirect.github.com/spring-projects/spring-boot/pull/50373)
- Created StackTracePrinter instances have no access to the Environment [#&#8203;50303](https://redirect.github.com/spring-projects/spring-boot/pull/50303)
- NullPointerException in reactor-netty SniProvider when SSL bundle uses client-auth or server truststore without server-name-bundles [#&#8203;50301](https://redirect.github.com/spring-projects/spring-boot/pull/50301)
- Spring Boot Loader Does Not Support RSA and EC Signed Jars [#&#8203;50292](https://redirect.github.com/spring-projects/spring-boot/issues/50292)
- ConfigurationPropertiesReportEndpoint exposes AOP proxy internals [#&#8203;50273](https://redirect.github.com/spring-projects/spring-boot/pull/50273)
- Actuator's '/cloudfoundryapplication' endpoint does not work if restrictive CORS configuration is provided using a bean named corsConfigurationSource [#&#8203;50254](https://redirect.github.com/spring-projects/spring-boot/issues/50254)
- Meter registries are not removed from the global registry when the context is closed [#&#8203;50235](https://redirect.github.com/spring-projects/spring-boot/pull/50235)
- ThreadPoolTaskScheduleBuilder unnecessarily loses precision when configuring await termination time [#&#8203;50225](https://redirect.github.com/spring-projects/spring-boot/pull/50225)
- Apply HTML escaping to timestamp attribute in Whitelabel error page [#&#8203;50205](https://redirect.github.com/spring-projects/spring-boot/pull/50205)
- NimbusJwtDecoder silently accepts unknown values for spring.security.oauth2.resourceserver.jwt.jws-algorithms [#&#8203;50118](https://redirect.github.com/spring-projects/spring-boot/pull/50118)
- EndpointRequest links matcher unnecessarily matches HTTP methods other than GET  [#&#8203;50095](https://redirect.github.com/spring-projects/spring-boot/pull/50095)

##### :notebook\_with\_decorative\_cover: Documentation

- Fix reference to Gradle documentation for module replacement [#&#8203;50641](https://redirect.github.com/spring-projects/spring-boot/pull/50641)
- Remove the use of Optional from Data Neo4j repository examples [#&#8203;50600](https://redirect.github.com/spring-projects/spring-boot/pull/50600)
- Fix typos in documentation [#&#8203;50593](https://redirect.github.com/spring-projects/spring-boot/pull/50593)
- Document Java 25 requirement for AOT cache [#&#8203;50482](https://redirect.github.com/spring-projects/spring-boot/pull/50482)
- Clarify dependency requirement for Bean Validation support [#&#8203;50290](https://redirect.github.com/spring-projects/spring-boot/pull/50290)
- Document SSL reloading with Let's Encrypt [#&#8203;50222](https://redirect.github.com/spring-projects/spring-boot/pull/50222)
- Polish InvalidConfigurationPropertyValueException constructor javadoc [#&#8203;50212](https://redirect.github.com/spring-projects/spring-boot/issues/50212)
- Document known testcontainers lifecycle issues  [#&#8203;50210](https://redirect.github.com/spring-projects/spring-boot/pull/50210)
- Document configuring multiple connectors with Jetty [#&#8203;50206](https://redirect.github.com/spring-projects/spring-boot/pull/50206)
- Fix typo in Spring Security OAuth2 client registration documentation [#&#8203;50193](https://redirect.github.com/spring-projects/spring-boot/pull/50193)

##### :hammer: Dependency Upgrades

- Upgrade to [Caffeine 3.2.4](https://redirect.github.com/ben-manes/caffeine/releases/tag/v3.2.4) [#&#8203;50308](https://redirect.github.com/spring-projects/spring-boot/issues/50308)
- Upgrade to Cassandra Driver 4.19.3 [#&#8203;50670](https://redirect.github.com/spring-projects/spring-boot/issues/50670)
- Upgrade to Glassfish JAXB 4.0.9 [#&#8203;50671](https://redirect.github.com/spring-projects/spring-boot/issues/50671)
- Upgrade to Groovy 4.0.32 [#&#8203;50310](https://redirect.github.com/spring-projects/spring-boot/issues/50310)
- Upgrade to Hibernate 6.6.53.Final [#&#8203;50721](https://redirect.github.com/spring-projects/spring-boot/issues/50721)
- Upgrade to Jackson Bom 2.21.4 [#&#8203;50673](https://redirect.github.com/spring-projects/spring-boot/issues/50673)
- Upgrade to Jakarta Json Bind 3.0.2 [#&#8203;50674](https://redirect.github.com/spring-projects/spring-boot/issues/50674)
- Upgrade to [Jakarta XML Bind 4.0.5](https://redirect.github.com/jakartaee/jaxb-api/releases/tag/4.0.5) [#&#8203;50313](https://redirect.github.com/spring-projects/spring-boot/issues/50313)
- Upgrade to Jaxen 2.0.6 [#&#8203;50722](https://redirect.github.com/spring-projects/spring-boot/issues/50722)
- Upgrade to Jetty 12.0.36 [#&#8203;50676](https://redirect.github.com/spring-projects/spring-boot/issues/50676)
- Upgrade to Jetty Reactive HTTPClient 4.0.14 [#&#8203;50723](https://redirect.github.com/spring-projects/spring-boot/issues/50723)
- Upgrade to jOOQ 3.19.35 [#&#8203;50724](https://redirect.github.com/spring-projects/spring-boot/issues/50724)
- Upgrade to Logback 1.5.34 [#&#8203;50677](https://redirect.github.com/spring-projects/spring-boot/issues/50677)
- Upgrade to [Maven Failsafe Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50678](https://redirect.github.com/spring-projects/spring-boot/issues/50678)
- Upgrade to [Maven Surefire Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50679](https://redirect.github.com/spring-projects/spring-boot/issues/50679)
- Upgrade to [Micrometer 1.15.12](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.15.12) [#&#8203;50511](https://redirect.github.com/spring-projects/spring-boot/issues/50511)
- Upgrade to [Micrometer Tracing 1.5.12](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.5.12) [#&#8203;50512](https://redirect.github.com/spring-projects/spring-boot/issues/50512)
- Upgrade to Netty 4.1.135.Final [#&#8203;50680](https://redirect.github.com/spring-projects/spring-boot/issues/50680)
- Upgrade to [Postgresql 42.7.11](https://redirect.github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.11) [#&#8203;50317](https://redirect.github.com/spring-projects/spring-boot/issues/50317)
- Upgrade to Pulsar 4.0.11 [#&#8203;50725](https://redirect.github.com/spring-projects/spring-boot/issues/50725)
- Upgrade to [R2DBC MySQL 1.4.2](https://redirect.github.com/asyncer-io/r2dbc-mysql/releases/tag/r2dbc-mysql-1.4.2) [#&#8203;50319](https://redirect.github.com/spring-projects/spring-boot/issues/50319)
- Upgrade to [Reactor Bom 2024.0.18](https://redirect.github.com/reactor/reactor/releases/tag/2024.0.18) [#&#8203;50513](https://redirect.github.com/spring-projects/spring-boot/issues/50513)
- Upgrade to SAAJ Impl 3.0.6 [#&#8203;50726](https://redirect.github.com/spring-projects/spring-boot/issues/50726)
- Upgrade to SLF4J 2.0.18 [#&#8203;50533](https://redirect.github.com/spring-projects/spring-boot/issues/50533)
- Upgrade to [Spring AMQP 3.2.11](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v3.2.11) [#&#8203;50514](https://redirect.github.com/spring-projects/spring-boot/issues/50514)
- Upgrade to [Spring Authorization Server 1.5.8](https://redirect.github.com/spring-projects/spring-authorization-server/releases/tag/1.5.8) [#&#8203;50515](https://redirect.github.com/spring-projects/spring-boot/issues/50515)
- Upgrade to [Spring Batch 5.2.6](https://redirect.github.com/spring-projects/spring-batch/releases/tag/v5.2.6) [#&#8203;50516](https://redirect.github.com/spring-projects/spring-boot/issues/50516)
- Upgrade to [Spring Data Bom 2025.0.12](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.0.12) [#&#8203;50517](https://redirect.github.com/spring-projects/spring-boot/issues/50517)
- Upgrade to [Spring Framework 6.2.19](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v6.2.19) [#&#8203;50518](https://redirect.github.com/spring-projects/spring-boot/issues/50518)
- Upgrade to [Spring GraphQL 1.4.6](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v1.4.6) [#&#8203;50739](https://redirect.github.com/spring-projects/spring-boot/issues/50739)
- Upgrade to [Spring HATEOAS 2.5.3](https://redirect.github.com/spring-projects/spring-hateoas/releases/tag/2.5.3) [#&#8203;50519](https://redirect.github.com/spring-projects/spring-boot/issues/50519)
- Upgrade to [Spring Integration 6.5.9](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v6.5.9) [#&#8203;50520](https://redirect.github.com/spring-projects/spring-boot/issues/50520)
- Upgrade to [Spring Kafka 3.3.16](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v3.3.16) [#&#8203;50521](https://redirect.github.com/spring-projects/spring-boot/issues/50521)
- Upgrade to [Spring LDAP 3.3.8](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/3.3.8) [#&#8203;50522](https://redirect.github.com/spring-projects/spring-boot/issues/50522)
- Upgrade to [Spring Pulsar 1.2.18](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v1.2.18) [#&#8203;50523](https://redirect.github.com/spring-projects/spring-boot/issues/50523)
- Upgrade to [Spring RESTDocs 3.0.6](https://redirect.github.com/spring-projects/spring-restdocs/releases/tag/v3.0.6) [#&#8203;50524](https://redirect.github.com/spring-projects/spring-boot/issues/50524)
- Upgrade to [Spring Retry 2.0.13](https://redirect.github.com/spring-projects/spring-retry/releases/tag/v2.0.13) [#&#8203;50525](https://redirect.github.com/spring-projects/spring-boot/issues/50525)
- Upgrade to [Spring Security 6.5.11](https://redirect.github.com/spring-projects/spring-security/releases/tag/6.5.11) [#&#8203;50526](https://redirect.github.com/spring-projects/spring-boot/issues/50526)
- Upgrade to [Spring Session 3.5.7](https://redirect.github.com/spring-projects/spring-session/releases/tag/3.5.7) [#&#8203;50527](https://redirect.github.com/spring-projects/spring-boot/issues/50527)
- Upgrade to [Spring WS 4.1.4](https://redirect.github.com/spring-projects/spring-ws/releases/tag/v4.1.4) [#&#8203;50528](https://redirect.github.com/spring-projects/spring-boot/issues/50528)
- Upgrade to [Tomcat 10.1.55](https://tomcat.apache.org/tomcat-10.1-doc/changelog.html) [#&#8203;50534](https://redirect.github.com/spring-projects/spring-boot/issues/50534)

##### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;Abdlatif-nabgha](https://redirect.github.com/Abdlatif-nabgha), [@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;Kapil-chn7](https://redirect.github.com/Kapil-chn7), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;ares333](https://redirect.github.com/ares333), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;henriquejsza](https://redirect.github.com/henriquejsza), [@&#8203;igormukhin](https://redirect.github.com/igormukhin), [@&#8203;johnnypwong](https://redirect.github.com/johnnypwong), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;leestana01](https://redirect.github.com/leestana01), [@&#8203;mheath](https://redirect.github.com/mheath), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;vinhhieu21](https://redirect.github.com/vinhhieu21), [@&#8203;won-seoop](https://redirect.github.com/won-seoop), and [@&#8203;zxuhan](https://redirect.github.com/zxuhan)

### [`v3.5.14`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.14)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.13...v3.5.14)

##### :lady\_beetle: Bug Fixes

- ApplicationPidFileWriter does not handle symlinks correctly [#&#8203;50173](https://redirect.github.com/spring-projects/spring-boot/issues/50173)
- RandomValuePropertySource is not suitable for secrets [#&#8203;50172](https://redirect.github.com/spring-projects/spring-boot/issues/50172)
- Cassandra auto-configuration misconfigures CqlSessionBuilder [#&#8203;50171](https://redirect.github.com/spring-projects/spring-boot/issues/50171)
- ApplicationTemp does not handle symlinks correctly [#&#8203;50170](https://redirect.github.com/spring-projects/spring-boot/issues/50170)
- Remote DevTools performs comparison incorrectly [#&#8203;50169](https://redirect.github.com/spring-projects/spring-boot/issues/50169)
- spring.rabbitmq.ssl.verify-hostname is applied inconsistently [#&#8203;50168](https://redirect.github.com/spring-projects/spring-boot/issues/50168)
- EnversRevisionRepositoriesRegistrar should reuse `@EnableEnversRepositories` rather than configuring the JPA counterpart [#&#8203;50035](https://redirect.github.com/spring-projects/spring-boot/pull/50035)
- Annotations like `@Ssl` don't work on `@Bean` methods when using `@ServiceConnection` [#&#8203;50033](https://redirect.github.com/spring-projects/spring-boot/pull/50033)
- Whole number values are ignored when configuring min and max expected values and SLO boundaries for a distribution summary meter [#&#8203;50021](https://redirect.github.com/spring-projects/spring-boot/issues/50021)
- WebFlux Cloud Foundry links endpoint includes query string from received request in resolved links [#&#8203;50008](https://redirect.github.com/spring-projects/spring-boot/pull/50008)
- 500 response from env endpoint when supplied pattern is invalid [#&#8203;49942](https://redirect.github.com/spring-projects/spring-boot/pull/49942)
- HTTP method is lost when configuring excludes in EndpointRequest [#&#8203;49885](https://redirect.github.com/spring-projects/spring-boot/pull/49885)
- Docker Compose support doesn't work with apache/artemis image [#&#8203;49865](https://redirect.github.com/spring-projects/spring-boot/issues/49865)
- Honor HttpMethod for reactive additional endpoint paths [#&#8203;49864](https://redirect.github.com/spring-projects/spring-boot/pull/49864)
- Docker Compose support doesn't work with apache/activemq image [#&#8203;49863](https://redirect.github.com/spring-projects/spring-boot/issues/49863)
- Imports on a containing test class are ignored when a nested class has imports [#&#8203;49860](https://redirect.github.com/spring-projects/spring-boot/issues/49860)

##### :notebook\_with\_decorative\_cover: Documentation

- Link to the observability section of the Lettuce documentation is broken [#&#8203;50092](https://redirect.github.com/spring-projects/spring-boot/pull/50092)
- Javadoc for StaticResourceLocation.FAVICON doesn't describe icons location [#&#8203;50083](https://redirect.github.com/spring-projects/spring-boot/issues/50083)
- MySamlRelyingPartyConfiguration is missing a Kotlin sample [#&#8203;50023](https://redirect.github.com/spring-projects/spring-boot/issues/50023)
- Incorrect default value for management.httpexchanges.recording.include in configuration metadata [#&#8203;50010](https://redirect.github.com/spring-projects/spring-boot/pull/50010)
- Link to the Kubernetes documentation when discussing startup probes [#&#8203;50007](https://redirect.github.com/spring-projects/spring-boot/issues/50007)
- Update docs to encourage Java fundamentals for beginners that prefer to learn that way [#&#8203;49895](https://redirect.github.com/spring-projects/spring-boot/issues/49895)
- Clarify that configuration property default values are not available through the Environment [#&#8203;49835](https://redirect.github.com/spring-projects/spring-boot/pull/49835)

##### :hammer: Dependency Upgrades

- Upgrade to Groovy 4.0.31 [#&#8203;49905](https://redirect.github.com/spring-projects/spring-boot/issues/49905)
- Upgrade to Hibernate 6.6.49.Final [#&#8203;50140](https://redirect.github.com/spring-projects/spring-boot/issues/50140)
- Upgrade to [Jaxen 2.0.1](https://redirect.github.com/jaxen-xpath/jaxen/releases/tag/v2.0.1) [#&#8203;50109](https://redirect.github.com/spring-projects/spring-boot/issues/50109)
- Upgrade to [Jaybird 6.0.5](https://redirect.github.com/FirebirdSQL/jaybird/releases/tag/v6.0.5) [#&#8203;49907](https://redirect.github.com/spring-projects/spring-boot/issues/49907)
- Upgrade to [Jetty 12.0.34](https://redirect.github.com/jetty/jetty.project/releases/tag/jetty-12.0.34) [#&#8203;49908](https://redirect.github.com/spring-projects/spring-boot/issues/49908)
- Upgrade to [jOOQ 3.19.32](https://redirect.github.com/jOOQ/jOOQ/releases/tag/version-3.19.32) [#&#8203;50110](https://redirect.github.com/spring-projects/spring-boot/issues/50110)
- Upgrade to Lombok 1.18.46 [#&#8203;50148](https://redirect.github.com/spring-projects/spring-boot/issues/50148)
- Upgrade to [MariaDB 3.5.8](https://mariadb.com/kb/en/mariadb-connector-j-3-5-8-release-notes) [#&#8203;49909](https://redirect.github.com/spring-projects/spring-boot/issues/49909)
- Upgrade to [Micrometer 1.15.11](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.15.11) [#&#8203;49961](https://redirect.github.com/spring-projects/spring-boot/issues/49961)
- Upgrade to [Micrometer Tracing 1.5.11](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.5.11) [#&#8203;49962](https://redirect.github.com/spring-projects/spring-boot/issues/49962)
- Upgrade to [MySQL 9.7.0](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html) [#&#8203;50161](https://redirect.github.com/spring-projects/spring-boot/issues/50161)
- Upgrade to Neo4j Java Driver 5.28.13 [#&#8203;50074](https://redirect.github.com/spring-projects/spring-boot/issues/50074)
- Upgrade to [Reactor Bom 2024.0.17](https://redirect.github.com/reactor/reactor/releases/tag/2024.0.17) [#&#8203;49963](https://redirect.github.com/spring-projects/spring-boot/issues/49963)
- Upgrade to [Spring AMQP 3.2.10](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v3.2.10) [#&#8203;49964](https://redirect.github.com/spring-projects/spring-boot/issues/49964)
- Upgrade to [Spring Authorization Server 1.5.7](https://redirect.github.com/spring-projects/spring-authorization-server/releases/tag/1.5.7) [#&#8203;49965](https://redirect.github.com/spring-projects/spring-boot/issues/49965)
- Upgrade to [Spring Data Bom 2025.0.11](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.0.11) [#&#8203;49966](https://redirect.github.com/spring-projects/spring-boot/issues/49966)
- Upgrade to [Spring Framework 6.2.18](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v6.2.18) [#&#8203;49967](https://redirect.github.com/spring-projects/spring-boot/issues/49967)
- Upgrade to [Spring Kafka 3.3.15](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v3.3.15) [#&#8203;50129](https://redirect.github.com/spring-projects/spring-boot/issues/50129)
- Upgrade to [Spring LDAP 3.3.7](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/3.3.7) [#&#8203;49968](https://redirect.github.com/spring-projects/spring-boot/issues/49968)
- Upgrade to [Spring Pulsar 1.2.17](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v1.2.17) [#&#8203;49969](https://redirect.github.com/spring-projects/spring-boot/issues/49969)
- Upgrade to [Spring Security 6.5.10](https://redirect.github.com/spring-projects/spring-security/releases/tag/6.5.10) [#&#8203;49970](https://redirect.github.com/spring-projects/spring-boot/issues/49970)
- Upgrade to [Spring Session 3.5.6](https://redirect.github.com/spring-projects/spring-session/releases/tag/3.5.6) [#&#8203;49971](https://redirect.github.com/spring-projects/spring-boot/issues/49971)
- Upgrade to Thymeleaf 3.1.5.RELEASE [#&#8203;50149](https://redirect.github.com/spring-projects/spring-boot/issues/50149)
- Upgrade to Thymeleaf Extras SpringSecurity 3.1.5.RELEASE [#&#8203;50151](https://redirect.github.com/spring-projects/spring-boot/issues/50151)
- Upgrade to [Tomcat 10.1.54](https://tomcat.apache.org/tomcat-10.1-doc/changelog.html) [#&#8203;49910](https://redirect.github.com/spring-projects/spring-boot/issues/49910)

##### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;MohammedGhallab](https://redirect.github.com/MohammedGhallab), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;edwardsre](https://redirect.github.com/edwardsre), [@&#8203;kodama-kcc](https://redirect.github.com/kodama-kcc), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;refeccd](https://redirect.github.com/refeccd), and [@&#8203;scordio](https://redirect.github.com/scordio)

### [`v3.5.13`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.13)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.12...v3.5.13)

#### :warning: Attention Required

- Jackson has been upgraded to 2.21.2 in response to the Jackson team ending support for Jackson 2.19.x and 2.20.x. [#&#8203;49365](https://redirect.github.com/spring-projects/spring-boot/issues/49365)

#### :lady\_beetle: Bug Fixes

- WebSocket messaging's task executors are only auto-configured and stompWebSocketHandlerMapping is only forced to be eager when using Jackson [#&#8203;49750](https://redirect.github.com/spring-projects/spring-boot/issues/49750)
- Metadata annotation processor ignores method-level `@NestedConfigurationProperty` when using constructor binding [#&#8203;49734](https://redirect.github.com/spring-projects/spring-boot/issues/49734)
- Override of property in external 'application.properties' or 'application.yaml' is ignored [#&#8203;49724](https://redirect.github.com/spring-projects/spring-boot/issues/49724)
- Some sliced tests that import TransactionAutoConfiguration do not import TransactionManagerCustomizationAutoConfiguration [#&#8203;49716](https://redirect.github.com/spring-projects/spring-boot/issues/49716)
- NativeImageResourceProvider does not find Flyway migration scripts in subdirectories [#&#8203;49661](https://redirect.github.com/spring-projects/spring-boot/issues/49661)
- `@GraphQlTest` does not include `@ControllerAdvice` [#&#8203;49660](https://redirect.github.com/spring-projects/spring-boot/pull/49660)

#### :notebook\_with\_decorative\_cover: Documentation

- Fix incorrect indefinite articles in Javadoc [#&#8203;49723](https://redirect.github.com/spring-projects/spring-boot/pull/49723)
- Add some more Kotlin examples and trivial style fixes [#&#8203;49710](https://redirect.github.com/spring-projects/spring-boot/pull/49710)

#### :hammer: Dependency Upgrades

- Upgrade to Hibernate 6.6.45.Final [#&#8203;49757](https://redirect.github.com/spring-projects/spring-boot/issues/49757)
- Upgrade to jOOQ 3.19.31 [#&#8203;49758](https://redirect.github.com/spring-projects/spring-boot/issues/49758)
- Upgrade to Netty 4.1.132.Final [#&#8203;49759](https://redirect.github.com/spring-projects/spring-boot/issues/49759)
- Upgrade to Tomcat 10.1.53 [#&#8203;49760](https://redirect.github.com/spring-projects/spring-boot/issues/49760)
- Upgrade to Undertow 2.3.24.Final [#&#8203;49761](https://redirect.github.com/spring-projects/spring-boot/issues/49761)
- Upgrade to Zipkin Reporter 3.5.3 [#&#8203;49756](https://redirect.github.com/spring-projects/spring-boot/issues/49756)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;Joowon-Seo](https://redirect.github.com/Joowon-Seo), [@&#8203;deejay1](https://redirect.github.com/deejay1), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), and [@&#8203;ljrmorgan](https://redirect.github.com/ljrmorgan)

### [`v3.5.12`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.12)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.11...v3.5.12)

#### :lady\_beetle: Bug Fixes

- EndpointRequest request matcher for health groups is too complex [#&#8203;49648](https://redirect.github.com/spring-projects/spring-boot/issues/49648)
- "/cloudfoundryapplication" web path is not limited to Actuator [#&#8203;49645](https://redirect.github.com/spring-projects/spring-boot/issues/49645)
- RSocket exposes duplicate endpoint for websocket setups [#&#8203;49592](https://redirect.github.com/spring-projects/spring-boot/issues/49592)
- Fix EndpointRequest.toLinks() when base-path is '/' [#&#8203;49591](https://redirect.github.com/spring-projects/spring-boot/pull/49591)
- SpringBootContextLoader mentions class that no longer exists in message for classes or locations assertion [#&#8203;49518](https://redirect.github.com/spring-projects/spring-boot/issues/49518)
- "spring.main.cloud-platform=none" does not disable cloud features [#&#8203;49478](https://redirect.github.com/spring-projects/spring-boot/issues/49478)
- Using `@AutoConfigureWebTestClient` prevents separate configuration of spring.test.webtestclient.timeout from taking effect [#&#8203;49340](https://redirect.github.com/spring-projects/spring-boot/pull/49340)
- Ordering of 'spring.config.import' is inconsistent when defined in environment or system properties [#&#8203;49324](https://redirect.github.com/spring-projects/spring-boot/pull/49324)
- RouterFunctions descriptions in Actuator do not support nesting [#&#8203;49289](https://redirect.github.com/spring-projects/spring-boot/issues/49289)
- Maven plugin does not set '-parameters' option when processing AOT code [#&#8203;49268](https://redirect.github.com/spring-projects/spring-boot/issues/49268)
- SSL support with Docker Compose does not work as documented [#&#8203;49210](https://redirect.github.com/spring-projects/spring-boot/pull/49210)
- Docker fails when a 'tcp\://' address ends with a slash (for example 'tcp\://docker:2375/') [#&#8203;49055](https://redirect.github.com/spring-projects/spring-boot/issues/49055)

#### :notebook\_with\_decorative\_cover: Documentation

- List all supported colors when describing color-coded log output [#&#8203;49561](https://redirect.github.com/spring-projects/spring-boot/issues/49561)
- Clarify that running is the only supported input state when triggering a Quartz job through the Actuator endpoint [#&#8203;49506](https://redirect.github.com/spring-projects/spring-boot/issues/49506)
- Tutorial in the reference guide has outdated instructions [#&#8203;49411](https://redirect.github.com/spring-projects/spring-boot/issues/49411)
- Javadoc of JettyHttpClientBuilder refers to the wrong type [#&#8203;49364](https://redirect.github.com/spring-projects/spring-boot/pull/49364)
- Example spring-devtools.properties file is shown in the wrong format [#&#8203;49357](https://redirect.github.com/spring-projects/spring-boot/pull/49357)
- Mention using org.springframework.boot.aot Gradle plugin directly for AOT processing with the JVM [#&#8203;49307](https://redirect.github.com/spring-projects/spring-boot/issues/49307)
- Update CLI's INSTALL.txt to reflect Groovy no longer being bundled [#&#8203;49297](https://redirect.github.com/spring-projects/spring-boot/issues/49297)
- JDK requirement for the CLI still refers to Java 8 [#&#8203;49290](https://redirect.github.com/spring-projects/spring-boot/pull/49290)
- Java and Kotlin samples of an environment post processor are inconsistent [#&#8203;49282](https://redirect.github.com/spring-projects/spring-boot/pull/49282)
- Document additional repositories required for shibboleth.net [#&#8203;49260](https://redirect.github.com/spring-projects/spring-boot/issues/49260)
- Clarify inferred relationships between OAuth 2 registrations and providers [#&#8203;49240](https://redirect.github.com/spring-projects/spring-boot/issues/49240)

#### :hammer: Dependency Upgrades

- Upgrade to DB2 JDBC 12.1.4.0 [#&#8203;49544](https://redirect.github.com/spring-projects/spring-boot/issues/49544)
- Upgrade to Hibernate 6.6.44.Final [#&#8203;49457](https://redirect.github.com/spring-projects/spring-boot/issues/49457)
- Upgrade to Jakarta XML WS 4.0.3 [#&#8203;49458](https://redirect.github.com/spring-projects/spring-boot/issues/49458)
- Upgrade to JBoss Logging 3.6.3.Final [#&#8203;49630](https://redirect.github.com/spring-projects/spring-boot/issues/49630)
- Upgrade to Jetty 12.0.33 [#&#8203;49459](https://redirect.github.com/spring-projects/spring-boot/issues/49459)
- Upgrade to Kafka 3.9.2 [#&#8203;49460](https://redirect.github.com/spring-projects/spring-boot/issues/49460)
- Upgrade to Lombok 1.18.44 [#&#8203;49574](https://redirect.github.com/spring-projects/spring-boot/issues/49574)
- Upgrade to Maven Failsafe Plugin 3.5.5 [#&#8203;49461](https://redirect.github.com/spring-projects/spring-boot/issues/49461)
- Upgrade to Maven Shade Plugin 3.6.2 [#&#8203;49462](https://redirect.github.com/spring-projects/spring-boot/issues/49462)
- Upgrade to Maven Surefire Plugin 3.5.5 [#&#8203;49463](https://redirect.github.com/spring-projects/spring-boot/issues/49463)
- Upgrade to Micrometer 1.15.10 [#&#8203;49403](https://redirect.github.com/spring-projects/spring-boot/issues/49403)
- Upgrade to Micrometer Tracing 1.5.10 [#&#8203;49404](https://redirect.github.com/spring-projects/spring-boot/issues/49404)
- Upgrade to Pulsar 4.0.9 [#&#8203;49464](https://redirect.github.com/spring-projects/spring-boot/issues/49464)
- Upgrade to Reactor Bom 2024.0.16 [#&#8203;49405](https://redirect.github.com/spring-projects/spring-boot/issues/49405)
- Upgrade to Spring Batch 5.2.5 [#&#8203;49406](https://redirect.github.com/spring-projects/spring-boot/issues/49406)
- Upgrade to Spring Data Bom 2025.0.10 [#&#8203;49407](https://redirect.github.com/spring-projects/spring-boot/issues/49407)
- Upgrade to Spring Framework 6.2.17 [#&#8203;49408](https://redirect.github.com/spring-projects/spring-boot/issues/49408)
- Upgrade to Spring HATEOAS 2.5.2 [#&#8203;49586](https://redirect.github.com/spring-projects/spring-boot/issues/49586)
- Upgrade to Spring Integration 6.5.8 [#&#8203;49631](https://redirect.github.com/spring-projects/spring-boot/issues/49631)
- Upgrade to Spring Kafka 3.3.14 [#&#8203;49409](https://redirect.github.com/spring-projects/spring-boot/issues/49409)
- Upgrade to Spring Pulsar 1.2.16 [#&#8203;49410](https://redirect.github.com/spring-projects/spring-boot/issues/49410)
- Upgrade to Spring Security 6.5.9 [#&#8203;49527](https://redirect.github.com/spring-projects/spring-boot/issues/49527)
- Upgrade to Spring WS 4.1.3 [#&#8203;49528](https://redirect.github.com/spring-projects/spring-boot/issues/49528)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;bbbbooo](https://redirect.github.com/bbbbooo), [@&#8203;chandanv89](https://redirect.github.com/chandanv89), [@&#8203;itsmevichu](https://redirect.github.com/itsmevichu), [@&#8203;jayychoi](https://redirect.github.com/jayychoi), [@&#8203;l2yujw](https://redirect.github.com/l2yujw), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;qnnn](https://redirect.github.com/qnnn), [@&#8203;quaff](https://redirect.github.com/quaff), and [@&#8203;sbrannen](https://redirect.github.com/sbrannen)

### [`v3.5.11`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.11)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.10...v3.5.11)

#### :lady\_beetle: Bug Fixes

- Whitespace can be incorrectly removed when spring-boot-configuration-processor runs on multi-line javadoc [#&#8203;49039](https://redirect.github.com/spring-projects/spring-boot/pull/49039)
- server.jetty.threads.max is ignored when using virtual threads [#&#8203;48982](https://redirect.github.com/spring-projects/spring-boot/issues/48982)
- Docker credential helpers with file extensions cannot be executed on Windows [#&#8203;48965](https://redirect.github.com/spring-projects/spring-boot/pull/48965)

#### :notebook\_with\_decorative\_cover: Documentation

- Couchbase and Kafka are incorrectly listed as supporting SSL with Docker Compose [#&#8203;49211](https://redirect.github.com/spring-projects/spring-boot/issues/49211)
- Document that use of non idiomatic format for '`@Value`' still apply for environment variables [#&#8203;49054](https://redirect.github.com/spring-projects/spring-boot/pull/49054)
- Document naming convention for custom test-scoped starters [#&#8203;49014](https://redirect.github.com/spring-projects/spring-boot/pull/49014)
- LICENSE.txt and NOTICE.txt files have the wrong content in the latest releases [#&#8203;48996](https://redirect.github.com/spring-projects/spring-boot/issues/48996)
- ApplicationContextAssert documents a non-existent assertion in getFailure() [#&#8203;48973](https://redirect.github.com/spring-projects/spring-boot/pull/48973)
- Highlight the importance of the preStop hook when configuring Kubernetes probes [#&#8203;48936](https://redirect.github.com/spring-projects/spring-boot/pull/48936)

#### :hammer: Dependency Upgrades

- Upgrade to AssertJ 3.27.7 [#&#8203;49075](https://redirect.github.com/spring-projects/spring-boot/issues/49075)
- Upgrade to Groovy 4.0.30 [#&#8203;49076](https://redirect.github.com/spring-projects/spring-boot/issues/49076)
- Upgrade to Hibernate 6.6.42.Final [#&#8203;49077](https://redirect.github.com/spring-projects/spring-boot/issues/49077)
- Upgrade to Jaybird 6.0.4 [#&#8203;49078](https://redirect.github.com/spring-projects/spring-boot/issues/49078)
- Upgrade to JBoss Logging 3.6.2.Final [#&#8203;49079](https://redirect.github.com/spring-projects/spring-boot/issues/49079)
- Upgrade to Jetty 12.0.32 [#&#8203;49080](https://redirect.github.com/spring-projects/spring-boot/issues/49080)
- Upgrade to jOOQ 3.19.30 [#&#8203;49081](https://redirect.github.com/spring-projects/spring-boot/issues/49081)
- Upgrade to Logback 1.5.32 [#&#8203;49243](https://redirect.github.com/spring-projects/spring-boot/issues/49243)
- Upgrade to Micrometer 1.15.9 [#&#8203;49064](https://redirect.github.com/spring-projects/spring-boot/issues/49064)
- Upgrade to Micrometer Tracing 1.5.9 [#&#8203;49065](https://redirect.github.com/spring-projects/spring-boot/issues/49065)
- Upgrade to MySQL 9.6.0 [#&#8203;49083](https://redirect.github.com/spring-projects/spring-boot/issues/49083)
- Upgrade to Netty 4.1.131.Final [#&#8203;49165](https://redirect.github.com/spring-projects/spring-boot/issues/49165)
- Upgrade to Postgresql 42.7.10 [#&#8203;49201](https://redirect.github.com/spring-projects/spring-boot/issues/49201)
- Upgrade to Reactor Bom 2024.0.15 [#&#8203;49066](https://redirect.github.com/spring-projects/spring-boot/issues/49066)
- Upgrade to Spring Authorization Server 1.5.6 [#&#8203;49067](https://redirect.github.com/spring-projects/spring-boot/issues/49067)
- Upgrade to Spring Data Bom 2025.0.9 [#&#8203;49068](https://redirect.github.com/spring-projects/spring-boot/issues/49068)
- Upgrade to Spring Framework 6.2.16 [#&#8203;49069](https://redirect.github.com/spring-projects/spring-boot/issues/49069)
- Upgrade to Spring GraphQL 1.4.5 [#&#8203;49070](https://redirect.github.com/spring-projects/spring-boot/issues/49070)
- Upgrade to Spring Integration 6.5.7 [#&#8203;49071](https://redirect.github.com/spring-projects/spring-boot/issues/49071)
- Upgrade to Spring Kafka 3.3.13 [#&#8203;49244](https://redirect.github.com/spring-projects/spring-boot/issues/49244)
- Upgrade to Spring LDAP 3.3.6 [#&#8203;49072](https://redirect.github.com/spring-projects/spring-boot/issues/49072)
- Upgrade to Spring Pulsar 1.2.15 [#&#8203;49073](https://redirect.github.com/spring-projects/spring-boot/issues/49073)
- Upgrade to Spring Security 6.5.8 [#&#8203;49225](https://redirect.github.com/spring-projects/spring-boot/issues/49225)
- Upgrade to Spring Session 3.5.5 [#&#8203;49074](https://redirect.github.com/spring-projects/spring-boot/issues/49074)
- Upgrade to Tomcat 10.1.52 [#&#8203;49084](https://redirect.github.com/spring-projects/spring-boot/issues/49084)
- Upgrade to Undertow 2.3.23.Final [#&#8203;49166](https://redirect.github.com/spring-projects/spring-boot/issues/49166)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;dsyer](https://redirect.github.com/dsyer), [@&#8203;linkian209](https://redirect.github.com/linkian209), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), and [@&#8203;srt](https://redirect.github.com/srt)

### [`v3.5.10`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.10)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.9...v3.5.10)

#### :lady\_beetle: Bug Fixes

- Evaluation of bean conditions unnecessarily queries the bean factory for types that are not present [#&#8203;48836](https://redirect.github.com/spring-projects/spring-boot/issues/48836)
- When a bean condition references a type that is not present, it appears as ? in the condition evaluation report [#&#8203;48835](https://redirect.github.com/spring-projects/spring-boot/issues/48835)
- Actuator /info endpoint fails in Java 25 Native Image (VirtualThreadSchedulerMXBean support) [#&#8203;48810](https://redirect.github.com/spring-projects/spring-boot/issues/48810)
- DataSourceBuilder cannot create oracle.ucp.jdbc.PoolDataSourceImpl in a native image [#&#8203;48702](https://redirect.github.com/spring-projects/spring-boot/issues/48702)
- Application JAR created by extract command is not reproductible [#&#8203;48664](https://redirect.github.com/spring-projects/spring-boot/issues/48664)
- AOT processing of tests should not be disabled when 'skipTests' is set [#&#8203;48661](https://redirect.github.com/spring-projects/spring-boot/issues/48661)
- Fix zero-length byte buffer in InspectedContent [#&#8203;48649](https://redirect.github.com/spring-projects/spring-boot/pull/48649)

#### :notebook\_with\_decorative\_cover: Documentation

- Update documentation for Buildpack's AOT Cache support [#&#8203;48768](https://redirect.github.com/spring-projects/spring-boot/issues/48768)
- Document support for configuring arguments passed to Docker Compose [#&#8203;48657](https://redirect.github.com/spring-projects/spring-boot/issues/48657)
- Clarify javadoc to make it clear that HazelcastConfigCustomizer beans are only applied if Hazelcast is configured via a config file [#&#8203;48634](https://redirect.github.com/spring-projects/spring-boot/issues/48634)
- Fix grammar and typos in the reference guide [#&#8203;48596](https://redirect.github.com/spring-projects/spring-boot/pull/48596)

#### :hammer: Dependency Upgrades

- Upgrade to Classmate 1.7.3 [#&#8203;48775](https://redirect.github.com/spring-projects/spring-boot/issues/48775)
- Upgrade to Hibernate 6.6.41.Final [#&#8203;48881](https://redirect.github.com/spring-projects/spring-boot/issues/48881)
- Upgrade to HttpClient5 5.5.2 [#&#8203;48777](https://redirect.github.com/spring-projects/spring-boot/issues/48777)
- Upgrade to Logback 1.5.25 [#&#8203;48882](https://redirect.github.com/spring-projects/spring-boot/issues/48882)
- Upgrade to Micrometer 1.15.8 [#&#8203;48705](https://redirect.github.com/spring-projects/spring-boot/issues/48705)
- Upgrade to Micrometer Tracing 1.5.8 [#&#8203;48706](https://redirect.github.com/spring-projects/spring-boot/issues/48706)
- Upgrade to Pooled JMS 3.1.9 [#&#8203;48779](https://redirect.github.com/spring-projects/spring-boot/issues/48779)
- Upgrade to Postgresql 42.7.9 [#&#8203;48883](https://redirect.github.com/spring-projects/spring-boot/issues/48883)
- Upgrade to R2DBC MSSQL 1.0.4.RELEASE [#&#8203;48847](https://redirect.github.com/spring-projects/spring-boot/issues/48847)
- Upgrade to Reactor Bom 2024.0.14 [#&#8203;48707](https://redirect.github.com/spring-projects/spring-boot/issues/48707)
- Upgrade to REST Assured 5.5.7 [#&#8203;48884](https://redirect.github.com/spring-projects/spring-boot/issues/48884)
- Upgrade to Spring AMQP 3.2.9 [#&#8203;48909](https://redirect.github.com/spring-projects/spring-boot/issues/48909)
- Upgrade to Spring Data Bom 2025.0.8 [#&#8203;48708](https://redirect.github.com/spring-projects/spring-boot/issues/48708)
- Upgrade to Spring Integration 6.5.6 [#&#8203;48921](https://redirect.github.com/spring-projects/spring-boot/issues/48921)
- Upgrade to Spring Kafka 3.3.12 [#&#8203;48709](https://redirect.github.com/spring-projects/spring-boot/issues/48709)
- Upgrade to Spring Pulsar 1.2.14 [#&#8203;48710](https://redirect.github.com/spring-projects/spring-boot/issues/48710)
- Upgrade to Undertow 2.3.22.Final [#&#8203;48848](https://redirect.github.com/spring-projects/spring-boot/issues/48848)
- Upgrade to WebJars Locator Lite 1.1.3 [#&#8203;48780](https://redirect.github.com/spring-projects/spring-boot/issues/48780)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;GaoSSR](https://redirect.github.com/GaoSSR), [@&#8203;izeye](https://redirect.github.com/izeye), and [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996)

### [`v3.5.9`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.9)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.8...v3.5.9)

#### :lady\_beetle: Bug Fixes

- RabbitHealthIndicator reports an error when version is missing from the connection's server properties [#&#8203;48486](https://redirect.github.com/spring-projects/spring-boot/issues/48486)
- Profiles retained during AOT processing are not configured in a native image [#&#8203;48475](https://redirect.github.com/spring-projects/spring-boot/issues/48475)
- NullPointerException in UndertowWebServer.destroy() when using `@DirtiesContext` and Citrus Spring Boot Simulator [#&#8203;48450](https://redirect.github.com/spring-projects/spring-boot/issues/48450)
- Redis health check reports an error when redis\_version is missing from the INFO response [#&#8203;48326](https://redirect.github.com/spring-projects/spring-boot/issues/48326)
- Parent's MeterRegistry beans are closed when child context closes [#&#8203;48324](https://redirect.github.com/spring-projects/spring-boot/issues/48324)
- SpringBootTest.UseMainMethod.WHEN\_AVAILABLE and ALWAYS are incompatible with package-private or parameter-less main method [#&#8203;48271](https://redirect.github.com/spring-projects/spring-boot/issues/48271)

#### :notebook\_with\_decorative\_cover: Documentation

- Documentation has an outdated reference to the Jackson Kotlin Module [#&#8203;48533](https://redirect.github.com/spring-projects/spring-boot/issues/48533)
- Caching documentation should clarify how to use a no-op implementation to run a test suite [#&#8203;48531](https://redirect.github.com/spring-projects/spring-boot/issues/48531)
- Document that the default rolling policy for Log4j2 requires logging.file.path to be set [#&#8203;48526](https://redirect.github.com/spring-projects/spring-boot/issues/48526)
- License header in build samples is displayed in the reference documentation [#&#8203;48477](https://redirect.github.com/spring-projects/spring-boot/issues/48477)
- Configuring Two DataSources How-To code sample is inconsistent [#&#8203;48448](https://redirect.github.com/spring-projects/spring-boot/issues/48448)
- Improve javadoc for when to use class names rather than class references [#&#8203;48395](https://redirect.github.com/spring-projects/spring-boot/issues/48395)
- Document that org.aspectj.weaver.Advice must be on the classpath to enable support for Micrometer's annotations [#&#8203;48359](https://redirect.github.com/spring-projects/spring-boot/issues/48359)
- Polish TestRestTemplate examples in the reference guide [#&#8203;48335](https://redirect.github.com/spring-projects/spring-boot/issues/48335)
- Fix links to javadoc in the reference documentation [#&#8203;48299](https://redirect.github.com/spring-projects/spring-boot/issues/48299)
- Clarify that `@EnableBatchProcessing` turns off all batch auto-configuration, including schema initialization [#&#8203;48265](https://redirect.github.com/spring-projects/spring-boot/issues/48265)
- Kotlin auto-configuration examples are not annotated with `@AutoConfiguration` [#&#8203;48227](https://redirect.github.com/spring-projects/spring-boot/issues/48227)
- Infinispan Cache Documentation is outdated [#&#8203;48217](https://redirect.github.com/spring-projects/spring-boot/issues/48217)
- Revise "Use Liquibase for test-only migrations" section in reference manual [#&#8203;48169](https://redirect.github.com/spring-projects/spring-boot/pull/48169)

#### :hammer: Dependency Upgrades

- Prevent upgrade to Netty 4.1.129.Final [#&#8203;48508](https://redirect.github.com/spring-projects/spring-boot/issues/48508)
- Upgrade to AspectJ 1.9.25.1 [#&#8203;48557](https://redirect.github.com/spring-projects/spring-boot/issues/48557)
- Upgrade to Hibernate 6.6.39.Final [#&#8203;48540](https://redirect.github.com/spring-projects/spring-boot/issues/48540)
- Upgrade to Jetty 12.0.31 [#&#8203;48455](https://redirect.github.com/spring-projects/spring-boot/issues/48455)
- Upgrade to jOOQ 3.19.29 [#&#8203;48456](https://redirect.github.com/spring-projects/spring-boot/issues/48456)
- Upgrade to Logback 1.5.22 [#&#8203;48507](https://redirect.github.com/spring-projects/spring-boot/issues/48507)
- Upgrade to MariaDB 3.5.7 [#&#8203;48558](https://redirect.github.com/spring-projects/spring-boot/issues/48558)
- Upgrade to Micrometer 1.15.7 [#&#8203;48423](https://redirect.github.com/spring-projects/spring-boot/issues/48423)
- Upgrade to Micrometer Tracing 1.5.7 [#&#8203;48424](https://redirect.github.com/spring-projects/spring-boot/issues/48424)
- Upgrade to Netty 4.1.130.Final [#&#8203;48541](https://redirect.github.com/spring-projects/spring-boot/issues/48541)
- Upgrade to Pooled JMS 3.1.8 [#&#8203;48559](https://redirect.github.com/spring-projects/spring-boot/issues/48559)
- Upgrade to Pulsar 4.0.8 [#&#8203;48457](https://redirect.github.com/spring-projects/spring-boot/issues/48457)
- Upgrade to Quartz 2.5.2 [#&#8203;48458](https://redirect.github.com/spring-projects/spring-boot/issues/48458)
- Upgrade to Reactor Bom 2024.0.13 [#&#8203;48425](https://redirect.github.com/spring-projects/spring-boot/issues/48425)
- Upgrade to Spring Authorization Server 1.5.5 [#&#8203;48426](https://redirect.github.com/spring-projects/spring-boot/issues/48426)
- Upgrade to Spring Data Bom 2025.0.7 [#&#8203;48427](https://redirect.github.com/spring-projects/spring-boot/issues/48427)
- Upgrade to Spring Framework 6.2.15 [#&#8203;48428](https://redirect.github.com/spring-projects/spring-boot/issues/48428)
- Upgrade to Spring GraphQL 1.4.4 [#&#8203;48429](https://redirect.github.com/spring-projects/spring-boot/issues/48429)
- Upgrade to Spring Integration 6.5.5 [#&#8203;48560](https://redirect.github.com/spring-projects/spring-boot/issues/48560)
- Upgrade to Spring LDAP 3.3.5 [#&#8203;48430](https://redirect.github.com/spring-projects/spring-boot/issues/48430)
- Upgrade to Spring Pulsar 1.2.13 [#&#8203;48431](https://redirect.github.com/spring-projects/spring-boot/issues/48431)
- Upgrade to Spring Session 3.5.4 [#&#8203;48432](https://redirect.github.com/spring-projects/spring-boot/issues/48432)
- Upgrade to Testcontainers 1.21.4 [#&#8203;48542](https://redirect.github.com/spring-projects/spring-boot/issues/48542)
- Upgrade to UnboundID LDAPSDK 7.0.4 [#&#8203;48459](https://redirect.github.com/spring-projects/spring-boot/issues/48459)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;banseok1216](https://redirect.github.com/banseok1216), [@&#8203;berry120](https://redirect.github.com/berry120), [@&#8203;dmitrysulman](https://redirect.github.com/dmitrysulman), [@&#8203;geopark021](https://redirect.github.com/geopark021), [@&#8203;noojung](https://redirect.github.com/noojung), [@&#8203;scottfrederick](https://redirect.github.com/scottfrederick), [@&#8203;vpavic](https://redirect.github.com/vpavic), and [@&#8203;youngledo](https://redirect.github.com/youngledo)

### [`v3.5.8`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v3.5.8)

[Compare Source](https://redirect.github.com/spring-projects/spring-boot/compare/v3.5.7...v3.5.8)

#### :warning: Noteworthy changes

- This release [contains a fix](https://redirect.github.com/spring-projects/spring-boot/issues/48104) to get Testcontainers working with modern Docker versions. If this causes problems in your setup, [you can downgrade the minimum Docker API](https://docs.spring.io/spring-boot/3.4/how-to/testing.html#howto.testing.testcontainers-api-downgrade), effectively reverting that change.

#### :lady\_beetle: Bug Fixes

- Gradle war task does not exclude starter POMs from lib-provided [#&#8203;48196](https://redirect.github.com/spring-projects/spring-boot/issues/48196)
- Testcontainers integration fails on Docker 29.0.0 [#&#8203;48192](https://redirect.github.com/spring-projects/spring-boot/issues/48192)
- SslMeterBinder doesn't register metrics for dynamically added bundles if no bundles exist at bind time [#&#8203;48180](https://redirect.github.com/spring-projects/spring-boot/issues/48180)
- Properties bound in the child management context ignore the parent's environment prefix [#&#8203;48176](https://redirect.github.com/spring-projects/spring-boot/issues/48176)
- ssl.chain.expiry metrics doesn't update for dynamically registered SSL bundles [#&#8203;48153](https://redirect.github.com/spring-projects/spring-boot/pull/48153)
- Auto-configuration exclusions are checked using a different class loader to the one that loads auto-configuration classes [#&#8203;48129](https://redirect.github.com/spring-projects/spring-boot/pull/48129)
- New arm64 macbooks fail to bootBuildImage due to incorrect platform image [#&#8203;48127](https://redirect.github.com/spring-projects/spring-boot/issues/48127)
- NullPointerException when using `@ConditionalOnSingleCandidate` with multiple manually registered singletons [#&#8203;48123](https://redirect.github.com/spring-projects/spring-boot/issues/48123)
- Buildpack fails with recent Docker installs due to hardcoded version in URL [#&#8203;48102](https://redirect.github.com/spring-projects/spring-boot/issues/48102)
- Image building may fail when specifying a platform if an image has already been built with a different platform [#&#8203;48098](https://redirect.github.com/spring-projects/spring-boot/issues/48098)
- Undertow's ServletContext is destroy too early, making it unusable in `@PreDestroy` methods [#&#8203;48061](https://redirect.github.com/spring-projects/spring-boot/issues/48061)
- PortInUseException incorrectly thrown on failure to bind port due to Netty IP misconfiguration [#&#8203;48058](https://redirect.github.com/spring-projects/spring-boot/issues/48058)
- Auto-configured JCacheMetrics cannot be customized [#&#8203;48056](https://redirect.github.com/spring-projects/spring-boot/issues/48056)
- WebSecurityCustomizer beans are excluded by WebMvcTest [#&#8203;48054](https://redirect.github.com/spring-projects/spring-boot/issues/48054)
- Devtools Restarter does not work with a parameterless main method [#&#8203;47987](https://redirect.github.com/spring-projects/spring-boot/pull/47987)
- Setting 'max-uri-tags' does not prevent unlimited meter growth on any AutoConfiguredCompositeMeterRegistry [#&#8203;47923](https://redirect.github.com/spring-projects/spring-boot/issues/47923)
- Docker response 407 is not handled correctly resulting in no error message [#&#8203;47900](https://redirect.github.com/spring-projects/spring-boot/issues/47900)
- spring-boot-maven-plugin process-aot goal does not find package-private main method [#&#8203;47780](https://redirect.github.com/spring-projects/spring-boot/issues/47780)

#### :notebook\_with\_decorative\_cover: Documentation

- Revise AWS section of "Deploying to the Cloud" in reference manual [#&#8203;48156](https://redirect.github.com/spring-projects/spring-boot/pull/48156)
- Fix typo in PortInUseException Javadoc [#&#8203;48133](https://redirect.github.com/spring-projects/spring-boot/issues/48133)
- Correct section about required setters in "Type-safe Configuration Properties" [#&#8203;48130](https://redirect.github.com/spring-projects/spring-boot/issues/48130)
- Document EndpointObjectMapper and management.endpoints.jackson.isolated-object-mapper [#&#8203;48114](https://redirect.github.com/spring-projects/spring-boot/issues/48114)
- Document support for configuring servlet context init parameters using properties [#&#8203;48111](https://redirect.github.com/spring-projects/spring-boot/issues/48111)
- Clarify how warnings about soon-to-expire SSL certificates are reported [#&#8203;48062](https://redirect.github.com/spring-projects/spring-boot/issues/48062)
- Document how to use ContextPropagatingTaskDecorator for propagating trace context over thread boundaries [#&#8203;48052](https://redirect.github.com/spring-projects/spring-boot/issues/48052)
- Use since attribute in configuration properties deprecation consistently [#&#8203;47980](https

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

🚦 **Automerge**: Disabled by config. Please merge this manually once you are satisfied.

♻ **Rebasing**: Whenever PR becomes conflicted, or you tick the rebase/retry checkbox.

🔕 **Ignore**: Close this PR and you won't be reminded about these updates again.

---

 - [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box

---

This PR was generated by [Mend Renovate](https://mend.io/renovate/). View the [repository job log](https://developer.mend.io/github/diggsweden/sdg-authorization).
<!--renovate-debug:eyJjcmVhdGVkSW5WZXIiOiI0NC4xMjUuMSIsInVwZGF0ZWRJblZlciI6IjQ0LjEyNS4xIiwidGFyZ2V0QnJhbmNoIjoibWFpbiIsImxhYmVscyI6W119-->

