package com.ryuqqq.platform.archrules;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 소비측 도메인 레이어 작성 컨벤션 ArchUnit 룰 (자작 — marketplace 코드 귀납). <b>상대 패키지 매처</b>
 * ({@code ..domain..}·{@code ..aggregate..} 등)로 root 패키지 무관하게 적용된다.
 *
 * <p>전달은 하이브리드다 — {@code CRITICAL}(프레임워크 의존·Lombok)은 {@link HexagonalArchRules}가
 * 빌드 게이트로 막고, 여기 룰(HIGH/MEDIUM/LOW)은 {@link DomainHealthReporter}로 진단·감점한다.
 * 출처: 우형 근거는 계층 원칙뿐이며, 작성 룰은 자작이다.
 */
public final class DomainConventionRules {

    private DomainConventionRules() {}

    private static final String DOMAIN = "..domain..";
    private static final String AGGREGATE = "..aggregate..";
    private static final String VO = "..vo..";
    private static final String ID = "..id..";
    private static final String EXCEPTION = "..exception..";
    private static final String QUERY = "..query..";

    // ── 커스텀 술어·조건 (룰보다 먼저 선언: static 초기화 순서) ──────────────

    /** 시간 직접 읽기 호출(now/systemUTC/currentTimeMillis 등). */
    private static final DescribedPredicate<JavaMethodCall> TIME_NOW_CALL =
            new DescribedPredicate<>("현재 시각을 직접 읽는 호출") {
                private final Map<String, Set<String>> banned =
                        Map.of(
                                "java.time.Instant", Set.of("now"),
                                "java.time.LocalDateTime", Set.of("now"),
                                "java.time.LocalDate", Set.of("now"),
                                "java.time.LocalTime", Set.of("now"),
                                "java.time.ZonedDateTime", Set.of("now"),
                                "java.time.OffsetDateTime", Set.of("now"),
                                "java.time.Year", Set.of("now"),
                                "java.time.Clock", Set.of("systemUTC", "systemDefaultZone", "system"),
                                "java.lang.System", Set.of("currentTimeMillis", "nanoTime"));

                @Override
                public boolean test(JavaMethodCall call) {
                    String owner = call.getTargetOwner().getFullName();
                    Set<String> names = banned.get(owner);
                    return names != null && names.contains(call.getName());
                }
            };

    /**
     * 표준 raw 컬렉션 타입(java.util Collection·Map)만. {@code java.util..} 한정으로, 컬렉션을
     * 구현한 커스텀 일급 컬렉션 VO(예: {@code OrderItems implements Collection})는 오탐하지 않는다.
     */
    private static final DescribedPredicate<JavaClass> RAW_COLLECTION_TYPE =
            assignableTo(java.util.Collection.class)
                    .or(assignableTo(java.util.Map.class))
                    .and(resideInAPackage("java.util.."))
                    .as("표준 raw 컬렉션 타입(java.util Collection·Map)");

