package com.aiarchitect.rag.report.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Enforces hexagonal architecture boundaries.
 *
 * <p>The Dependency Rule: dependencies point INWARD.
 * Infrastructure → Domain is OK. Domain → Infrastructure is FORBIDDEN.
 *
 * <pre>
 * domain/          — pure business logic, no Spring/JPA/framework deps
 *   model/         — value objects and aggregates
 *   port/in/       — inbound ports (use cases / facades)
 *   port/out/      — outbound ports (repository, LLM, vector store)
 *   service/       — domain services implementing inbound ports
 *
 * infrastructure/  — framework adapters, Spring beans, JPA entities
 *   adapter/in/    — REST controllers, CLI runners, event listeners
 *   adapter/out/   — persistence, AI, vector store, PDF adapters
 *   config/        — Spring @Configuration classes
 *   props/         — @ConfigurationProperties classes
 * </pre>
 */
@DisplayName("Hexagonal Architecture Rules")
class HexagonalArchitectureTest {

    private static final String ROOT = "com.aiarchitect.rag.report";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("Domain must not depend on infrastructure")
    void domainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE);

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain must not import Spring framework annotations (except @Service)")
    void domainMustNotImportSpringWebOrData() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.data..",
                        "jakarta.persistence.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Inbound ports must be interfaces in domain.port.in")
    void inboundPortsMustBeInterfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage(ROOT + ".domain.port.in..")
                .should().beInterfaces();

        rule.check(classes);
    }

    @Test
    @DisplayName("Outbound ports must be interfaces in domain.port.out")
    void outboundPortsMustBeInterfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage(ROOT + ".domain.port.out..")
                .should().beInterfaces();

        rule.check(classes);
    }

    @Test
    @DisplayName("Infrastructure adapters must reside in infrastructure.adapter")
    void adaptersMustResideInInfrastructureAdapter() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .should().resideInAPackage(ROOT + ".infrastructure.adapter..");

        rule.check(classes);
    }
}
