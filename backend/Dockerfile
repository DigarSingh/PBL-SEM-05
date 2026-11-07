# syntax=docker/dockerfile:1.7
# Build: compile and package the Spring Boot jar (tests run in CI, not in the image build).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/shopsmart-backend.jar /workspace/app.jar

# Runtime: JRE only, non-root user, uploads on a volume.
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home /app shopsmart \
 && mkdir -p /app/uploads \
 && chown -R shopsmart /app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER shopsmart
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    UPLOAD_DIR=/app/uploads \
    SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
