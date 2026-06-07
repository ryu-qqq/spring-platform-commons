package com.ryuqqq.platform.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 공유 헥사고날 ArchUnit 규칙. <b>root 패키지에 무관한 상대 패키지 매처</b>({@code ..domain..} 등)로 작성되어
 * 서버({@code com.ryuqq.<service>})·platform 어디서나 동일하게 적용된다.
 *
 * <p>소비 예 (소비 레포의 테스트):
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.ryuqq.marketplace")
 * class HexagonalArchitectureTest {
 *     @ArchTest static final ArchRule layers       = HexagonalArchRules.HEXAGONAL_LAYERS;
 *     @ArchTest static final ArchRule domainPure   = HexagonalArchRules.DOMAIN_FRAMEWORK_FREE;
 *     @ArchTest static final ArchRule appIsolated  = HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE;
 * }
 * }</pre>
 *
 * <p>위반 시 빌드(test)가 실패한다 = Enforce. 점진 도입은 소비측이 규칙을 하나씩 추가하거나
 * {@code @ArchIgnore}로 일시 보류하는 식으로 조절한다.
 */
public final class HexagonalArchRules {

    private HexagonalArchRules() {}

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String ADAPTER_IN = "..adapter.in..";
    private static final String ADAPTER_OUT = "..adapter.out..";
    private static final String BOOTSTRAP = "..bootstrap..";

    /** 도메인은 프레임워크 비의존(순수 자바)이어야 한다. */
    public static final ArchRule DOMAIN_FRAMEWORK_FREE =
            noClasses()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "org.hibernate..",
                            "com.fasterxml.jackson..",
                            "jakarta.servlet..",
                            "com.querydsl..")
                    .as("도메인은 프레임워크에 의존하지 않는다")
                    .because("헥사고날: 도메인은 Spring/JPA/Jackson/Servlet을 몰라야 한다")
                    .allowEmptyShould(true);

    /** 애플리케이션은 웹/영속 스택에 직접 의존하지 않는다 (포트로만 통신). */
    public static final ArchRule APPLICATION_NO_WEB_OR_PERSISTENCE =
            noClasses()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "jakarta.servlet..",
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "org.hibernate..",
                            "org.springframework.data.jpa..",
                            "com.querydsl..")
                    .as("애플리케이션은 웹/영속 스택에 직접 의존하지 않는다")
                    .because("헥사고날: application은 포트로만 바깥과 통신한다")
                    .allowEmptyShould(true);

    /** 헥사고날 레이어 의존 방향 — 의존은 안쪽으로만(adapter→application→domain, bootstrap만 조립 루트). */
    public static final ArchRule HEXAGONAL_LAYERS =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy(DOMAIN)
                    .layer("Application")
                    .definedBy(APPLICATION)
                    .layer("AdapterIn")
                    .definedBy(ADAPTER_IN)
                    .layer("AdapterOut")
                    .definedBy(ADAPTER_OUT)
                    .layer("Bootstrap")
                    .definedBy(BOOTSTRAP)
                    .whereLayer("Bootstrap")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("AdapterIn")
                    .mayOnlyBeAccessedByLayers("Bootstrap")
                    .whereLayer("AdapterOut")
                    .mayOnlyBeAccessedByLayers("Bootstrap")
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("AdapterIn", "AdapterOut", "Bootstrap")
                    .whereLayer("Domain")
                    .mayOnlyBeAccessedByLayers("Application", "AdapterIn", "AdapterOut", "Bootstrap")
                    .withOptionalLayers(true)
                    .as("헥사고날 레이어 의존 방향")
                    .because("의존은 안쪽으로만: adapter→application→domain, bootstrap만 조립 루트");
}
