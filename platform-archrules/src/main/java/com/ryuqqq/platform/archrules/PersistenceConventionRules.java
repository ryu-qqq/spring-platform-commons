package com.ryuqqq.platform.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

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

    /** Repository 인터페이스가 직접 선언한 메서드는 save/saveAll만 허용한다. */
    private static final ArchCondition<JavaClass> ONLY_DECLARE_COMMAND_METHODS =
            new ArchCondition<>("only declare save/saveAll methods") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (JavaMethod method : item.getMethods()) {
                        String name = method.getName();
                        boolean isCommand = name.equals("save") || name.equals("saveAll");
                        if (!isCommand) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            method,
                                            method.getFullName()
                                                    + " declares non-command method '"
                                                    + name
                                                    + "' (save/saveAll만 허용)"));
                        }
                    }
                }
            };

    /** R: adapter-out의 *Repository 인터페이스는 save/saveAll만 직접 선언한다(조회는 QueryDSL). */
    public static final ArchRule REPOSITORY_COMMAND_ONLY =
            classes()
                    .that()
                    .resideInAPackage(ADAPTER_OUT)
                    .and()
                    .areInterfaces()
                    .and()
                    .haveSimpleNameEndingWith("Repository")
                    .should(ONLY_DECLARE_COMMAND_METHODS)
                    .as("REPOSITORY_COMMAND_ONLY")
                    .because("Repository 인터페이스는 순수 저장 기능만 — 조회/파생 쿼리는 QueryDSL로 분리한다")
                    .allowEmptyShould(true);

    /** QueryDSL 조건 타입(BooleanExpression/Predicate)을 반환하는 메서드. */
    private static final DescribedPredicate<JavaMethod> RETURNS_QUERYDSL_CONDITION =
            new DescribedPredicate<>("returns a QueryDSL BooleanExpression/Predicate") {
                private final Set<String> conditionTypes =
                        Set.of(
                                "com.querydsl.core.types.dsl.BooleanExpression",
                                "com.querydsl.core.types.Predicate");

                @Override
                public boolean test(JavaMethod method) {
                    return conditionTypes.contains(method.getRawReturnType().getFullName());
                }
            };

    /** R: QueryDSL 조건 조립은 *ConditionBuilder 타입 안에서만 한다(조건 로직 캡슐화). */
    public static final ArchRule CONDITION_LOGIC_IN_BUILDER =
            noMethods()
                    .that(RETURNS_QUERYDSL_CONDITION)
                    .should()
                    .beDeclaredInClassesThat()
                    .haveSimpleNameNotEndingWith("ConditionBuilder")
                    .as("CONDITION_LOGIC_IN_BUILDER")
                    .because("QueryDSL 동적 조건은 흩뿌리지 않고 *ConditionBuilder에 캡슐화한다")
                    .allowEmptyShould(true);

    /** R: @Entity 클래스는 platform BaseAuditEntity 계열을 상속한다(감사·soft-delete 일관성). */
    public static final ArchRule JPA_ENTITY_EXTENDS_BASE =
            classes()
                    .that()
                    .areAnnotatedWith("jakarta.persistence.Entity")
                    .should()
                    .beAssignableTo("com.ryuqqq.platform.persistence.jpa.entity.BaseAuditEntity")
                    .as("JPA_ENTITY_EXTENDS_BASE")
                    .because("@Entity는 BaseAuditEntity 계열을 상속해 감사/soft-delete를 일관 적용한다")
                    .allowEmptyShould(true);

    /** QueryDSL·JPA 영속 스택은 adapter-out 안에서만 사용한다(게이트). */
    @ArchTest
    public static final ArchRule NO_QUERYDSL_OUTSIDE_ADAPTER_OUT =
            noClasses()
                    .that()
                    .resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER_IN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.querydsl..", "jakarta.persistence..", "org.hibernate..")
                    .as("NO_QUERYDSL_OUTSIDE_ADAPTER_OUT")
                    .because("QueryDSL·JPA 영속 스택은 adapter-out 영속 레이어에서만 사용한다")
                    .allowEmptyShould(true);

    /**
     * 건강 리포터가 쓰는 감점 룰 + 심각도. 게이트({@link #NO_QUERYDSL_OUTSIDE_ADAPTER_OUT})는 빌드 게이트가 담당하므로 제외한다(도메인
     * 룰과 동일 패턴).
     */
    public static List<DomainRule> all() {
        return List.of(
                new DomainRule("REPOSITORY_COMMAND_ONLY", REPOSITORY_COMMAND_ONLY, Severity.HIGH),
                new DomainRule(
                        "CONDITION_LOGIC_IN_BUILDER", CONDITION_LOGIC_IN_BUILDER, Severity.MEDIUM),
                new DomainRule("JPA_ENTITY_EXTENDS_BASE", JPA_ENTITY_EXTENDS_BASE, Severity.LOW));
    }
}
