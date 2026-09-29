package pe.edu.nova.java.libs.secrets;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pe.edu.nova.java.libs.secrets");

    @Test
    void theContractDoesNotDependOnAnyFramework() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "io.quarkus..", "io.micronaut..", "jakarta.inject..")
                .because("ADR-015: a level 1 library must work in every framework")
                .check(classes);
    }

    @Test
    void jacksonStaysAnImplementationDetailOfTheSecret() {
        noClasses()
                .that()
                .doNotHaveFullyQualifiedName(Secret.class.getName())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.fasterxml.jackson..")
                .because("ADR-042: only Secret.fromJson() opens a JSON secret, so the parser can change")
                .check(classes);
    }
}