    private static ArchCondition<JavaClass> beRecord(boolean expected) {
        String desc = expected ? "be a record" : "not be a record";
        return new ArchCondition<>(desc) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean isRecord = item.isRecord();
                boolean satisfied = isRecord == expected;
                events.add(
                        new SimpleConditionEvent(
                                item,
                                satisfied,
                                item.getDescription() + (isRecord ? " is a record" : " is not a record")));
            }
        };
    }

    private static final ArchCondition<JavaClass> BE_RECORD = beRecord(true);
    private static final ArchCondition<JavaClass> NOT_BE_RECORD = beRecord(false);

    private static final ArchCondition<JavaClass> BE_ENUM =
            new ArchCondition<>("be an enum") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean isEnum = item.isEnum();
                    events.add(
                            new SimpleConditionEvent(
                                    item,
                                    isEnum,
                                    item.getDescription() + (isEnum ? " is an enum" : " is not an enum")));
                }
            };

    // ── Tier 1 ───────────────────────────────────────────────────────────────

    /** R1. 도메인은 현재 시각을 직접 호출하지 않고 주입받는다. */
    public static final ArchRule NO_TIME_IN_DOMAIN =
            noClasses()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .callMethodWhere(TIME_NOW_CALL)
                    .as("NO_TIME_IN_DOMAIN")
                    .because("도메인은 Instant.now() 등 현재 시각을 직접 읽지 않고 주입받는다")
                    .allowEmptyShould(true);

    /** R2. 도메인 타입은 불변 — setter 금지. */
    public static final ArchRule NO_SETTERS_IN_DOMAIN =
            noMethods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .haveNameStartingWith("set")
                    .as("NO_SETTERS_IN_DOMAIN")
                    .because("도메인 상태 변경은 set* 가 아니라 비즈니스 메서드로만 한다")
                    .allowEmptyShould(true);

    /** R3. 도메인 예외는 공통 {@code DomainException}을 상속한다(FQN 문자열 참조). */
    public static final ArchRule DOMAIN_EXCEPTIONS_EXTEND_BASE =
            classes()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .and()
                    .haveSimpleNameEndingWith("Exception")
                    .should()
                    .beAssignableTo("com.ryuqqq.platform.common.exception.DomainException")
                    .as("DOMAIN_EXCEPTIONS_EXTEND_BASE")
                    .because("도메인 예외는 DomainException 베이스를 상속한다")
                    .allowEmptyShould(true);

    // ── Tier 2 ───────────────────────────────────────────────────────────────

    /** R5. Aggregate Root는 record가 아니라 class. */
    public static final ArchRule AGGREGATE_IS_CLASS =
            classes()
                    .that()
                    .resideInAPackage(AGGREGATE)
                    .should(NOT_BE_RECORD)
                    .as("AGGREGATE_IS_CLASS")
                    .because("Aggregate Root는 정체성·가변 전이를 위해 record가 아니라 class다")
                    .allowEmptyShould(true);

    /** R6. Aggregate 생성자는 public 금지(정적 팩토리 강제). */
    public static final ArchRule AGGREGATE_CTORS_NOT_PUBLIC =
            constructors()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(AGGREGATE)
                    .should()
                    .notBePublic()
                    .as("AGGREGATE_CTORS_NOT_PUBLIC")
                    .because("외부 생성은 정적 팩토리(forNew/reconstitute)로만 한다")
                    .allowEmptyShould(true);

    /** R7. 일급 컬렉션 — Aggregate는 raw Collection/Map을 필드로 들지 않는다. */
    public static final ArchRule NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE =
            noFields()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(AGGREGATE)
                    .should()
                    .haveRawType(RAW_COLLECTION_TYPE)
                    .as("NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE")
                    .because("컬렉션은 일급 컬렉션 Wrapper VO로 감싼다")
                    .allowEmptyShould(true);

    /** R8. VO의 public 필드는 상수(static final)만 허용 — public static 가변·public 인스턴스 필드 금지. */
    public static final ArchRule VO_FIELDS_NOT_PUBLIC =
            fields()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage(VO)
                    .and()
                    .arePublic()
                    .should()
                    .beStatic()
                    .andShould()
                    .beFinal()
                    .as("VO_FIELDS_NOT_PUBLIC")
                    .because("VO는 accessor로만 노출 — public 필드는 상수(static final)만 허용")
                    .allowEmptyShould(true);

    /** R9. Identifier VO는 record. */
    public static final ArchRule ID_TYPES_ARE_RECORDS =
            classes()
                    .that()
                    .resideInAPackage(ID)
                    .should(BE_RECORD)
                    .as("ID_TYPES_ARE_RECORDS")
                    .because("Identifier VO는 record(불변·값 동등성)")
                    .allowEmptyShould(true);

    /** R10. {@code *ErrorCode}는 enum. */
    public static final ArchRule ERRORCODE_TYPES_ARE_ENUMS =
            classes()
                    .that()
                    .resideInAPackage(EXCEPTION)
                    .and()
                    .haveSimpleNameEndingWith("ErrorCode")
                    .should(BE_ENUM)
                    .as("ERRORCODE_TYPES_ARE_ENUMS")
                    .because("ErrorCode는 enum implements ErrorCode")
                    .allowEmptyShould(true);

    /** R11. {@code *SortKey}·{@code *SearchField}는 enum. */
    public static final ArchRule SORT_SEARCH_KEYS_ARE_ENUMS =
            classes()
                    .that()
                    .resideInAPackage(QUERY)
                    .and(simpleNameEndingWith("SortKey").or(simpleNameEndingWith("SearchField")))
                    .should(BE_ENUM)
                    .as("SORT_SEARCH_KEYS_ARE_ENUMS")
                    .because("SortKey/SearchField는 도메인 어휘 enum")
                    .allowEmptyShould(true);

    /** R12. 도메인 클래스는 aggregate/vo/id/exception/query 슬라이스에만(.event 금지). */
    public static final ArchRule DOMAIN_PACKAGE_SLICES =
            classes()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .resideInAnyPackage(
                            "..domain.aggregate..",
                            "..domain.vo..",
                            "..domain.id..",
                            "..domain.exception..",
                            "..domain.query..")
                    .as("DOMAIN_PACKAGE_SLICES")
                    .because("도메인은 aggregate/vo/id/exception/query 슬라이스만 — Domain Event 미사용")
                    .allowEmptyShould(true);

    /** 리포터·게이트가 쓰는 전체 룰 + 심각도. */
    public static List<DomainRule> all() {
        return List.of(
                new DomainRule("NO_TIME_IN_DOMAIN", NO_TIME_IN_DOMAIN, Severity.HIGH),
                new DomainRule("DOMAIN_EXCEPTIONS_EXTEND_BASE", DOMAIN_EXCEPTIONS_EXTEND_BASE, Severity.HIGH),
                new DomainRule("DOMAIN_PACKAGE_SLICES", DOMAIN_PACKAGE_SLICES, Severity.HIGH),
                new DomainRule("NO_SETTERS_IN_DOMAIN", NO_SETTERS_IN_DOMAIN, Severity.MEDIUM),
                new DomainRule("AGGREGATE_IS_CLASS", AGGREGATE_IS_CLASS, Severity.MEDIUM),
                new DomainRule("AGGREGATE_CTORS_NOT_PUBLIC", AGGREGATE_CTORS_NOT_PUBLIC, Severity.MEDIUM),
                new DomainRule(
                        "NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE",
                        NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE,
                        Severity.MEDIUM),
                new DomainRule("VO_FIELDS_NOT_PUBLIC", VO_FIELDS_NOT_PUBLIC, Severity.MEDIUM),
                new DomainRule("ID_TYPES_ARE_RECORDS", ID_TYPES_ARE_RECORDS, Severity.LOW),
                new DomainRule("ERRORCODE_TYPES_ARE_ENUMS", ERRORCODE_TYPES_ARE_ENUMS, Severity.LOW),
                new DomainRule("SORT_SEARCH_KEYS_ARE_ENUMS", SORT_SEARCH_KEYS_ARE_ENUMS, Severity.LOW));
    }
}
