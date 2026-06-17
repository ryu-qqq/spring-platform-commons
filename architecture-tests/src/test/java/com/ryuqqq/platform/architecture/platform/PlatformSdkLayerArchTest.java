package com.ryuqqq.platform.architecture.platform;

import static com.ryuqqq.platform.architecture.support.ArchitectureRules.allPackages;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.COMMON_DOMAIN_ALLOWED_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.OBSERVABILITY_PACKAGES_FORBIDDEN_IN_DOMAIN;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PERSISTENCE_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PLATFORM_ADAPTER_IN_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ArchitectureRules.PLATFORM_BOOTSTRAP_PACKAGES;
import static com.ryuqqq.platform.architecture.support.ModuleClasses.importProductionClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Platform SDK module layer dependency smoke tests.
 *
 * <p>Maps publishable SDK modules to hexagonal layers: common-domain (domain), common-application
 * (application), platform-web (adapter-in), platform-bootstrap (bootstrap).
 */
@DisplayName("Platform SDK hexagonal layer dependency rules")
class PlatformSdkLayerArchTest {

    private static JavaClasses commonDomainClasses;
    private static JavaClasses commonApplicationClasses;
    private static JavaClasses platformObservabilityClasses;
    private static JavaClasses platformWebClasses;
    private static JavaClasses platformBootstrapClasses;
    private static JavaClasses platformPersistenceJpaClasses;

    @BeforeAll
    static void setUp() {
        commonDomainClasses = importProductionClasses("platform-common-domain");
        commonApplicationClasses = importProductionClasses("platform-common-application");
        platformObservabilityClasses = importProductionClasses("platform-observability");
        platformWebClasses = importProductionClasses("platform-web");
        platformBootstrapClasses = importProductionClasses("platform-bootstrap");
        platformPersistenceJpaClasses = importProductionClasses("platform-persistence-jpa");
    }

    @Nested
    @DisplayName("platform-observability (Independent layer)")
    class ObservabilityRules {

