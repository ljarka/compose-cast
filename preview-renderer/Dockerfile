# compose-cast preview renderer image (T-048): renders screens for the admin panel's phone frame.
# linux/amd64 only: Robolectric's native graphics exist for linux/x86_64, not linux/arm64.
# Built from the output of `./gradlew :preview-renderer:installRenderer` (needs the Android SDK,
# so it is not built inside Docker like the server image):
#   docker build --platform linux/amd64 -f preview-renderer/Dockerfile \
#     -t compose-cast-preview-renderer preview-renderer/build/renderer
# glibc base (Ubuntu), not Alpine: the native runtime is linked against glibc.
FROM eclipse-temurin:21.0.13_9-jre-noble
COPY --chown=root:root . /opt/preview-renderer
ENV PORT=8090 \
    JAVA_OPTS="-Xmx512m"
EXPOSE 8090
# The base image's unprivileged user (UID 1000); the renderer writes only to /tmp.
USER ubuntu
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/health" || exit 1
ENTRYPOINT ["/opt/preview-renderer/bin/preview-renderer"]
