# The tool image: lector on JDK 25, with git, Maven, and JDKs 17, 21, and 25 for the projects it builds (concept 8.4).
# The pr command picks the JDK a project needs through LECTOR_JDK_<release>.
# No model weights: they are in the Ollama image. No recipe JARs: the project's own Maven fetches them (M2).
#
# Build from the repository root, after ./gradlew :cli:installDist:
#   docker build -f docker/lector/Dockerfile -t lector .
FROM eclipse-temurin:17.0.20.1_1-jdk-noble AS jdk17
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS jdk21
FROM maven:3.10.0-eclipse-temurin-25-noble AS maven

FROM eclipse-temurin:25.0.4.1_1-jdk-noble
COPY --from=jdk17 /opt/java/openjdk /opt/java/17
COPY --from=jdk21 /opt/java/openjdk /opt/java/21
COPY --from=maven /usr/share/maven /usr/share/maven
# GitHub mounts the workspace owned by the runner's user, and the action runs as root: git must trust it.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn \
    && git config --system --add safe.directory '*'
ENV MAVEN_HOME=/usr/share/maven \
    LECTOR_JDK_17=/opt/java/17 \
    LECTOR_JDK_21=/opt/java/21 \
    LECTOR_JDK_25=/opt/java/openjdk \
    LANG=C.UTF-8
COPY cli/build/install/lector /opt/lector
ENTRYPOINT ["/opt/lector/bin/lector"]
