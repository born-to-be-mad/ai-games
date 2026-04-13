plugins {
    id("org.springframework.boot") version "4.0.3"
    // PIT mutation testing disabled: gradle-pitest-plugin 1.15.0 incompatible with Gradle 9+.
    // Re-enable once a Gradle 9-compatible release is available.
    // id("info.solidsoft.pitest")
}

springBoot {
    mainClass = "com.aiarchitect.terraquery.TerraQueryApplication"
}

dependencies {
    implementation(project(":terra-core"))

    // Spring AI model providers
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    // Spring AI MCP client
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // H2 (dev/test)
    runtimeOnly("com.h2database:h2")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

tasks.register<Test>("liveTest") {
    useJUnitPlatform {
        includeTags("live-llm")
    }
    systemProperty("spring.profiles.active", "live-test")
}

// pitest {
//     targetClasses.set(listOf("com.aiarchitect.terraquery.domain.*", "com.aiarchitect.terraquery.service.*"))
//     targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
//     mutators.set(listOf("DEFAULTS"))
//     timestampedReports.set(false)
//     outputFormats.set(listOf("HTML", "XML"))
//     threads.set(4)
//     mutationThreshold.set(60)
// }
