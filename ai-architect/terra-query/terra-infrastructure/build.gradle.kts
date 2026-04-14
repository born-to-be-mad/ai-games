plugins {
    // Spring Boot plugin declared in root plugins {} — apply here without version
    id("org.springframework.boot")
    id("info.solidsoft.pitest")
    id("io.gatling.gradle")
}

springBoot {
    mainClass = "com.aiarchitect.terraquery.TerraQueryApplication"
}

dependencies {
    implementation(project(":terra-core"))

    // Common (BOM-managed)
    implementation(libs.spring.boot.starter)
    implementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Spring AI model providers
    implementation(libs.spring.ai.starter.model.openai)
    implementation(libs.spring.ai.starter.model.anthropic)
    implementation(libs.spring.ai.starter.model.ollama)

    // Spring AI MCP client
    implementation(libs.spring.ai.starter.mcp.client)

    // Spring Boot starters
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aspectj)
    implementation(libs.spring.boot.starter.cache)

    // H2 (dev/test)
    runtimeOnly(libs.h2)

    // Rate limiting
    implementation(libs.bucket4j.core)

    // Metrics
    implementation(libs.micrometer.registry.prometheus)

    // Distributed tracing — OTEL bridge + OTLP exporter (sends to Grafana Tempo)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)

    // Log shipping to Grafana Loki via Logback appender
    implementation(libs.loki4j)

    // Resilience4j — circuit breaker + retry with Spring AOP + Micrometer metrics
    implementation(libs.resilience4j.spring.boot)
    implementation(libs.resilience4j.micrometer)

    // Jackson
    implementation(libs.jackson.databind)

    // Test dependencies
    // Spring Boot 4.x modularised test slices — packages renamed:
    //   @WebMvcTest → org.springframework.boot.webmvc.test.autoconfigure (spring-boot-starter-webmvc-test)
    //   @DataJpaTest → org.springframework.boot.data.jpa.test.autoconfigure (spring-boot-data-jpa-test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.data.jpa.test)
    // spring-boot-starter-webflux needed for WebTestClient in streaming tests
    testImplementation(libs.spring.boot.starter.webflux)
    // Exclude ArchUnit's transitive junit-platform-* (1.12.2) so JUnit Jupiter 6.0.3 owns the whole
    // JUnit Platform classpath. ArchUnit 1.4.1 compiled against Platform 1.x APIs that are still
    // present in 6.x, so the exclusion is safe for architecture tests.
    testImplementation(libs.archunit.junit5) {
        exclude(group = "org.junit.platform")
        exclude(group = "org.junit", module = "junit-bom")
    }
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    // Gatling — performance baseline simulations
    gatling(libs.gatling.charts.highcharts)
}

tasks.register<Test>("liveTest") {
    useJUnitPlatform {
        includeTags("live-llm")
    }
    systemProperty("spring.profiles.active", "live-test")
    systemProperty("eval.subset", findProperty("subset") ?: "canary")
}

pitest {
    targetClasses.set(listOf("com.aiarchitect.terraquery.domain.*", "com.aiarchitect.terraquery.service.*"))
    targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
    mutators.set(listOf("DEFAULTS"))
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
    threads.set(4)
    mutationThreshold.set(50)
    // Auto-detection of JUnit 5/6 plugin fails when Jupiter version is 6.x;
    // explicitly set the pitest-junit5-plugin version (works for both JUnit 5 and 6).
    junit5PluginVersion.set(libs.versions.pitest.junit5.plugin.get())
}
