# The tool image: lector on JDK 25, with git, Maven, and JDKs 17, 21, and 25 for the projects it builds (concept 8.4).
# The pr command picks the JDK a project needs through LECTOR_JDK_<release>.
# No model weights: they are in the Ollama image. No recipe JARs: the project's own Maven or Gradle fetches them (M2).
# No Gradle: a Gradle project's wrapper downloads its own distribution (M7). The OpenRewrite Gradle plugin comes with the
# distribution, as a directory of JARs that the recipe init script loads (LECTOR_REWRITE_GRADLE_PLUGIN).
# For TypeScript projects (M9): Node 22 and 24, one per LTS line, picked through LECTOR_NODE_<major>; Corepack, which
# runs the package manager a project pins, with pnpm, Yarn, and Yarn classic pre-cached; and jest-junit. Their versions
# are in docker/lector/node/package.json. No OpenRewrite JavaScript, no Moderne CLI (spec M9 Q10).
#
# Build from the repository root, after ./gradlew :cli:installDist:
#   docker build -f docker/lector/Dockerfile -t lector .
FROM eclipse-temurin:17.0.20.1_1-jdk-noble AS jdk17
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS jdk21
FROM maven:3-eclipse-temurin-26-noble AS maven
FROM node:22.23.3-bookworm-slim AS node22
FROM node:24.21.0-bookworm-slim AS node24

FROM eclipse-temurin:25.0.4.1_1-jdk-noble
COPY --from=jdk17 /opt/java/openjdk /opt/java/17
COPY --from=jdk21 /opt/java/openjdk /opt/java/21
COPY --from=maven /usr/share/maven /usr/share/maven
# Each Node with its npm and the header lector-ai reads the version from. Not the Node images' Corepack and Yarn: the
# Node directory comes first on a run's PATH, and its yarn would hide the Corepack one.
COPY --from=node22 /usr/local/bin/node /opt/node/22/bin/node
COPY --from=node22 /usr/local/lib/node_modules/npm /opt/node/22/lib/node_modules/npm
COPY --from=node22 /usr/local/include/node /opt/node/22/include/node
COPY --from=node24 /usr/local/bin/node /opt/node/24/bin/node
COPY --from=node24 /usr/local/lib/node_modules/npm /opt/node/24/lib/node_modules/npm
COPY --from=node24 /usr/local/include/node /opt/node/24/include/node
COPY docker/lector/node/package.json docker/lector/node/package-lock.json /opt/lector-node/
# Corepack's cache is outside HOME, which the action sets to the runner's directory, so the pre-cached package managers
# are found there; pnpm 12 is a native binary, which its first run downloads. A project that pins another version
# fetches it from its registry (COREPACK_NPM_*).
ENV COREPACK_HOME=/opt/corepack/cache \
    COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    COREPACK_DEFAULT_TO_LATEST=0 \
    PATH=/opt/corepack/bin:/opt/node/24/bin:$PATH
# GitHub mounts the workspace owned by the runner's user, and the action runs as root: git must trust it.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn \
    && git config --system --add safe.directory '*' \
    && for major in 22 24; do \
         ln -s ../lib/node_modules/npm/bin/npm-cli.js /opt/node/$major/bin/npm \
         && ln -s ../lib/node_modules/npm/bin/npx-cli.js /opt/node/$major/bin/npx; \
       done \
    && cd /opt/lector-node \
    && npm ci --omit=dev --ignore-scripts \
    && mkdir -p /opt/corepack/bin \
    && node_modules/.bin/corepack enable --install-directory /opt/corepack/bin pnpm yarn \
    && version() { node -p "require('./package.json').devDependencies['$1']"; } \
    && node_modules/.bin/corepack install --global "yarn@$(version yarn)" \
    && node_modules/.bin/corepack install --global "pnpm@$(version pnpm)" \
    && (cd /tmp && pnpm --version) \
    && node_modules/.bin/corepack "yarn@$(version @yarnpkg/cli-dist)" --version \
    && rm -rf /root/.npm
ENV MAVEN_HOME=/usr/share/maven \
    LECTOR_JDK_17=/opt/java/17 \
    LECTOR_JDK_21=/opt/java/21 \
    LECTOR_JDK_25=/opt/java/openjdk \
    LECTOR_NODE_22=/opt/node/22 \
    LECTOR_NODE_24=/opt/node/24 \
    LECTOR_JEST_JUNIT=/opt/lector-node/node_modules/jest-junit \
    LECTOR_REWRITE_GRADLE_PLUGIN=/opt/lector/rewrite-gradle-plugin \
    LANG=C.UTF-8
COPY cli/build/install/lector /opt/lector
ENTRYPOINT ["/opt/lector/bin/lector"]
