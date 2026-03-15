plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.aiarchitect.rag"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI — model providers
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    // Spring AI — document reader (PDF via Apache PDFBox)
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")

    // Spring AI — vector store: VectorStore interface, SimpleVectorStore, SearchRequest
    implementation("org.springframework.ai:spring-ai-vector-store")

    // Spring AI — ChromaDB vector store (no auto-config; bean created manually in VectorStoreConfig)
    implementation("org.springframework.ai:spring-ai-chroma-store")

    // Resilience — retry with exponential backoff
    // Note: spring-boot-starter-aop is not in the Spring Boot 4.0.3 BOM; aspectjweaver is
    // already on the classpath transitively. Only spring-retry needs an explicit version.
    implementation("org.springframework.retry:spring-retry:2.0.11")

    // Caching — Spring Cache abstraction + Caffeine high-performance cache
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Observability — Actuator + Micrometer → Prometheus metrics endpoint
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Distributed tracing — Micrometer Tracing bridge + OTel OTLP exporter → Grafana Tempo
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Centralised logging — Loki4j appender pushes JSON logs (with traceId/spanId) to Grafana Loki
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    // H2 for local dev
    runtimeOnly("com.h2database:h2")

    // PostgreSQL — runtime driver for production profile
    runtimeOnly("org.postgresql:postgresql")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Apache Commons Math — linear regression for PredictionService
    implementation("org.apache.commons:commons-math3:3.6.1")

    // dotenv for .env file support
    implementation("io.github.cdimascio:dotenv-java:3.0.2")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")

    // ArchUnit — architecture enforcement (1.4.1 supports Java 25 / class file v69)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
