# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy Gradle wrapper and config first (layer cache for dependency downloads)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY terra-core/build.gradle.kts terra-core/
COPY terra-infrastructure/build.gradle.kts terra-infrastructure/

# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon -q || true

# Copy source code
COPY terra-core/src terra-core/src
COPY terra-infrastructure/src terra-infrastructure/src
COPY api-spec/ api-spec/

# Build the fat JAR (skip tests — run separately in CI)
RUN ./gradlew :terra-infrastructure:bootJar --no-daemon -x test

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/terra-infrastructure/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=10s --start-period=60s --retries=5 CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
