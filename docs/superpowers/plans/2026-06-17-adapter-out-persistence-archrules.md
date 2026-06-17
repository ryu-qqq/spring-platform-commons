# AdapterOut Persistence ArchUnit 룰 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** platform-archrules에 영속(adapter-out) 레이어 컨벤션 룰 4종을 `PersistenceConventionRules` 클래스로 추가하고, 도메인 룰과 동일한 하이브리드(게이트 1 + 감점 3) 전달 방식으로 검증한다.

**Architecture:** 기존 `DomainConventionRules`를 미러한 단일 클래스 `PersistenceConventionRules` 추가. CRITICAL 게이트 룰은 `@ArchTest` 상수로 노출, HIGH/MEDIUM/LOW 감점 룰은 `all()`로 묶어 기존 범용 `DomainHealthReporter`에 위임. compliant/violation fixture 미러 테스트로 각 룰을 GREEN/RED 검증.

**Tech Stack:** Java, ArchUnit(`com.tngtech.archunit`), JUnit5, AssertJ. fixture 컴파일용으로 `platform-persistence-jpa`를 test 의존에 추가(querydsl·jakarta.persistence·BaseAuditEntity 제공).

## Global Constraints

- **production 의존성 추가 0** — 룰의 모든 외부 타입(`com.querydsl..`·`jakarta.persistence..`)은 ArchUnit FQN 문자열 매처로만 참조. `platform-persistence-jpa`는 **`testImplementation`** 으로만 추가.
- **root-패키지 무관** — 모든 룰은 상대 패키지 매처(`..domain..`·`..adapter.out..` 등)로 작성. 절대 root 패키지(`com.ryuqqq.myservice`)를 박지 않는다.
- **인큐베이터 표기 `com.ryuqqq`** — FQN 문자열은 `com.ryuqqq.*`로 작성. `tools/promote-module.sh`가 승격 시 `com.connectly`로 치환한다.
- **패키지 상수 재사용** — `DOMAIN="..domain.."`, `APPLICATION="..application.."`, `ADAPTER_IN="..adapter.in.."`, `ADAPTER_OUT="..adapter.out.."` (HexagonalArchRules와 동일 표기).
- **`allowEmptyShould(true)`** — 모든 룰에 필수(빈 매칭 시 통과).
- **테스트 import 주의** — `ClassFileImporter`는 `DO_NOT_INCLUDE_TESTS` 옵션을 쓰지 않는다(fixture가 test 소스셋에 있어 전부 제외됨).

---

## File Structure

- **Create** `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java` — 룰 4종 + `all()`.
- **Modify** `platform-archrules/build.gradle` — `testImplementation project(':platform-persistence-jpa')` 추가.
- **Create** test fixtures under `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/fixture/persistenceconv/{compliant,violation}/svc/...`
- **Create** `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/PersistenceConventionRulesTest.java` — compliant/violation 미러 테스트 + 게이트 + 점수 결정성.

기존 `DomainRule`·`Severity`·`Finding`·`HealthReport`·`DomainHealthReporter`는 **재사용**(수정 없음).

참고 — 재사용 타입 시그니처(기존 코드, 변경 금지):
- `record DomainRule(String id, ArchRule rule, Severity severity)` — 접근자 `id()`·`rule()`·`severity()`.
- `enum Severity { CRITICAL(25), HIGH(10), MEDIUM(5), LOW(2) }` — `weight()`.
- `DomainHealthReporter.report(JavaClasses classes, List<DomainRule> rules) -> HealthReport`.
- `HealthReport` — `score()`·`isHealthy()`·`findings()`·`toJson()`; `Finding` — `ruleId()`·`severity()`·`detail()`.

---

## Task 1: 게이트 룰 NO_QUERYDSL_OUTSIDE_ADAPTER_OUT + 빌드 의존성 + fixture 골격

**Files:**
- Modify: `platform-archrules/build.gradle`
- Create: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java`
- Create: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/fixture/persistenceconv/compliant/svc/application/GoodAppService.java`
- Create: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/fixture/persistenceconv/violation/svc/application/LeakyAppService.java`
- Create: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/PersistenceConventionRulesTest.java`

**Interfaces:**
- Consumes: (없음 — 첫 태스크)
- Produces: `PersistenceConventionRules.NO_QUERYDSL_OUTSIDE_ADAPTER_OUT` (`public static final ArchRule`, `@ArchTest`). 패키지 상수 `DOMAIN`·`APPLICATION`·`ADAPTER_IN`·`ADAPTER_OUT` (`private static final String`).

