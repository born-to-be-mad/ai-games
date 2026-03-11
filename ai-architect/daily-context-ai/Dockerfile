# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY orchestrator-core orchestrator-core
COPY orchestrator-mcp orchestrator-mcp
COPY orchestrator-web orchestrator-web
RUN chmod +x gradlew
RUN ./gradlew :orchestrator-web:bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/orchestrator-web/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
