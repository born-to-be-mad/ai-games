package ai.architect.orchestrator;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application entry point for test slices (@WebMvcTest).
 * Located in the common parent package so that Spring Boot's upward package scan
 * can find it from any sub-package test class.
 */
@SpringBootApplication
class TestApplication {
}