- [ ] **Step 1: build.gradle에 test 의존 추가**

`platform-archrules/build.gradle`의 `dependencies` 블록, `testImplementation project(':platform-common-domain')` 다음 줄에 추가:

```groovy
    testImplementation project(':platform-persistence-jpa')
```

- [ ] **Step 2: fixture 작성 (compliant application — QueryDSL 미사용)**

`.../fixture/persistenceconv/compliant/svc/application/GoodAppService.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.application;

/** application 레이어 — 영속/QueryDSL 스택에 의존하지 않는다(컨벤션 준수). */
public class GoodAppService {
    public String describe() {
        return "no querydsl here";
    }
}
```

- [ ] **Step 3: fixture 작성 (violation application — QueryDSL 누수)**

`.../fixture/persistenceconv/violation/svc/application/LeakyAppService.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.application;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 위반 — application 레이어가 QueryDSL을 직접 사용한다. */
public class LeakyAppService {
    public BooleanExpression alwaysTrue() {
        return Expressions.asBoolean(true).isTrue();
    }
}
```

- [ ] **Step 4: 실패하는 테스트 작성**

`PersistenceConventionRulesTest.java`:

```java
package com.ryuqqq.platform.archrules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("영속 컨벤션 룰 + 건강 리포트")
class PersistenceConventionRulesTest {

    private static final String BASE = "com.ryuqqq.platform.archrules.fixture.persistenceconv.";

    private static JavaClasses compliant;
    private static JavaClasses violation;

    @BeforeAll
    static void load() {
        ClassFileImporter importer = new ClassFileImporter();
        compliant = importer.importPackages(BASE + "compliant");
        violation = importer.importPackages(BASE + "violation");
    }

    @Test
    @DisplayName("게이트: compliant은 QueryDSL 누수가 없다 (GREEN)")
    void gatePassesOnCompliant() {
        assertThat(
                        PersistenceConventionRules.NO_QUERYDSL_OUTSIDE_ADAPTER_OUT
                                .evaluate(compliant)
                                .hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("게이트: violation은 application의 QueryDSL 사용에 걸린다 (RED)")
    void gateFailsOnViolation() {
        assertThat(
                        PersistenceConventionRules.NO_QUERYDSL_OUTSIDE_ADAPTER_OUT
                                .evaluate(violation)
                                .hasViolation())
                .isTrue();
    }
}
```

- [ ] **Step 5: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: 컴파일 실패 (`PersistenceConventionRules` 클래스 없음).

- [ ] **Step 6: PersistenceConventionRules 최소 구현**

`PersistenceConventionRules.java`:

```java
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
```

- [ ] **Step 7: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: PASS (2 tests).

- [ ] **Step 8: 커밋**

```bash
git add platform-archrules/build.gradle platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java platform-archrules/src/test/java/com/ryuqqq/platform/archrules/
git commit -m "feat(archrules): NO_QUERYDSL_OUTSIDE_ADAPTER_OUT 게이트 룰 + 영속 fixture 골격"
```

---

## Task 2: REPOSITORY_COMMAND_ONLY (HIGH, 커스텀 ArchCondition)

**Files:**
- Modify: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java`
- Create: `.../fixture/persistenceconv/compliant/svc/adapter/out/persistence/GoodRepository.java`
- Create: `.../fixture/persistenceconv/violation/svc/adapter/out/persistence/BadRepository.java`
- Modify: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/PersistenceConventionRulesTest.java`

**Interfaces:**
- Consumes: `PersistenceConventionRules`의 `ADAPTER_OUT` 상수.
- Produces: `PersistenceConventionRules.REPOSITORY_COMMAND_ONLY` (`public static final ArchRule`).

- [ ] **Step 1: fixture (compliant Repository — save/saveAll만 선언)**

`.../compliant/svc/adapter/out/persistence/GoodRepository.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import java.util.List;

/** 컨벤션 준수 — 직접 선언 메서드는 save/saveAll만. 조회는 QueryDSL(별도)로 한다. */
public interface GoodRepository {
    GoodEntity save(GoodEntity entity);

    List<GoodEntity> saveAll(List<GoodEntity> entities);
}
```

