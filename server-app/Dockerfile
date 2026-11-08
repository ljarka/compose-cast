# compose-cast server image (T-009). Build from the repository root:
#   docker build -f server-app/Dockerfile -t compose-cast-server .
# or just `docker compose up` (docker-compose.yml). Screens come from the volume at /data
# (`screens/<id>.json`, optional `brand.json`; ADR 0011) — there is no built-in default: a missing
# mount stops the container with "Screens directory … does not exist".

# --- build: Gradle installDist of :server-app (JVM modules only, no Android SDK) ---------------
# The build output (jars) is platform-independent: build once on the host platform, also for
# multi-arch images (docker buildx build --platform linux/amd64,linux/arm64).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.13_9-jdk AS build
WORKDIR /src
# Wrapper + build scripts first: the Gradle distribution and dependency downloads are cached
# across source changes (BuildKit cache mount).
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY protocol protocol
COPY sdk-server sdk-server
COPY server-scripting server-scripting
COPY server-app server-app
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -Pcomposecast.serverOnly=true :server-app:installDist \
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
