package com.aiarchitect.terraquery.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit boundary enforcement — 5 hexagonal architecture rules.
 * These rules prevent architecture erosion over time.
 */
class HexagonalArchitectureTest {

    static JavaClasses allClasses;
    static JavaClasses domainClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiarchitect.terraquery");

        domainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.aiarchitect.terraquery.model",
                        "com.aiarchitect.terraquery.port",
                        "com.aiarchitect.terraquery.service"
                );
    }

    /**
     * Rule 1: Domain layer (model, port, service) must have zero Spring imports.
     * terra-core must remain a plain Java library.
     */
    @Test
    void rule1_domainHasNoSpringDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.aiarchitect.terraquery.model..",
                        "com.aiarchitect.terraquery.port..",
                        "com.aiarchitect.terraquery.service.."
                )
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet.."
                )
                .because("Domain layer must remain framework-free (hexagonal architecture)");

        rule.check(allClasses);
    }

    /**
     * Rule 2: Infrastructure adapters must not be referenced from the domain layer.
     * Domain depends on port interfaces, not implementations.
     */
    @Test
    void rule2_domainDoesNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.aiarchitect.terraquery.model..",
                        "com.aiarchitect.terraquery.port..",
                        "com.aiarchitect.terraquery.service.."
                )
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiarchitect.terraquery.adapter..",
                        "com.aiarchitect.terraquery.config..",
                        "com.aiarchitect.terraquery.streaming.."
                )
                .because("Domain must not depend on infrastructure — dependency direction is inward");

        rule.check(allClasses);
    }

    /**
     * Rule 3: Port interfaces must be interfaces, not abstract classes.
     * Ports define contracts; concrete behavior belongs in adapters.
     */
    @Test
    void rule3_portsMustBeInterfaces() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("com.aiarchitect.terraquery.port..")
                .should().beInterfaces()
                .because("Port definitions must be interfaces to allow multiple implementations");

        rule.check(allClasses);
    }

    /**
     * Rule 4: Inbound adapters (controllers) must not directly access outbound adapters
     * (agents, persistence). They must go through the domain use cases.
     */
    @Test
    void rule4_controllersAccessDomainOnlyViaUseCases() {
        ArchRule rule = noClasses()
                .that().resideInPackage("com.aiarchitect.terraquery.adapter.in..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiarchitect.terraquery.adapter.out..",
                        "com.aiarchitect.terraquery.service.."
                )
                .because("Inbound adapters must depend only on port interfaces, not service impl or outbound adapters");

        rule.check(allClasses);
    }

    /**
     * Rule 5: No circular dependencies between packages.
     */
    @Test
    void rule5_noCircularDependencies() {
        ArchRule rule = slices()
                .matching("com.aiarchitect.terraquery.(*)..")
                .should().beFreeOfCycles()
                .because("Circular package dependencies cause fragile builds and hidden coupling");

        rule.check(allClasses);
    }
}
