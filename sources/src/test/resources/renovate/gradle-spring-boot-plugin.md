This PR contains the following updates:

| Package | Change | [Age](https://docs.renovatebot.com/merge-confidence/) | [Confidence](https://docs.renovatebot.com/merge-confidence/) |
|---|---|---|---|
| [org.springframework.boot](https://spring.io/projects/spring-boot) ([source](https://redirect.github.com/spring-projects/spring-boot)) | `4.0.5` → `4.1.1` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.springframework.boot:org.springframework.boot.gradle.plugin/4.1.1?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.springframework.boot:org.springframework.boot.gradle.plugin/4.0.5/4.1.1?slim=true) |
| [org.springframework.boot:spring-boot-gradle-plugin](https://spring.io/projects/spring-boot) ([source](https://redirect.github.com/spring-projects/spring-boot)) | `4.0.5` → `4.1.1` | ![age](https://developer.mend.io/api/mc/badges/age/maven/org.springframework.boot:spring-boot-gradle-plugin/4.1.1?slim=true) | ![confidence](https://developer.mend.io/api/mc/badges/confidence/maven/org.springframework.boot:spring-boot-gradle-plugin/4.0.5/4.1.1?slim=true) |

---

### Release Notes

<details>
<summary>spring-projects/spring-boot (org.springframework.boot)</summary>

### [`v4.1.1`](https://redirect.github.com/spring-projects/spring-boot/compare/v4.1.0...v4.1.1)

### [`v4.1.0`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.1.0)

Full [release notes for Spring Boot 4.1](https://redirect.github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) are available on the wiki.

#### :star: New Features

- Add public constructor to InvalidConfigurationPropertyValueException that accepts a cause [#&#8203;50211](https://redirect.github.com/spring-projects/spring-boot/issues/50211)
- Reduce memory consumption when repeatedly calling WritableJson.toByteArray [#&#8203;49428](https://redirect.github.com/spring-projects/spring-boot/issues/49428)

#### :lady\_beetle: Bug Fixes

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

#### :notebook\_with\_decorative\_cover: Documentation

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

#### :hammer: Dependency Upgrades

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

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;Abdlatif-nabgha](https://redirect.github.com/Abdlatif-nabgha), [@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;Kapil-chn7](https://redirect.github.com/Kapil-chn7), [@&#8203;Kimgyuilli](https://redirect.github.com/Kimgyuilli), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;ares333](https://redirect.github.com/ares333), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;eddumelendez](https://redirect.github.com/eddumelendez), [@&#8203;henriquejsza](https://redirect.github.com/henriquejsza), [@&#8203;igormukhin](https://redirect.github.com/igormukhin), [@&#8203;johnnypwong](https://redirect.github.com/johnnypwong), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;leestana01](https://redirect.github.com/leestana01), [@&#8203;mheath](https://redirect.github.com/mheath), [@&#8203;mmoayyed](https://redirect.github.com/mmoayyed), [@&#8203;msridhar](https://redirect.github.com/msridhar), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;vinhhieu21](https://redirect.github.com/vinhhieu21), [@&#8203;vpavic](https://redirect.github.com/vpavic), [@&#8203;won-seoop](https://redirect.github.com/won-seoop), and [@&#8203;zxuhan](https://redirect.github.com/zxuhan)

### [`v4.0.8`](https://redirect.github.com/spring-projects/spring-boot/compare/v4.0.7...v4.0.8)

### [`v4.0.7`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.0.7)

#### :lady\_beetle: Bug Fixes

- MailSender auto-configuration does not enable hostname verification [#&#8203;50746](https://redirect.github.com/spring-projects/spring-boot/issues/50746)
- Artemis auto-configuration uses a predictable default location for the embedded broker's data [#&#8203;50744](https://redirect.github.com/spring-projects/spring-boot/issues/50744)
- NullPointerException in reactor-netty SniProvider and unmapped SSL bundle with RSocket [#&#8203;50640](https://redirect.github.com/spring-projects/spring-boot/pull/50640)
- SSL should not be enabled when a SSL bundle is overridden to an empty string [#&#8203;50634](https://redirect.github.com/spring-projects/spring-boot/issues/50634)
- Docker Compose support does not restore thread interrupt flag when catching InterruptedException [#&#8203;50617](https://redirect.github.com/spring-projects/spring-boot/issues/50617)
- RabbitProperties enables SSL even when spring.rabbitmq.ssl.bundle is overridden to an empty string [#&#8203;50611](https://redirect.github.com/spring-projects/spring-boot/issues/50611)
- NullPointerException in reactor-netty SniProvider when SSL bundle uses client-auth or server truststore without server-name-bundles [#&#8203;50609](https://redirect.github.com/spring-projects/spring-boot/issues/50609)
- Test auto-configuration no longer integrates Spring Security with HtmlUnitDriver [#&#8203;50602](https://redirect.github.com/spring-projects/spring-boot/pull/50602)
- Layer written outside the output location of '//' exception is thrown when using extract layers in root directory [#&#8203;50509](https://redirect.github.com/spring-projects/spring-boot/issues/50509)
- ConfigurationPropertiesReportEndpoint exposes AOP proxy internals [#&#8203;50416](https://redirect.github.com/spring-projects/spring-boot/issues/50416)
- Created StackTracePrinter instances have no access to the Environment [#&#8203;50413](https://redirect.github.com/spring-projects/spring-boot/issues/50413)
- MappingsEndpoint reports the context's own ID as parentId when a parent exists [#&#8203;50411](https://redirect.github.com/spring-projects/spring-boot/issues/50411)
- Buildpack module does not validate long-to-int casts [#&#8203;50409](https://redirect.github.com/spring-projects/spring-boot/issues/50409)
- GraphQL WebSocket support does not configure allowed origins [#&#8203;50393](https://redirect.github.com/spring-projects/spring-boot/issues/50393)
- Configuration property metadata includes incorrect class references [#&#8203;50375](https://redirect.github.com/spring-projects/spring-boot/pull/50375)
- Spring Boot Loader Does Not Support RSA and EC Signed Jars [#&#8203;50297](https://redirect.github.com/spring-projects/spring-boot/issues/50297)
- Meter registries are not removed from the global registry when the context is closed [#&#8203;50286](https://redirect.github.com/spring-projects/spring-boot/issues/50286)
- Nullable annotations from AbstractErrorController.getErrorAttributes are not aligned with implementation [#&#8203;50265](https://redirect.github.com/spring-projects/spring-boot/pull/50265)
- EndpointRequest links matcher unnecessarily matches HTTP methods other than GET  [#&#8203;50260](https://redirect.github.com/spring-projects/spring-boot/issues/50260)
- Actuator's '/cloudfoundryapplication' endpoint does not work if restrictive CORS configuration is provided using a bean named corsConfigurationSource [#&#8203;50257](https://redirect.github.com/spring-projects/spring-boot/issues/50257)
- ThreadPoolTaskScheduleBuilder unnecessarily loses precision when configuring await termination time [#&#8203;50233](https://redirect.github.com/spring-projects/spring-boot/issues/50233)
- NimbusJwtDecoder silently accepts unknown values for spring.security.oauth2.resourceserver.jwt.jws-algorithms [#&#8203;50227](https://redirect.github.com/spring-projects/spring-boot/issues/50227)
- Apply HTML escaping to timestamp attribute in Whitelabel error page [#&#8203;50215](https://redirect.github.com/spring-projects/spring-boot/issues/50215)
- Setting server.servlet.session.cookie.partitioned=true has no effect when using Tomcat [#&#8203;50201](https://redirect.github.com/spring-projects/spring-boot/issues/50201)

#### :notebook\_with\_decorative\_cover: Documentation

- Fix reference to Gradle documentation for module replacement [#&#8203;50646](https://redirect.github.com/spring-projects/spring-boot/issues/50646)
- Document SSL reloading with Let's Encrypt [#&#8203;50629](https://redirect.github.com/spring-projects/spring-boot/issues/50629)
- Remove the use of Optional from Data Neo4j repository examples [#&#8203;50621](https://redirect.github.com/spring-projects/spring-boot/issues/50621)
- Fix typos in documentation [#&#8203;50619](https://redirect.github.com/spring-projects/spring-boot/issues/50619)
- Clarify dependency requirement for Bean Validation support [#&#8203;50613](https://redirect.github.com/spring-projects/spring-boot/issues/50613)
- Document Java 25 requirement for AOT cache [#&#8203;50484](https://redirect.github.com/spring-projects/spring-boot/issues/50484)
- Add links for Java CAS Client Spring Boot Starter [#&#8203;50281](https://redirect.github.com/spring-projects/spring-boot/pull/50281)
- Document known testcontainers lifecycle issues  [#&#8203;50219](https://redirect.github.com/spring-projects/spring-boot/issues/50219)
- Document adding multiple connectors for Jetty [#&#8203;50217](https://redirect.github.com/spring-projects/spring-boot/issues/50217)
- Polish InvalidConfigurationPropertyValueException constructor javadoc [#&#8203;50213](https://redirect.github.com/spring-projects/spring-boot/issues/50213)
- Fix typo in Spring Security OAuth2 client registration documentation [#&#8203;50198](https://redirect.github.com/spring-projects/spring-boot/issues/50198)

#### :hammer: Dependency Upgrades

- Upgrade to [Caffeine 3.2.4](https://redirect.github.com/ben-manes/caffeine/releases/tag/v3.2.4) [#&#8203;50322](https://redirect.github.com/spring-projects/spring-boot/issues/50322)
- Upgrade to Cassandra Driver 4.19.3 [#&#8203;50681](https://redirect.github.com/spring-projects/spring-boot/issues/50681)
- Upgrade to Glassfish JAXB 4.0.9 [#&#8203;50682](https://redirect.github.com/spring-projects/spring-boot/issues/50682)
- Upgrade to Groovy 5.0.6 [#&#8203;50324](https://redirect.github.com/spring-projects/spring-boot/issues/50324)
- Upgrade to Hibernate 7.2.19.Final [#&#8203;50733](https://redirect.github.com/spring-projects/spring-boot/issues/50733)
- Upgrade to Jackson 2 Bom 2.21.4 [#&#8203;50684](https://redirect.github.com/spring-projects/spring-boot/issues/50684)
- Upgrade to Jackson Bom 3.1.4 [#&#8203;50685](https://redirect.github.com/spring-projects/spring-boot/issues/50685)
- Upgrade to Jakarta Json Bind 3.0.2 [#&#8203;50686](https://redirect.github.com/spring-projects/spring-boot/issues/50686)
- Upgrade to [Jakarta XML Bind 4.0.5](https://redirect.github.com/jakartaee/jaxb-api/releases/tag/4.0.5) [#&#8203;50328](https://redirect.github.com/spring-projects/spring-boot/issues/50328)
- Upgrade to Jaxen 2.0.6 [#&#8203;50717](https://redirect.github.com/spring-projects/spring-boot/issues/50717)
- Upgrade to [Jetty 12.1.10](https://redirect.github.com/jetty/jetty.project/releases/tag/jetty-12.1.10) [#&#8203;50688](https://redirect.github.com/spring-projects/spring-boot/issues/50688)
- Upgrade to Jetty Reactive HTTPClient 4.1.5 [#&#8203;50718](https://redirect.github.com/spring-projects/spring-boot/issues/50718)
- Upgrade to jOOQ 3.19.35 [#&#8203;50719](https://redirect.github.com/spring-projects/spring-boot/issues/50719)
- Upgrade to [Liquibase 5.0.3](https://redirect.github.com/liquibase/liquibase/releases/tag/v5.0.3) [#&#8203;50554](https://redirect.github.com/spring-projects/spring-boot/issues/50554)
- Upgrade to Logback 1.5.34 [#&#8203;50689](https://redirect.github.com/spring-projects/spring-boot/issues/50689)
- Upgrade to [Maven Enforcer Plugin 3.6.3](https://redirect.github.com/apache/maven-enforcer/releases/tag/enforcer-3.6.3) [#&#8203;50555](https://redirect.github.com/spring-projects/spring-boot/issues/50555)
- Upgrade to [Maven Failsafe Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50690](https://redirect.github.com/spring-projects/spring-boot/issues/50690)
- Upgrade to [Maven Surefire Plugin 3.5.6](https://redirect.github.com/apache/maven-surefire/releases/tag/surefire-3.5.6) [#&#8203;50691](https://redirect.github.com/spring-projects/spring-boot/issues/50691)
- Upgrade to [Micrometer 1.16.6](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.16.6) [#&#8203;50535](https://redirect.github.com/spring-projects/spring-boot/issues/50535)
- Upgrade to [Micrometer Tracing 1.6.6](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.6.6) [#&#8203;50536](https://redirect.github.com/spring-projects/spring-boot/issues/50536)
- Upgrade to [Neo4j Java Driver 6.1.0](https://redirect.github.com/neo4j/neo4j-java-driver/releases/tag/6.1.0) [#&#8203;50556](https://redirect.github.com/spring-projects/spring-boot/issues/50556)
- Upgrade to Netty 4.2.15.Final [#&#8203;50692](https://redirect.github.com/spring-projects/spring-boot/issues/50692)
- Upgrade to [Postgresql 42.7.11](https://redirect.github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.11) [#&#8203;50332](https://redirect.github.com/spring-projects/spring-boot/issues/50332)
- Upgrade to [R2DBC MySQL 1.4.2](https://redirect.github.com/asyncer-io/r2dbc-mysql/releases/tag/r2dbc-mysql-1.4.2) [#&#8203;50333](https://redirect.github.com/spring-projects/spring-boot/issues/50333)
- Upgrade to [Reactor Bom 2025.0.6](https://redirect.github.com/reactor/reactor/releases/tag/2025.0.6) [#&#8203;50537](https://redirect.github.com/spring-projects/spring-boot/issues/50537)
- Upgrade to SAAJ Impl 3.0.6 [#&#8203;50720](https://redirect.github.com/spring-projects/spring-boot/issues/50720)
- Upgrade to SLF4J 2.0.18 [#&#8203;50558](https://redirect.github.com/spring-projects/spring-boot/issues/50558)
- Upgrade to [Spring AMQP 4.0.4](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v4.0.4) [#&#8203;50538](https://redirect.github.com/spring-projects/spring-boot/issues/50538)
- Upgrade to [Spring Batch 6.0.4](https://redirect.github.com/spring-projects/spring-batch/releases/tag/v6.0.4) [#&#8203;50539](https://redirect.github.com/spring-projects/spring-boot/issues/50539)
- Upgrade to [Spring Data Bom 2025.1.6](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.1.6) [#&#8203;50540](https://redirect.github.com/spring-projects/spring-boot/issues/50540)
- Upgrade to [Spring Framework 7.0.8](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v7.0.8) [#&#8203;50541](https://redirect.github.com/spring-projects/spring-boot/issues/50541)
- Upgrade to [Spring GraphQL 2.0.4](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v2.0.4) [#&#8203;50740](https://redirect.github.com/spring-projects/spring-boot/issues/50740)
- Upgrade to [Spring HATEOAS 3.0.4](https://redirect.github.com/spring-projects/spring-hateoas/releases/tag/3.0.4) [#&#8203;50542](https://redirect.github.com/spring-projects/spring-boot/issues/50542)
- Upgrade to [Spring Integration 7.0.5](https://redirect.github.com/spring-projects/spring-integration/releases/tag/v7.0.5) [#&#8203;50543](https://redirect.github.com/spring-projects/spring-boot/issues/50543)
- Upgrade to [Spring Kafka 4.0.6](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v4.0.6) [#&#8203;50544](https://redirect.github.com/spring-projects/spring-boot/issues/50544)
- Upgrade to [Spring LDAP 4.0.4](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/4.0.4) [#&#8203;50545](https://redirect.github.com/spring-projects/spring-boot/issues/50545)
- Upgrade to [Spring Pulsar 2.0.6](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v2.0.6) [#&#8203;50546](https://redirect.github.com/spring-projects/spring-boot/issues/50546)
- Upgrade to [Spring RESTDocs 4.0.1](https://redirect.github.com/spring-projects/spring-restdocs/releases/tag/v4.0.1) [#&#8203;50547](https://redirect.github.com/spring-projects/spring-boot/issues/50547)
- Upgrade to [Spring Security 7.0.6](https://redirect.github.com/spring-projects/spring-security/releases/tag/7.0.6) [#&#8203;50548](https://redirect.github.com/spring-projects/spring-boot/issues/50548)
- Upgrade to [Spring Session 4.0.4](https://redirect.github.com/spring-projects/spring-session/releases/tag/4.0.4) [#&#8203;50549](https://redirect.github.com/spring-projects/spring-boot/issues/50549)
- Upgrade to [Spring WS 5.0.2](https://redirect.github.com/spring-projects/spring-ws/releases/tag/v5.0.2) [#&#8203;50550](https://redirect.github.com/spring-projects/spring-boot/issues/50550)
- Upgrade to [Tomcat 11.0.22](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) [#&#8203;50335](https://redirect.github.com/spring-projects/spring-boot/issues/50335)

#### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;Abdlatif-nabgha](https://redirect.github.com/Abdlatif-nabgha), [@&#8203;DragonFSKY](https://redirect.github.com/DragonFSKY), [@&#8203;Kapil-chn7](https://redirect.github.com/Kapil-chn7), [@&#8203;Kimgyuilli](https://redirect.github.com/Kimgyuilli), [@&#8203;SJvaca30](https://redirect.github.com/SJvaca30), [@&#8203;SebTardif](https://redirect.github.com/SebTardif), [@&#8203;ares333](https://redirect.github.com/ares333), [@&#8203;codingkiddo](https://redirect.github.com/codingkiddo), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;henriquejsza](https://redirect.github.com/henriquejsza), [@&#8203;igormukhin](https://redirect.github.com/igormukhin), [@&#8203;johnnypwong](https://redirect.github.com/johnnypwong), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;leestana01](https://redirect.github.com/leestana01), [@&#8203;mheath](https://redirect.github.com/mheath), [@&#8203;mmoayyed](https://redirect.github.com/mmoayyed), [@&#8203;msridhar](https://redirect.github.com/msridhar), [@&#8203;ngocnhan-tran1996](https://redirect.github.com/ngocnhan-tran1996), [@&#8203;nosan](https://redirect.github.com/nosan), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;scordio](https://redirect.github.com/scordio), [@&#8203;vinhhieu21](https://redirect.github.com/vinhhieu21), [@&#8203;won-seoop](https://redirect.github.com/won-seoop), and [@&#8203;zxuhan](https://redirect.github.com/zxuhan)

### [`v4.0.6`](https://redirect.github.com/spring-projects/spring-boot/releases/tag/v4.0.6)

##### :lady\_beetle: Bug Fixes

- Default security is misconfigured when spring-boot-actuator-autoconfigure is present and spring-boot-health is not [#&#8203;50188](https://redirect.github.com/spring-projects/spring-boot/issues/50188)
- Elasticsearch Rest5Client auto-configuration misconfigures underlying HTTP client [#&#8203;50187](https://redirect.github.com/spring-projects/spring-boot/issues/50187)
- ApplicationPidFileWriter does not handle symlinks correctly [#&#8203;50185](https://redirect.github.com/spring-projects/spring-boot/issues/50185)
- RandomValuePropertySource is not suitable for secrets [#&#8203;50183](https://redirect.github.com/spring-projects/spring-boot/issues/50183)
- Cassandra auto-configuration misconfigures CqlSessionBuilder [#&#8203;50180](https://redirect.github.com/spring-projects/spring-boot/issues/50180)
- ApplicationTemp does not handle symlinks correctly [#&#8203;50178](https://redirect.github.com/spring-projects/spring-boot/issues/50178)
- Remote DevTools performs comparison incorrectly [#&#8203;50176](https://redirect.github.com/spring-projects/spring-boot/issues/50176)
- spring.rabbitmq.ssl.verify-hostname is applied inconsistently [#&#8203;50174](https://redirect.github.com/spring-projects/spring-boot/issues/50174)
- Whole number values are ignored when configuring min and max expected values and SLO boundaries for a distribution summary meter [#&#8203;50077](https://redirect.github.com/spring-projects/spring-boot/issues/50077)
- Classic starters are missing several modules [#&#8203;50071](https://redirect.github.com/spring-projects/spring-boot/issues/50071)
- Module spring-boot-resttestclient is missing from spring-boot-starter-test-classic [#&#8203;50069](https://redirect.github.com/spring-projects/spring-boot/pull/50069)
- Annotations like `@Ssl` don't work on `@Bean` methods when using `@ServiceConnection` [#&#8203;50064](https://redirect.github.com/spring-projects/spring-boot/issues/50064)
- EnversRevisionRepositoriesRegistrar should reuse `@EnableEnversRepositories` rather than configuring the JPA counterpart [#&#8203;50039](https://redirect.github.com/spring-projects/spring-boot/issues/50039)
- WebFlux Cloud Foundry links endpoint includes query string from received request in resolved links [#&#8203;50017](https://redirect.github.com/spring-projects/spring-boot/issues/50017)
- Imports on a containing test class are ignored when a nested class has imports [#&#8203;50012](https://redirect.github.com/spring-projects/spring-boot/issues/50012)
- With spring.jackson.use-jackson2-defaults set to true, FAIL\_ON\_UNKNOWN\_PROPERTIES is enabled [#&#8203;49951](https://redirect.github.com/spring-projects/spring-boot/issues/49951)
- 500 response from env endpoint when supplied pattern is invalid [#&#8203;49946](https://redirect.github.com/spring-projects/spring-boot/issues/49946)
- Reactive MongoDB starter has a transitive dependency on the synchronous MongoDB driver [#&#8203;49945](https://redirect.github.com/spring-projects/spring-boot/issues/49945)
- HTTP method is lost when configuring excludes in EndpointRequest [#&#8203;49943](https://redirect.github.com/spring-projects/spring-boot/issues/49943)
- Honor HttpMethod for reactive additional endpoint paths [#&#8203;49880](https://redirect.github.com/spring-projects/spring-boot/issues/49880)
- Docker Compose support doesn't work with apache/artemis image [#&#8203;49869](https://redirect.github.com/spring-projects/spring-boot/issues/49869)
- Docker Compose support doesn't work with apache/activemq image [#&#8203;49866](https://redirect.github.com/spring-projects/spring-boot/issues/49866)
- Spring Security's PathPatternRequestMatcher.Builder is not auto-configured when using WebMvcTest and spring-boot-security-test [#&#8203;49854](https://redirect.github.com/spring-projects/spring-boot/issues/49854)
- API versioning path strategy should be applied path last as it is not meant to yield [#&#8203;49800](https://redirect.github.com/spring-projects/spring-boot/issues/49800)

##### :notebook\_with\_decorative\_cover: Documentation

- Update docs to encourage Java fundamentals for beginners that prefer to learn that way [#&#8203;50146](https://redirect.github.com/spring-projects/spring-boot/issues/50146)
- HTTP Service Interface Clients still document that API versioning can be configured via properties [#&#8203;50126](https://redirect.github.com/spring-projects/spring-boot/issues/50126)
- Link to the observability section of the Lettuce documentation is broken [#&#8203;50097](https://redirect.github.com/spring-projects/spring-boot/issues/50097)
- Javadoc for StaticResourceLocation.FAVICON doesn't describe icons location [#&#8203;50085](https://redirect.github.com/spring-projects/spring-boot/issues/50085)
- MySamlRelyingPartyConfiguration is missing a Kotlin sample [#&#8203;50024](https://redirect.github.com/spring-projects/spring-boot/issues/50024)
- Incorrect default value for management.httpexchanges.recording.include in configuration metadata [#&#8203;50019](https://redirect.github.com/spring-projects/spring-boot/issues/50019)
- Link to the Kubernetes documentation when discussing startup probes [#&#8203;50015](https://redirect.github.com/spring-projects/spring-boot/issues/50015)
- Typo in JdbcSessionAutoConfiguration Javadoc [#&#8203;49873](https://redirect.github.com/spring-projects/spring-boot/pull/49873)
- Clarify that configuration property default values are not available through the Environment [#&#8203;49851](https://redirect.github.com/spring-projects/spring-boot/issues/49851)
- Document the need for Liquibase and Flyway starters [#&#8203;49839](https://redirect.github.com/spring-projects/spring-boot/pull/49839)
- Kafka documentation refers to deprecated JSON serializer and deserializer classes [#&#8203;49826](https://redirect.github.com/spring-projects/spring-boot/pull/49826)

##### :hammer: Dependency Upgrades

- Upgrade to Elasticsearch Client 9.2.8 [#&#8203;50027](https://redirect.github.com/spring-projects/spring-boot/issues/50027)
- Upgrade to Groovy 5.0.5 [#&#8203;49911](https://redirect.github.com/spring-projects/spring-boot/issues/49911)
- Upgrade to Hibernate 7.2.12.Final [#&#8203;50134](https://redirect.github.com/spring-projects/spring-boot/issues/50134)
- Upgrade to Jackson Bom 3.1.2 [#&#8203;50051](https://redirect.github.com/spring-projects/spring-boot/issues/50051)
- Upgrade to [Jaxen 2.0.1](https://redirect.github.com/jaxen-xpath/jaxen/releases/tag/v2.0.1) [#&#8203;50104](https://redirect.github.com/spring-projects/spring-boot/issues/50104)
- Upgrade to [Jaybird 6.0.5](https://redirect.github.com/FirebirdSQL/jaybird/releases/tag/v6.0.5) [#&#8203;49914](https://redirect.github.com/spring-projects/spring-boot/issues/49914)
- Upgrade to [Jetty 12.1.8](https://redirect.github.com/jetty/jetty.project/releases/tag/jetty-12.1.8) [#&#8203;49915](https://redirect.github.com/spring-projects/spring-boot/issues/49915)
- Upgrade to [jOOQ 3.19.32](https://redirect.github.com/jOOQ/jOOQ/releases/tag/version-3.19.32) [#&#8203;50105](https://redirect.github.com/spring-projects/spring-boot/issues/50105)
- Upgrade to [Log4j2 2.25.4](https://redirect.github.com/apache/logging-log4j2/releases/tag/rel%2F2.25.4) [#&#8203;49916](https://redirect.github.com/spring-projects/spring-boot/issues/49916)
- Upgrade to Lombok 1.18.46 [#&#8203;50150](https://redirect.github.com/spring-projects/spring-boot/issues/50150)
- Upgrade to [MariaDB 3.5.8](https://mariadb.com/kb/en/mariadb-connector-j-3-5-8-release-notes) [#&#8203;49917](https://redirect.github.com/spring-projects/spring-boot/issues/49917)
- Upgrade to [Micrometer 1.16.5](https://redirect.github.com/micrometer-metrics/micrometer/releases/tag/v1.16.5) [#&#8203;49972](https://redirect.github.com/spring-projects/spring-boot/issues/49972)
- Upgrade to [Micrometer Tracing 1.6.5](https://redirect.github.com/micrometer-metrics/tracing/releases/tag/v1.6.5) [#&#8203;49973](https://redirect.github.com/spring-projects/spring-boot/issues/49973)
- Upgrade to [MongoDB 5.6.5](https://redirect.github.com/mongodb/mongo-java-driver/releases/tag/r5.6.5) [#&#8203;50028](https://redirect.github.com/spring-projects/spring-boot/issues/50028)
- Upgrade to [MySQL 9.7.0](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html) [#&#8203;50159](https://redirect.github.com/spring-projects/spring-boot/issues/50159)
- Upgrade to Neo4j Java Driver 6.0.5 [#&#8203;50075](https://redirect.github.com/spring-projects/spring-boot/issues/50075)
- Upgrade to [Reactor Bom 2025.0.5](https://redirect.github.com/reactor/reactor/releases/tag/2025.0.5) [#&#8203;49974](https://redirect.github.com/spring-projects/spring-boot/issues/49974)
- Upgrade to [Spring AMQP 4.0.3](https://redirect.github.com/spring-projects/spring-amqp/releases/tag/v4.0.3) [#&#8203;49975](https://redirect.github.com/spring-projects/spring-boot/issues/49975)
- Upgrade to [Spring Data Bom 2025.1.5](https://redirect.github.com/spring-projects/spring-data-bom/releases/tag/2025.1.5) [#&#8203;49976](https://redirect.github.com/spring-projects/spring-boot/issues/49976)
- Upgrade to [Spring Framework 7.0.7](https://redirect.github.com/spring-projects/spring-framework/releases/tag/v7.0.7) [#&#8203;49977](https://redirect.github.com/spring-projects/spring-boot/issues/49977)
- Upgrade to [Spring GraphQL 2.0.3](https://redirect.github.com/spring-projects/spring-graphql/releases/tag/v2.0.3) [#&#8203;49978](https://redirect.github.com/spring-projects/spring-boot/issues/49978)
- Upgrade to [Spring Kafka 4.0.5](https://redirect.github.com/spring-projects/spring-kafka/releases/tag/v4.0.5) [#&#8203;49979](https://redirect.github.com/spring-projects/spring-boot/issues/49979)
- Upgrade to [Spring LDAP 4.0.3](https://redirect.github.com/spring-projects/spring-ldap/releases/tag/4.0.3) [#&#8203;49980](https://redirect.github.com/spring-projects/spring-boot/issues/49980)
- Upgrade to [Spring Pulsar 2.0.5](https://redirect.github.com/spring-projects/spring-pulsar/releases/tag/v2.0.5) [#&#8203;49981](https://redirect.github.com/spring-projects/spring-boot/issues/49981)
- Upgrade to [Spring Security 7.0.5](https://redirect.github.com/spring-projects/spring-security/releases/tag/7.0.5) [#&#8203;49982](https://redirect.github.com/spring-projects/spring-boot/issues/49982)
- Upgrade to [Spring Session 4.0.3](https://redirect.github.com/spring-projects/spring-session/releases/tag/4.0.3) [#&#8203;49983](https://redirect.github.com/spring-projects/spring-boot/issues/49983)
- Upgrade to [Testcontainers 2.0.5](https://redirect.github.com/testcontainers/testcontainers-java/releases/tag/2.0.5) [#&#8203;50135](https://redirect.github.com/spring-projects/spring-boot/issues/50135)
- Upgrade to Thymeleaf 3.1.5.RELEASE [#&#8203;50152](https://redirect.github.com/spring-projects/spring-boot/issues/50152)
- Upgrade to Thymeleaf Extras SpringSecurity 3.1.5.RELEASE [#&#8203;50153](https://redirect.github.com/spring-projects/spring-boot/issues/50153)
- Upgrade to [Tomcat 11.0.21](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html) [#&#8203;49918](https://redirect.github.com/spring-projects/spring-boot/issues/49918)

##### :heart: Contributors

Thank you to all the contributors who worked on this release:

[@&#8203;GollapudiSrikanth](https://redirect.github.com/GollapudiSrikanth), [@&#8203;MohammedGhallab](https://redirect.github.com/MohammedGhallab), [@&#8203;bachhs](https://redirect.github.com/bachhs), [@&#8203;dlwldnjs1009](https://redirect.github.com/dlwldnjs1009), [@&#8203;edwardsre](https://redirect.github.com/edwardsre), [@&#8203;kodama-kcc](https://redirect.github.com/kodama-kcc), [@&#8203;kwondh5217](https://redirect.github.com/kwondh5217), [@&#8203;ppapaj](https://redirect.github.com/ppapaj), [@&#8203;quaff](https://redirect.github.com/quaff), [@&#8203;refeccd](https://redirect.github.com/refeccd), [@&#8203;scordio](https://redirect.github.com/scordio), and [@&#8203;xxxxxxjun](https://redirect.github.com/xxxxxxjun)

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

This PR was generated by [Mend Renovate](https://mend.io/renovate/). View the [repository job log](https://developer.mend.io/github/theborakompanioni/tor-spring-boot-starter).
<!--renovate-debug:eyJjcmVhdGVkSW5WZXIiOiI0My4xNTAuMCIsInVwZGF0ZWRJblZlciI6IjQ0LjM1LjQiLCJ0YXJnZXRCcmFuY2giOiJkZXZlbCIsImxhYmVscyI6W119-->

