# compose-cast server image (T-009). Build from the repository root:
#   docker build -f server-app/Dockerfile -t compose-cast-server .
# or just `docker compose up` (docker-compose.yml). Screens come from the volume at /data
# (`screens/<id>.json`, optional `brand.json`; ADR 0011) — there is no built-in default: a missing
# mount stops the container with "Screens directory … does not exist".

# --- build: Gradle installDist of :server-app (JVM modules only, no Android SDK) ---------------
# The build output (jars) is platform-independent: build once on the host platform, also for
# multi-arch images (docker buildx build --platform linux/amd64,linux/arm64).
#
# Gradle comes from the official image, not from the wrapper: the wrapper downloads its distribution
# from GitHub Releases, and an outage there (HTTP 504, 2026-11-08) broke every fresh image build.
# The tag must match gradle/wrapper/gradle-wrapper.properties (checked by scripts/verify-docker.sh).
FROM --platform=$BUILDPLATFORM gradle:9.6.0-jdk21-noble AS build
WORKDIR /src
ENV GRADLE_USER_HOME=/cache/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY protocol protocol
COPY sdk-server sdk-server
COPY server-scripting server-scripting
COPY server-app server-app
# Dependency downloads are cached across builds (BuildKit cache mount).
RUN --mount=type=cache,target=/cache/gradle \
    gradle --no-daemon --console=plain -Pcomposecast.serverOnly=true :server-app:installDist \
    # Jars from the Gradle cache are 0600: make them readable by the non-root runtime user.
    && chmod -R a+rX server-app/build/install

# --- runtime: JRE on Alpine (busybox wget for the healthcheck) ------------------------------------
FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S composecast && adduser -S -G composecast composecast
COPY --from=build /src/server-app/build/install/server-app /opt/compose-cast
ENV COMPOSECAST_DATA_DIR=/data \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
VOLUME /data
EXPOSE 8080
USER composecast
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/health" || exit 1
ENTRYPOINT ["/opt/compose-cast/bin/server-app"]
