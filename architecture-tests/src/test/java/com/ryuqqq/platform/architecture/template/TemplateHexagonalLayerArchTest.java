package com.ryuqqq.platform.architecture.template;

import static com.ryuqqq.platform.architecture.support.ArchitectureRules.allPackages;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.APPLICATION_LAYER_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.DOMAIN_LAYER_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PERSISTENCE_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PLATFORM_ADAPTER_IN_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PLATFORM_BOOTSTRAP_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.TEMPLATE_ADAPTER_IN_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.TEMPLATE_ADAPTER_OUT_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ModuleClasses.importProductionClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Hexagonal template module layer dependency smoke tests.
 *
 * <p>Wiki: {@code wiki/conventions/java-springboot-hexagonal/_overview.md} — module-scoped imports
 * because template layers share {@code com.ryuqqq.platform.template} base packages.
 */
@DisplayName("Template hexagonal layer dependency rules")
class TemplateHexagonalLayerArchTest {

    private static JavaClasses domainClasses;
    private static JavaClasses applicationClasses;
    private static JavaClasses adapterInClasses;
    private static JavaClasses adapterOutClasses;
    private static JavaClasses bootstrapClasses;

    @BeforeAll
    static void setUp() {
        domainClasses = importProductionClasses("domain");
        applicationClasses = importProductionClasses("application");
        adapterInClasses = importProductionClasses("adapter-in/rest-api");
        adapterOutClasses = importProductionClasses("adapter-out/client/example-client");
        bootstrapClasses = importProductionClasses("bootstrap/bootstrap-web-api");
    }

    @Nested
    @DisplayName("Domain (:domain)")
    class DomainLayerRules {

        @Test
        @DisplayName("must not depend on Spring, JPA, Jackson, or other frameworks")
        void domain_MustNotDependOnFrameworks() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN))
                            .allowEmptyShould(true)
                            .because("Domain is pure Java — no Spring/JPA/Jackson (wiki layers/domain)");

            rule.check(domainClasses);
        }

        @Test
        @DisplayName("must not depend on application, adapter, or bootstrap layers")
        void domain_MustNotDependOnOuterLayers() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            APPLICATION_LAYER_PACKAGES,
                                            TEMPLATE_ADAPTER_IN_PACKAGES,
                                            TEMPLATE_ADAPTER_OUT_PACKAGES,
                                            PLATFORM_ADAPTER_IN_PACKAGES,
                                            PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Domain must not depend on outer hexagonal layers (wiki overview matrix)");

            rule.check(domainClasses);
        }
    }

    @Nested
    @DisplayName("Application (:application)")
    class ApplicationLayerRules {

        @Test
        @DisplayName("must not depend on adapter-in, adapter-out, or bootstrap layers")
        void application_MustNotDependOnAdaptersOrBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            TEMPLATE_ADAPTER_IN_PACKAGES,
                                            TEMPLATE_ADAPTER_OUT_PACKAGES,
                                            PLATFORM_ADAPTER_IN_PACKAGES,
                                            PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Application depends on domain only — no adapter or bootstrap (wiki overview)");

            rule.check(applicationClasses);
        }

        @Test
        @DisplayName("must not depend on persistence or Spring Web")
        void application_MustNotDependOnPersistenceOrWeb() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            PERSISTENCE_PACKAGES,
                                            new String[] {
                                                "org.springframework.web..", "jakarta.servlet.."
                                            }))
                            .allowEmptyShould(true)
                            .because("Application must not reach persistence or web stack directly (wiki layers/application)");

            rule.check(applicationClasses);
        }
    }

    @Nested
    @DisplayName("Adapter-In (:adapter-in:rest-api)")
    class AdapterInLayerRules {

        @Test
        @DisplayName("must not depend on adapter-out or persistence")
        void adapterIn_MustNotDependOnAdapterOutOrPersistence() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(TEMPLATE_ADAPTER_OUT_PACKAGES, PERSISTENCE_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Adapter-In must not depend on adapter-out or persistence (wiki overview)");

            rule.check(adapterInClasses);
        }

        @Test
        @DisplayName("must not depend on domain aggregates directly")
        void adapterIn_MustNotDependOnDomainAggregates() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage("com.ryuqqq.platform.template..aggregate..")
                            .allowEmptyShould(true)
                            .because(
                                    "Adapter-In calls application UseCase only — no direct domain aggregate"
                                            + " (wiki layers/adapter-in)");

            rule.check(adapterInClasses);
        }

        @Test
        @DisplayName("must not depend on bootstrap assembly modules")
        void adapterIn_MustNotDependOnBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            new String[] {"com.ryuqqq.platform.template.bootstrap.."},
                                            PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Adapter-In must not depend on bootstrap assembly (wiki overview)");

            rule.check(adapterInClasses);
        }
    }

    @Nested
    @DisplayName("Adapter-Out (:adapter-out:client:example-client)")
    class AdapterOutLayerRules {

        @Test
        @DisplayName("must not depend on adapter-in or bootstrap")
        void adapterOut_MustNotDependOnAdapterInOrBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            TEMPLATE_ADAPTER_IN_PACKAGES,
                                            PLATFORM_ADAPTER_IN_PACKAGES,
                                            PLATFORM_BOOTSTRAP_PACKAGES,
                                            new String[] {"com.ryuqqq.platform.template.bootstrap.."}))
                            .allowEmptyShould(true)
                            .because("Adapter-Out must not depend on adapter-in or bootstrap (wiki overview)");

            rule.check(adapterOutClasses);
        }

        @Test
        @DisplayName("must not depend on domain aggregates directly")
        void adapterOut_MustNotDependOnDomainAggregates() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage("com.ryuqqq.platform.template..aggregate..")
                            .allowEmptyShould(true)
                            .because(
                                    "Adapter-Out implements application ports — no direct domain aggregate"
                                            + " (wiki layers/adapter-out)");

            rule.check(adapterOutClasses);
        }

        @Test
        @DisplayName("example-client module is registered and importable")
        void adapterOut_ModuleIsPresent() {
            org.assertj.core.api.Assertions.assertThat(adapterOutClasses).isNotNull();
        }
    }

    @Nested
    @DisplayName("Bootstrap (:bootstrap:bootstrap-web-api)")
    class BootstrapLayerRules {

        @Test
        @DisplayName("may depend on all layers — no inward dependency violations from outer layers")
        void bootstrap_IsAssemblyRoot() {
            ArchRule rule =
                    noClasses()
                            .that()
                            .resideInAnyPackage(
                                    allPackages(
                                            DOMAIN_LAYER_PACKAGES,
                                            APPLICATION_LAYER_PACKAGES,
                                            TEMPLATE_ADAPTER_IN_PACKAGES))
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage("com.ryuqqq.platform.template.bootstrap..")
                            .allowEmptyShould(true)
                            .because("Only bootstrap may be the assembly root — inner layers must not depend on it");

            rule.check(domainClasses);
            rule.check(applicationClasses);
            rule.check(adapterInClasses);
        }

        @Test
        @DisplayName("bootstrap module is registered and importable")
        void bootstrap_ModuleIsPresent() {
            // Smoke assertion: empty skeleton still produces a valid (possibly empty) class set.
            org.assertj.core.api.Assertions.assertThat(bootstrapClasses).isNotNull();
        }
    }
}
