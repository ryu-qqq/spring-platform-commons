package com.ryuqqq.platform.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 소비측 영속(adapter-out) 레이어 작성 컨벤션 ArchUnit 룰. <b>상대 패키지 매처</b>로 root 패키지 무관.
 *
 * <p>전달은 하이브리드 — {@code CRITICAL}({@link #NO_QUERYDSL_OUTSIDE_ADAPTER_OUT})은 게이트로 막고,
 * 나머지(HIGH/MEDIUM/LOW)는 {@link DomainHealthReporter}로 진단·감점한다.
 */
public final class PersistenceConventionRules {

    private PersistenceConventionRules() {}

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String ADAPTER_IN = "..adapter.in..";
    private static final String ADAPTER_OUT = "..adapter.out..";

    /** QueryDSL·JPA 영속 스택은 adapter-out 안에서만 사용한다(게이트). */
    @ArchTest
    public static final ArchRule NO_QUERYDSL_OUTSIDE_ADAPTER_OUT =
            noClasses()
                    .that()
                    .resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER_IN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.querydsl..", "jakarta.persistence..", "org.hibernate..")
                    .as("NO_QUERYDSL_OUTSIDE_ADAPTER_OUT")
                    .because("QueryDSL·JPA 영속 스택은 adapter-out 영속 레이어에서만 사용한다")
                    .allowEmptyShould(true);
}
