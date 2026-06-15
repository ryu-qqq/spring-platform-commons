package com.ryuqqq.platform.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 공유 헥사고날 ArchUnit 규칙. <b>root 패키지에 무관한 상대 패키지 매처</b>({@code ..domain..} 등)로 작성되어
 * 서버({@code com.ryuqq.<service>})·platform 어디서나 동일하게 적용된다.
 *
 * <p>소비 예 (그린필드, strict 한 줄):
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.ryuqq.newservice")
 * class HexagonalArchitectureTest {
 *     @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRules.class);
 * }
 * }</pre>
 *
 * <p>레거시 위반이 있는 기존 레포는 {@link HexagonalArchRulesFrozen}(frozen ratchet)을 쓴다.
 * 개별 규칙 상수를 직접 참조하는 방식도 그대로 동작한다.
 */
public final class HexagonalArchRules {

    private HexagonalArchRules() {}

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String ADAPTER_IN = "..adapter.in..";
    private static final String ADAPTER_OUT = "..adapter.out..";
    private static final String BOOTSTRAP = "..bootstrap..";

    /** 도메인은 프레임워크 비의존(순수 자바)이어야 한다. */
    @ArchTest
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
    @ArchTest
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
    @ArchTest
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
