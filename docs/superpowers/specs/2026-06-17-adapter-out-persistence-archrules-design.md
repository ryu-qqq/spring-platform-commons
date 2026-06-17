# AdapterOut Persistence ArchUnit 룰 — 설계

> platform-archrules에 영속(adapter-out) 레이어 컨벤션 룰 4종을 추가한다.
> 작성: 2026-06-17 · 상태: 승인됨(설계) → 구현 계획 단계

## 배경·동기

`platform-archrules`는 도메인 레이어 룰(`HexagonalArchRules` 게이트 + `DomainConventionRules` 감점)은
갖췄으나 **영속(adapter-out) 레이어 룰이 없다**. 팀 컨벤션:

1. `JpaRepository`는 순수 Spring Data JPA 기능만, 가급적 `save`/`saveAll`만 쓴다.
2. 모든 조회는 QueryDSL(`JPAQueryFactory`)로 한다.
3. QueryDSL 동적 조건은 흩뿌리지 않고 별도 `*ConditionBuilder` 객체로 캡슐화한다.

`decision-researcher` 실측(8개 서비스·231개 ConditionBuilder)에서 **코드 유틸 승격(페이징/조건결합)은
타입 공유 불가 → 보류**로 결론났고, 반복되는 *관용구*는 **컨벤션 룰**의 그릇이 맞다는 방향이 데이터와
정합했다. 본 설계는 그 결론의 실행이다.

## 비목표 (YAGNI)

- QueryDSL 페이징/조건결합 코드 유틸 제공 — 보류(타입 공유 불가, 실측 근거).
- `JpaRepository` 상속 자체 금지 / 상속 메서드(`findById` 등) **호출처** 차단 — 오탐 위험 커 범위 밖.
- 마커 인터페이스·`@FunctionalInterface` Repository — NO-GO(Spring Data 프록시 시맨틱 충돌·우회 가능).

## 구조

`platform-archrules`에 클래스 **1개** 신규: `PersistenceConventionRules`
(`DomainConventionRules` 미러). 기존 `DomainHealthReporter`·`Severity`·`DomainRule`·`HealthReport`·
`Finding`은 **그대로 재사용**(범용 — `List<DomainRule>`을 받음). 신규 인프라 없음.

**의존성 추가 0** — 모든 외부 타입(`com.querydsl..`, `jakarta.persistence..`)은 ArchUnit의
**FQN 문자열 매처**로만 참조(컴파일 의존성 없음). production 의존성은 기존대로 `archunit-junit5` 단 하나.

## 룰 4종

| 룰 ID | 심각도 | 정의 |
|-------|--------|------|
| `NO_QUERYDSL_OUTSIDE_ADAPTER_OUT` | **CRITICAL** (게이트) | `..domain..`·`..application..`·`..adapter.in..`는 `com.querydsl..`·`com.querydsl.jpa.impl.JPAQueryFactory`·`jakarta.persistence.EntityManager`에 의존 금지 |
| `REPOSITORY_COMMAND_ONLY` | HIGH | `..adapter.out..`의 `*Repository` 인터페이스가 **직접 선언한** 메서드는 `save`/`saveAll`만 허용 |
| `CONDITION_LOGIC_IN_BUILDER` | MEDIUM | `com.querydsl.core.types.dsl.BooleanExpression`/`com.querydsl.core.types.Predicate`를 **반환**하는 메서드는 `*ConditionBuilder` 타입 안에서만 선언 |
| `JPA_ENTITY_EXTENDS_BASE` | LOW | `jakarta.persistence.Entity` 애노테이션 클래스는 `BaseAuditEntity` 계열을 상속(FQN `beAssignableTo`) |

### 정의 상세 / 오탐 방지

- **`REPOSITORY_COMMAND_ONLY`**: ArchUnit `methods().that().areDeclaredInClassesThat()
  .resideInAPackage("..adapter.out..").and().haveSimpleNameEndingWith("Repository")
  .and()(인터페이스)`에 대해 커스텀 `ArchCondition`으로 "메서드 이름 ∈ {save, saveAll}"을 검사.
  **직접 선언 메서드만** 대상(`JavaClass.getMethods()`는 선언 메서드만 반환) — 상속된 `findById` 등은
  검사하지 않는다. 즉 개발자가 손으로 적은 조회/파생 쿼리 메서드 선언을 막는 룰.