> 참고: `GoodEntity`는 Task 4에서 생성한다. 컴파일 순서상 Task 2 단독 실행 시 `GoodEntity`가 없으면 임시로 `Object`로 바꿔 작성해도 되지만, 순차 실행(Task 4 이후 전체 컴파일) 전제이므로 그대로 둔다. 단독 검증이 필요하면 이 파일의 `GoodEntity`를 `Object`로 두고 Task 4에서 되돌린다.

- [ ] **Step 2: fixture (violation Repository — 파생 쿼리 선언)**

`.../violation/svc/adapter/out/persistence/BadRepository.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import java.util.List;

/** 위반 — 조회/파생 쿼리 메서드를 Repository 인터페이스에 직접 선언한다. */
public interface BadRepository {
    Object save(Object entity);

    List<Object> findByName(String name);
}
```

- [ ] **Step 3: 실패하는 테스트 추가**

`PersistenceConventionRulesTest.java`에 추가:

```java
    @Test
    @DisplayName("REPOSITORY_COMMAND_ONLY: compliant Repository는 통과 (GREEN)")
    void repositoryCommandOnlyPassesOnCompliant() {
        assertThat(PersistenceConventionRules.REPOSITORY_COMMAND_ONLY.evaluate(compliant).hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("REPOSITORY_COMMAND_ONLY: 파생 쿼리 선언 Repository는 걸린다 (RED)")
    void repositoryCommandOnlyFailsOnViolation() {
        assertThat(PersistenceConventionRules.REPOSITORY_COMMAND_ONLY.evaluate(violation).hasViolation())
                .isTrue();
    }
```

- [ ] **Step 4: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: 컴파일 실패 (`REPOSITORY_COMMAND_ONLY` 없음).

- [ ] **Step 5: 룰 + 커스텀 ArchCondition 구현**

`PersistenceConventionRules.java`에 import 추가:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
```

클래스 본문에 추가(룰 상수보다 먼저 — static 초기화 순서):

```java
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
                    .because("JpaRepository는 순수 저장 기능만 — 조회/파생 쿼리는 QueryDSL로 분리한다")
                    .allowEmptyShould(true);
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: PASS (4 tests).

- [ ] **Step 7: 커밋**

```bash
git add platform-archrules/src/
git commit -m "feat(archrules): REPOSITORY_COMMAND_ONLY 룰 (save/saveAll만 직접 선언)"
```

---

## Task 3: CONDITION_LOGIC_IN_BUILDER (MEDIUM)