        @Test
        @DisplayName("must not depend on Spring, JPA, or Jackson")
        void observability_MustNotDependOnFrameworks() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN))
                            .allowEmptyShould(true)
                            .because(
                                    "platform-observability is a zero-dependency vocabulary SSOT — no framework deps (ADR-0006)");

            rule.check(platformObservabilityClasses);
        }

        @Test
        @DisplayName("must not depend on any other platform SDK module")
        void observability_MustNotDependOnOtherModules() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            new String[] {"com.ryuqqq.platform.common.."},
                                            PLATFORM_ADAPTER_IN_PACKAGES,
                                            PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Independent layer must not depend on other SDK modules (ADR-0006)");

            rule.check(platformObservabilityClasses);
        }
    }

    @Nested
    @DisplayName("platform-common-domain (domain layer)")
    class CommonDomainRules {

        @Test
        @DisplayName("must not depend on Spring, JPA, Jackson, or logging/metrics")
        void commonDomain_MustNotDependOnFrameworks() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN,
                                            OBSERVABILITY_PACKAGES_FORBIDDEN_IN_DOMAIN))
                            .allowEmptyShould(true)
                            .because(
                                    "platform-common-domain is pure Java — no framework/observability deps (ADR-0006)");

            rule.check(commonDomainClasses);
        }

        @Test
        @DisplayName("입주 기준 — vo·exception·domain 패키지에만 거주(횡단 인프라 어휘 금지)")
        void commonDomain_OnlyAllowedPackages() {
            ArchRule rule =
                    classes()
                            .that()
                            .resideInAPackage("com.ryuqqq.platform.common..")
                            .should()
                            .resideInAnyPackage(allPackages(COMMON_DOMAIN_ALLOWED_PACKAGES))
                            .allowEmptyShould(true)
                            .as("platform-common-domain 입주 기준")
                            .because(
                                    "도메인 커널은 vo·exception·domain 만 — 횡단 인프라 어휘(로깅·헤더·메트릭)는 금지."
                                            + " 새 패키지 입주는 ADR로 허용목록을 갱신해야 한다 (ADR-0006)");

            rule.check(commonDomainClasses);
        }

        @Test
        @DisplayName("must not depend on application, adapter, or bootstrap SDK layers")
        void commonDomain_MustNotDependOnOuterLayers() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            new String[] {
                                                "com.ryuqqq.platform.common.factory..",
                                                "com.ryuqqq.platform.common.component..",
                                                "com.ryuqqq.platform.common.port.."
                                            },
                                            PLATFORM_ADAPTER_IN_PACKAGES,
                                            PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Domain layer must not depend on outer SDK layers");

            rule.check(commonDomainClasses);
        }
    }

    @Nested
    @DisplayName("platform-common-application (application layer)")
    class CommonApplicationRules {

        @Test
        @DisplayName("must not depend on adapter-in or bootstrap SDK layers")
        void commonApplication_MustNotDependOnAdaptersOrBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(PLATFORM_ADAPTER_IN_PACKAGES, PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Application layer must not depend on adapter-in or bootstrap (wiki overview)");

            rule.check(commonApplicationClasses);
        }

        @Test
        @DisplayName("must not depend on persistence or Spring Web")
        void commonApplication_MustNotDependOnPersistenceOrWeb() {
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
                            .because("Application must not reach persistence or web stack (wiki layers/application)");

            rule.check(commonApplicationClasses);
        }
    }

    @Nested
    @DisplayName("platform-web (adapter-in layer)")
    class PlatformWebRules {

        @Test
        @DisplayName("must not depend on persistence or adapter-out")
        void platformWeb_MustNotDependOnPersistenceOrAdapterOut() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            PERSISTENCE_PACKAGES,
                                            new String[] {"com.ryuqqq.platform.template.adapter.out.."}))
                            .allowEmptyShould(true)
                            .because("Adapter-In must not depend on persistence or adapter-out (wiki overview)");

            rule.check(platformWebClasses);
        }

        @Test
        @DisplayName("must not depend on bootstrap assembly SDK")
        void platformWeb_MustNotDependOnBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Adapter-In must not depend on bootstrap assembly");

            rule.check(platformWebClasses);
        }

        @Test
        @DisplayName("may depend on platform-common-domain shared VOs (SDK exception to strict Port-In-only rule)")
        void platformWeb_MayDependOnCommonDomain() {
            // Documented pragmatic exception: adapter-in web SDK shares domain VOs for ApiResponse envelopes.
            // Strict wiki rule (adapter-in → application only) applies to template business modules.
            org.assertj.core.api.Assertions.assertThat(platformWebClasses).isNotNull();
        }
    }

    @Nested
    @DisplayName("platform-bootstrap (bootstrap layer)")
    class PlatformBootstrapRules {

        @Test
        @DisplayName("inner SDK layers must not depend on platform-bootstrap")
        void innerLayers_MustNotDependOnPlatformBootstrap() {
            ArchRule rule =
                    noClasses()
                            .that()
                            .resideInAnyPackage(
                                    "com.ryuqqq.platform.common..",
                                    "com.ryuqqq.platform.web..",
                                    "com.ryuqqq.platform.template..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(allPackages(PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Only runnable bootstrap apps assemble platform-bootstrap — inner layers must not");

            rule.check(commonDomainClasses);
            rule.check(commonApplicationClasses);
            rule.check(platformWebClasses);
        }

        @Test
        @DisplayName("platform-bootstrap module is registered and importable")
        void platformBootstrap_ModuleIsPresent() {
            org.assertj.core.api.Assertions.assertThat(platformBootstrapClasses).isNotNull();
        }
    }

    @Nested
    @DisplayName("platform-persistence-jpa (adapter-out layer)")
    class PlatformPersistenceJpaRules {

        @Test
        @DisplayName("must not depend on adapter-in or bootstrap SDK layers")
        void persistenceJpa_MustNotDependOnAdapterInOrBootstrap() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(PLATFORM_ADAPTER_IN_PACKAGES, PLATFORM_BOOTSTRAP_PACKAGES))
                            .allowEmptyShould(true)
                            .because("Adapter-out JPA SDK must not depend on adapter-in or bootstrap");

            rule.check(platformPersistenceJpaClasses);
        }

        @Test
        @DisplayName("must not depend on application layer SDK")
        void persistenceJpa_MustNotDependOnApplicationLayer() {
            ArchRule rule =
                    noClasses()
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(
                                    allPackages(
                                            new String[] {
                                                "com.ryuqqq.platform.common.factory..",
                                                "com.ryuqqq.platform.common.component..",
                                                "com.ryuqqq.platform.common.port.."
                                            }))
                            .allowEmptyShould(true)
                            .because("Adapter-out must not depend on application layer (wiki overview)");

            rule.check(platformPersistenceJpaClasses);
        }

        @Test
        @DisplayName("platform-persistence-jpa module is registered and importable")
        void platformPersistenceJpa_ModuleIsPresent() {
            org.assertj.core.api.Assertions.assertThat(platformPersistenceJpaClasses).isNotEmpty();
        }
    }
}