- **`NO_QUERYDSL_OUTSIDE_ADAPTER_OUT`**: 매처를 `..domain..`·`..application..`·`..adapter.in..`로
  **한정**한다. `BaseAuditEntity`(platform 모듈, `jakarta.persistence` 정당 사용)·소비측 `..adapter.out..`
  코드는 대상 외 → 충돌 없음. `HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE`의 영속 차단을
  domain·adapter.in까지 확장·보강하는 성격.
- **`CONDITION_LOGIC_IN_BUILDER`**: 반환 타입 매처는 `haveRawReturnType(String fqn)`으로 QueryDSL
  타입을 문자열 참조. `*ConditionBuilder` 외 클래스에서 `BooleanExpression`을 만들어 반환하면 위반.
- **`JPA_ENTITY_EXTENDS_BASE`**: `beAssignableTo("com.ryuqqq.platform.persistence.jpa.entity.BaseAuditEntity")`.
  Base 4종은 단일 루트 트리(`SoftDelete`→`Audit`, `VersionedSoftDelete`→`SoftDelete`, `Versioned`→`Audit`)라
  루트 FQN 하나로 전부 커버. `BaseAuditEntity`는 **platform 모듈의 고정 클래스**(소비측 `@Entity`가 상속)
  이므로 도메인 룰 `DOMAIN_EXCEPTIONS_EXTEND_BASE`(공통 `DomainException` FQN 참조)와 **동일 패턴**.
  `promote-module.sh`가 승격 시 `com.ryuqqq`→`com.connectly` FQN을 자동 치환하므로 인큐베이터엔
  `com.ryuqqq.*`로 작성한다. (소비측 `@Entity`만 대상이고 룰 자체는 root-무관 패키지 매처라 비종속 유지.)

## 전달 — 하이브리드 (도메인 룰과 동일)

- **게이트**: `NO_QUERYDSL_OUTSIDE_ADAPTER_OUT`(CRITICAL)만 `@ArchTest public static final ArchRule`로
  노출 → 소비측 strict/frozen suite에서 빌드 실패.
- **감점**: 나머지 3개는 `PersistenceConventionRules.all()`(`List<DomainRule>`)로 묶어
  `DomainHealthReporter.report(classes, PersistenceConventionRules.all())`에 넘겨 점수·findings 진단.
  빌드 안 깨짐 → 기존 fleet 코드가 즉시 깨지지 않음(점진 개선).
- 점수: `100 − (HIGH 10 + MEDIUM 5 + LOW 2)` = 위반 시 83. CRITICAL은 점수 제외(게이트).

## 테스트 (도메인 테스트 미러)

`src/test/.../fixture/persistenceconv/{compliant,violation}` 신규 + `PersistenceConventionRulesTest`:

- **compliant** adapter-out: QueryDslRepository(조회), `*ConditionBuilder`(BooleanExpression 반환),
  `*Repository`(save/saveAll만 선언), `BaseAuditEntity` 상속 `@Entity` → 전 룰 GREEN, 점수 100.
- **violation**: 파생쿼리 메서드 선언 Repository, ConditionBuilder 밖에서 BooleanExpression 반환,
  Base 미상속 `@Entity`, application 레이어에서 QueryDSL 사용 → 전 룰 RED, 점수 결정성 검증.
- 게이트 룰(`NO_QUERYDSL_OUTSIDE`)은 별도 `@Test`로 compliant GREEN / violation RED 검증.
- `ClassFileImporter`는 `DO_NOT_INCLUDE_TESTS` 미사용(fixture가 test 소스셋) — 도메인 테스트와 동일 주의.

## 영향 범위

- 변경: `platform-archrules` 1개 모듈(main 클래스 1개 + test fixture/테스트). 다른 모듈·소비측 무영향.
- 소비측은 기존 `DomainHealthReporter` 호출에 `PersistenceConventionRules.all()`을 추가로 넘기거나
  별도 리포트로 호출하면 됨(소비측 변경은 본 작업 범위 밖, 후속).