**Files:**
- Modify: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java`
- Create: `.../compliant/svc/adapter/out/persistence/GoodConditionBuilder.java`
- Create: `.../violation/svc/adapter/out/persistence/BadConditions.java`
- Modify: `PersistenceConventionRulesTest.java`

**Interfaces:**
- Consumes: (룰 상수 — 패키지 매처 무관, 클래스 simpleName 기반).
- Produces: `PersistenceConventionRules.CONDITION_LOGIC_IN_BUILDER` (`public static final ArchRule`). `private static final DescribedPredicate<JavaMethod> RETURNS_QUERYDSL_CONDITION`.

- [ ] **Step 1: fixture (compliant — ConditionBuilder가 BooleanExpression 반환)**

`.../compliant/svc/adapter/out/persistence/GoodConditionBuilder.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 컨벤션 준수 — QueryDSL 조건 조립은 *ConditionBuilder 안에 캡슐화한다. */
public class GoodConditionBuilder {
    public BooleanExpression nameEq(String name) {
        return name == null ? null : Expressions.asString(name).eq(name);
    }
}
```

- [ ] **Step 2: fixture (violation — ConditionBuilder 아닌 클래스가 BooleanExpression 반환)**

`.../violation/svc/adapter/out/persistence/BadConditions.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 위반 — ConditionBuilder가 아닌 곳에서 QueryDSL 조건을 만들어 반환한다(조건 로직 누수). */
public class BadConditions {
    public BooleanExpression nameEq(String name) {
        return Expressions.asString(name).eq(name);
    }
}
```

- [ ] **Step 3: 실패하는 테스트 추가**

`PersistenceConventionRulesTest.java`에 추가:

```java
    @Test
    @DisplayName("CONDITION_LOGIC_IN_BUILDER: ConditionBuilder의 조건 반환은 통과 (GREEN)")
    void conditionLogicPassesOnCompliant() {
        assertThat(
                        PersistenceConventionRules.CONDITION_LOGIC_IN_BUILDER
                                .evaluate(compliant)
                                .hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("CONDITION_LOGIC_IN_BUILDER: ConditionBuilder 밖의 조건 반환은 걸린다 (RED)")
    void conditionLogicFailsOnViolation() {
        assertThat(
                        PersistenceConventionRules.CONDITION_LOGIC_IN_BUILDER
                                .evaluate(violation)
                                .hasViolation())
                .isTrue();
    }
```

- [ ] **Step 4: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: 컴파일 실패 (`CONDITION_LOGIC_IN_BUILDER` 없음).

- [ ] **Step 5: 룰 + predicate 구현**

`PersistenceConventionRules.java`에 import 추가:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import java.util.Set;
```

클래스 본문에 추가:

```java
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
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: PASS (6 tests).

- [ ] **Step 7: 커밋**

```bash
git add platform-archrules/src/
git commit -m "feat(archrules): CONDITION_LOGIC_IN_BUILDER 룰 (조건은 ConditionBuilder에 캡슐화)"
```

---

## Task 4: JPA_ENTITY_EXTENDS_BASE (LOW)

**Files:**
- Modify: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java`
- Create: `.../compliant/svc/adapter/out/persistence/GoodEntity.java`
- Create: `.../violation/svc/adapter/out/persistence/BadEntity.java`
- Modify: `PersistenceConventionRulesTest.java`

**Interfaces:**
- Consumes: (룰 상수 — `@Entity` 애노테이션 + BaseAuditEntity FQN 매처).
- Produces: `PersistenceConventionRules.JPA_ENTITY_EXTENDS_BASE` (`public static final ArchRule`). `GoodEntity` 타입(Task 2 `GoodRepository`가 참조).

- [ ] **Step 1: fixture (compliant — @Entity extends BaseAuditEntity)**

`.../compliant/svc/adapter/out/persistence/GoodEntity.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import com.ryuqqq.platform.persistence.jpa.entity.BaseAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 컨벤션 준수 — @Entity는 platform BaseAuditEntity 계열을 상속한다. */
@Entity
public class GoodEntity extends BaseAuditEntity {
    @Id private Long id;

    public Long getId() {
        return id;
    }
}
```

- [ ] **Step 2: fixture (violation — @Entity, Base 미상속)**

`.../violation/svc/adapter/out/persistence/BadEntity.java`:

```java
package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 위반 — @Entity인데 platform BaseAuditEntity 계열을 상속하지 않는다. */
@Entity
public class BadEntity {
    @Id private Long id;

    public Long getId() {
        return id;
    }
}
```

- [ ] **Step 3: 실패하는 테스트 추가**

`PersistenceConventionRulesTest.java`에 추가:

```java
    @Test
    @DisplayName("JPA_ENTITY_EXTENDS_BASE: BaseAuditEntity 상속 @Entity는 통과 (GREEN)")
    void entityBasePassesOnCompliant() {
        assertThat(
                        PersistenceConventionRules.JPA_ENTITY_EXTENDS_BASE
                                .evaluate(compliant)
                                .hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("JPA_ENTITY_EXTENDS_BASE: Base 미상속 @Entity는 걸린다 (RED)")
    void entityBaseFailsOnViolation() {
        assertThat(
                        PersistenceConventionRules.JPA_ENTITY_EXTENDS_BASE
                                .evaluate(violation)
                                .hasViolation())
                .isTrue();
    }
```

- [ ] **Step 4: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: 컴파일 실패 (`JPA_ENTITY_EXTENDS_BASE` 없음).

- [ ] **Step 5: 룰 구현**

`PersistenceConventionRules.java`의 `CONDITION_LOGIC_IN_BUILDER` 다음에 추가(import는 Task 2에서 추가한 `classes` 재사용):

```java
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
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: PASS (8 tests).

- [ ] **Step 7: 커밋**

```bash
git add platform-archrules/src/
git commit -m "feat(archrules): JPA_ENTITY_EXTENDS_BASE 룰 (@Entity는 BaseAuditEntity 상속)"
```

---

## Task 5: all() 통합 + HealthReporter 점수 결정성 + compliant 전체 GREEN

**Files:**
- Modify: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/PersistenceConventionRules.java`
- Modify: `PersistenceConventionRulesTest.java`

**Interfaces:**
- Consumes: 4개 룰 상수 전부 + `DomainRule`·`Severity`·`DomainHealthReporter`·`HealthReport`·`Finding`(기존).
- Produces: `PersistenceConventionRules.all() -> List<DomainRule>` (감점 룰 3개만 — 게이트 룰 제외, 도메인 패턴과 일관).

- [ ] **Step 1: 실패하는 테스트 추가 (점수 결정성 + 전체 GREEN)**

`PersistenceConventionRulesTest.java`에 import 추가:

```java
import java.util.List;
```

테스트 추가:

```java
    @Test
    @DisplayName("compliant 건강 점수는 100 (findings 0)")
    void healthyScore() {
        HealthReport report = DomainHealthReporter.report(compliant, PersistenceConventionRules.all());

        assertThat(report.score()).isEqualTo(100);
        assertThat(report.isHealthy()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    @DisplayName("violation 점수는 결정적: 100 − (HIGH 10 + MEDIUM 5 + LOW 2) = 83")
    void unhealthyScore() {
        HealthReport report = DomainHealthReporter.report(violation, PersistenceConventionRules.all());

        assertThat(report.score()).isEqualTo(83);
        assertThat(report.findings()).isNotEmpty();

        List<String> failingIds =
                report.findings().stream().map(Finding::ruleId).distinct().toList();
        assertThat(failingIds)
                .containsExactlyInAnyOrder(
                        PersistenceConventionRules.all().stream()
                                .map(DomainRule::id)
                                .toArray(String[]::new));
    }

    @Test
    @DisplayName("toJson은 score·findings를 담는다")
    void jsonReport() {
        HealthReport report = DomainHealthReporter.report(violation, PersistenceConventionRules.all());

        String json = report.toJson();

        assertThat(json).contains("\"score\":83").contains("\"REPOSITORY_COMMAND_ONLY\"");
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: 컴파일 실패 (`all()` 메서드 없음).

- [ ] **Step 3: all() 구현**

`PersistenceConventionRules.java`에 import 추가:

```java
import java.util.List;
```

클래스 본문 끝에 추가:

```java
    /**
     * 건강 리포터가 쓰는 감점 룰 + 심각도. 게이트({@link #NO_QUERYDSL_OUTSIDE_ADAPTER_OUT})는
     * 빌드 게이트가 담당하므로 제외한다(도메인 룰과 동일 패턴).
     */
    public static List<DomainRule> all() {
        return List.of(
                new DomainRule("REPOSITORY_COMMAND_ONLY", REPOSITORY_COMMAND_ONLY, Severity.HIGH),
                new DomainRule("CONDITION_LOGIC_IN_BUILDER", CONDITION_LOGIC_IN_BUILDER, Severity.MEDIUM),
                new DomainRule("JPA_ENTITY_EXTENDS_BASE", JPA_ENTITY_EXTENDS_BASE, Severity.LOW));
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*PersistenceConventionRulesTest*'`
Expected: PASS (11 tests).

- [ ] **Step 5: 모듈 전체 빌드 — 회귀 없음 확인**

Run: `./gradlew :platform-archrules:build`
Expected: BUILD SUCCESSFUL (기존 도메인 룰 테스트 포함 전부 통과).

- [ ] **Step 6: 커밋**

```bash
git add platform-archrules/src/
git commit -m "feat(archrules): PersistenceConventionRules.all() + 건강 점수 결정성 테스트"
```

---

## Self-Review 결과

**Spec coverage:**
- 룰 4종 — Task 1(게이트)·2·3·4 ✓
- 하이브리드 전달(게이트 @ArchTest + 감점 all()) — Task 1·5 ✓
- 의존성 0(production) / persistence-jpa testImplementation — Task 1 Step 1 ✓
- compliant/violation 미러 테스트 + 점수 결정성 — Task 5 ✓
- root-무관 패키지 매처 — Global Constraints + 각 룰 ✓
- `REPOSITORY_COMMAND_ONLY` 직접 선언 메서드만(`getMethods()`) — Task 2 Step 5 ✓
- 비목표(코드 유틸·상속 메서드 차단·마커) — 계획에 포함 안 함 ✓

**Type consistency:** `all()`은 감점 3개만 반환(게이트 제외) → 점수 100−17=83 일관. `DomainRule(id, rule, severity)`·`Finding::ruleId`·`HealthReport.score()` 기존 시그니처와 일치.

**알려진 순서 의존:** Task 2의 `GoodRepository`가 Task 4의 `GoodEntity`를 참조 → 순차 실행 시 Task 4 완료 후 전체 컴파일 성공. Task 2 단독 검증이 필요하면 Step 1 노트대로 임시 `Object` 사용.
