// All versions are the single source of truth in gradle/libs.versions.toml.
// Gradle does NOT inject the version catalog extension into the ROOT project build script,
// so versions here are string literals — keep them in sync with the [versions] table in the TOML.
plugins {
    java
    id("org.springframework.boot") version "4.0.5" apply false  // libs.versions.spring-boot
    id("io.spring.dependency-management") version "1.1.7"       // libs.versions.spring-dependency-management
    id("info.solidsoft.pitest") version "1.19.0" apply false    // libs.versions.pitest
}

allprojects {
    group = "com.aiarchitect.terraquery"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)    // libs.versions.java
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.5")  // libs.versions.spring-boot
            mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M4")           // libs.versions.spring-ai
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("live-llm")
        }
    }
}
