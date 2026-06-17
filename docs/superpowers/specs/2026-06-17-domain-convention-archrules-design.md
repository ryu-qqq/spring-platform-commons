# Domain Convention ArchRules + Health Report — 설계 (spec)

- 날짜: 2026-06-17
- 상태: 확정 (구현 대기)
- 모듈: `platform-archrules` (publishable)
- 관련: ADR-0006, 기존 `HexagonalArchRules`/`HexagonalArchRulesFrozen`, `platform-audit-sweep` 스코어카드

## 목표

소비 프로젝트가 **자기 도메인 레이어**에 적용할 도메인 작성 컨벤션 룰을 `platform-archrules`에 추가하되,
**하이브리드 전달 모델**로 제공한다:

- **게이트(CRITICAL)**: 진짜 심각한 위반만 빌드 실패. (기존 framework-free·layer 룰 + Lombok)
- **건강 리포트(HIGH/MEDIUM/LOW)**: 도메인 작성 컨벤션 위반을 빌드를 죽이지 않고 평가해 **"어디·뭐가·몇 건"
  + 건강 점수**를 산출. `platform-audit-sweep` 스코어카드에 먹인다.

핵심: **#2는 기본적으로 새 빌드 실패를 추가하지 않는다.** "이 프로젝트 도메인이 얼마나 건강한가"를
진단하는 리포트가 주력이고, CRITICAL은 기존 게이트가 이미 막는다.

## 출처 정직성 (중요)

vault 근거상 **우아한형제들에서 온 것은 "계층 구조 원칙"(5계층·Common 순수 Java·단방향)뿐**이고, 기존
`DOMAIN_FRAMEWORK_FREE`와 정렬된다. 본 spec의 도메인 작성 룰(시간 주입·setter·타입 형태 등)은 **전부
자작**(marketplace 코드 전수조사 귀납)이다. 룰 Javadoc·문서에 "우형 표준"으로 표기하지 않는다.

## 범위 (Tier 1 + Tier 2)

**원칙:** 컨벤션 명문 근거 O + ArchUnit 정적 검사 가능 + **오탐 낮음**만.

- **Tier 1:** 시간 주입·setter·DomainException 상속 + Lombok(기존 룰 흡수). R1~R4.
- **Tier 2:** 에그리게이트 정의·일급 컬렉션(필드)·타입 형태·VO public 필드·패키지 슬라이스. R5~R12.

**제외:** 원시타입 전면 금지(VO가 원시 감쌈·근거 없음), 일급 컬렉션 내부 계약(정적 불가), static 가변
상태(선례 없음), 의미 판단 필요(null 검증 위치). R13(ID 래핑 휴리스틱)은 Tier 3 보류.

**전제:** Tier 2 룰은 소비측이 도메인 패키지 구조(`.aggregate/.vo/.id/.exception/.query`)를 따라야 한다.

## 전달 모델 — 하이브리드

```
DomainConventionRules: List<DomainRule>  (각 DomainRule = id + ArchRule + Severity)
        │
        ├─ 게이트:  CRITICAL 룰만 @ArchTest 표면 → 빌드 실패 (기본은 기존 HexagonalArchRules가 담당)
        └─ 리포터:  DomainHealthReporter.report(JavaClasses) → HealthReport
                     · 각 룰 ArchRule.evaluate(classes) (throw 안 함)
                     · 위반 수집 → Finding{ruleId, severity, violatingClass, message}
                     · score 계산
                     · HealthReport.toJson() → audit-sweep 스코어카드
```

### Severity 배분

| severity | 룰 | 동작 |
|---|---|---|
| CRITICAL | (기존) `DOMAIN_FRAMEWORK_FREE`(+`lombok..`)·`HEXAGONAL_LAYERS` | 빌드 실패 |
| HIGH | R1 시간주입 · R3 DomainException · R12 패키지슬라이스 | 리포트, 감점 −10 |
| MEDIUM | R2 setter · R5 aggregate=class · R6 ctor private · R7 일급컬렉션 · R8 VO필드 | 리포트, 감점 −5 |
| LOW | R9 `*Id` record · R10 `*ErrorCode` enum · R11 `*SortKey`/`*SearchField` enum | 리포트, 감점 −2 |

소비측이 특정 룰을 CRITICAL로 승격하려면 게이트 표면에 추가(문서화).

### 점수 모델 (단순·해석가능)

위반 *개수*가 아니라 **실패한 컨벤션 차원** 기준 — 한 클래스로 점수가 폭발하지 않게.

```
score = clamp(0, 100, 100 − Σ over rules with ≥1 violation ( weight(severity) ))
weight: HIGH=10, MEDIUM=5, LOW=2   (CRITICAL은 게이트라 점수 대상 아님; 포함 시 25)
```

위반 *개수*는 `Finding`에 별도로 담아 핫스팟(어느 클래스가 몇 건)을 본다. 점수=차원 건강, findings=상세.

## 룰 명세

## Tier 1 — 작성 핵심

### R1. `NO_TIME_IN_DOMAIN` (HIGH)
- 의미: 도메인은 현재 시각을 직접 호출하지 않고 주입받는다.
- 표현: `..domain..` 클래스가 호출 금지 — `Instant.now()`(전 오버로드)·`LocalDateTime/LocalDate/
  ZonedDateTime/OffsetDateTime/LocalTime/Year.now()`·`Clock.systemUTC()/systemDefaultZone()`·
  `System.currentTimeMillis()/nanoTime()`.
- 구현: `noClasses().that().resideInAPackage("..domain..").should()` + 커스텀
  `DescribedPredicate<JavaCall<?>>`(owner·name 쌍 OR).
- 근거: domain.md:45, outbox-family.md:57, factory.md:329.

### R2. `NO_SETTERS_IN_DOMAIN` (MEDIUM)
- 표현: `noMethods().that().areDeclaredInClassesThat().resideInAPackage("..domain..").should()
  .haveNameStartingWith("set")`.
- 근거: domain.md:10,119,185.

### R3. `DOMAIN_EXCEPTIONS_EXTEND_BASE` (HIGH)
- 표현: `classes().that().resideInAPackage("..domain..").and().haveSimpleNameEndingWith("Exception")
  .should().beAssignableTo("com.ryuqqq.platform.common.exception.DomainException")`.
- FQN 문자열 참조 → main 의존성 추가 없음. Outbox `IllegalStateException`(throw)과 비충돌(클래스 상속만 봄).
- 근거: domain.md:373,400.

### R4 (흡수). Lombok 금지 (CRITICAL)
- 기존 `HexagonalArchRules.DOMAIN_FRAMEWORK_FREE`에 `lombok..` 한 줄 추가. 게이트(빌드 실패) 유지.
- 근거: domain.md:7,121.

## Tier 2 — 구조·타입 형태 (상대 매처 `..aggregate..`·`..vo..`·`..id..`·`..exception..`·`..query..`)

### R5. `AGGREGATE_IS_CLASS` (MEDIUM)
- `classes().that().resideInAPackage("..aggregate..").should().notBeRecords()`. 근거: domain.md:38.

### R6. `AGGREGATE_CTORS_NOT_PUBLIC` (MEDIUM)
- `constructors().that().areDeclaredInClassesThat().resideInAPackage("..aggregate..").should()
  .notBePublic()`. VO record(canonical public ctor) 제외 위해 `..aggregate..` 스코프. 근거: domain.md:9,39.

### R7. `NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE` (MEDIUM)
- `noFields().that().areDeclaredInClassesThat().resideInAPackage("..aggregate..").should()
  .haveRawType(assignableTo java.util.Collection)` + `Map` 별도. 일급 컬렉션(필드 레벨). 근거: domain.md §4.

### R8. `VO_FIELDS_NOT_PUBLIC` (MEDIUM)
- `fields().that().areDeclaredInClassesThat().resideInAPackage("..vo..").and().areNotStatic()
  .should().notBePublic()`. static final 상수 제외. 근거: domain.md:186.

### R9. `ID_TYPES_ARE_RECORDS` (LOW)
- `classes().that().resideInAPackage("..id..").should().beRecords()`. `..id..` 스코프(오탐 회피).
  근거: domain.md:292.

### R10. `ERRORCODE_TYPES_ARE_ENUMS` (LOW)
- `classes().that().resideInAPackage("..exception..").and().haveSimpleNameEndingWith("ErrorCode")
  .should().beEnums()`. 근거: domain.md:332.

### R11. `SORT_SEARCH_KEYS_ARE_ENUMS` (LOW)
- `classes().that().resideInAPackage("..query..").and(haveSimpleNameEndingWith "SortKey" or "SearchField")
  .should().beEnums()`. 근거: domain.md:494,521.

### R12. `DOMAIN_PACKAGE_SLICES` (HIGH)
- `classes().that().resideInAPackage("..domain..").should().resideInAnyPackage("..domain.aggregate..",
  "..domain.vo..","..domain.id..","..domain.exception..","..domain.query..")` +
  `noClasses().should().resideInAPackage("..domain.event..")`. 다른 Tier 2 룰의 전제. 근거: package-structure.md.

## 컴포넌트

- `Severity` (enum): CRITICAL, HIGH, MEDIUM, LOW. `weight()` 제공.
- `DomainRule` (record): `String id`, `ArchRule rule`, `Severity severity`.
- `DomainConventionRules`: `List<DomainRule> all()` + 개별 `@ArchTest` 상수(strict 한 줄 입양도 지원).
- `Finding` (record): `String ruleId`, `Severity severity`, `String violatingClass`, `String message`.
- `HealthReport` (record): `int score`, `List<Finding> findings`, 집계 헬퍼(`countBySeverity()`,
  `failingRuleIds()`), `String toJson()` (수동 직렬화 — Jackson 의존 없이).
- `DomainHealthReporter`: `static HealthReport report(JavaClasses, List<DomainRule>)`. 각 룰
  `evaluate()` → `EvaluationResult.getFailureReport().getDetails()`로 위반 문자열·클래스 추출.

## 자가검증 (테스트)

기존 fixture 패턴 + 도메인 패키지 구조(`fixture/compliant/<svc>/domain/{aggregate,vo,id,exception,query}`).
- compliant: 시간 주입·setter 없음·`*Exception extends DomainException`·aggregate private-ctor class·
  raw 컬렉션 필드 없음·VO public 필드 없음·`*Id` record·`*ErrorCode`/`*SortKey` enum·올바른 패키지.
- violation: 위 각각의 위반본.
- 룰 단위: violation fixture에 각 ArchRule RED(TDD) → compliant GREEN.
- 리포터: compliant fixture → score 100·findings 0. violation fixture → 기대 점수(예: HIGH 1+MEDIUM 1
  위반 = 100−15=85)·findings에 위반 클래스 포함. **score 계산이 결정적**이므로 단위 테스트로 못박는다.
- 게이트: framework 위반 fixture로 CRITICAL(`DOMAIN_FRAMEWORK_FREE`+lombok) 빌드 실패 확인.

R3·리포터 검증에 `DomainException`이 필요 → **`platform-common-domain`을 `platform-archrules`의
`testImplementation`으로 추가**(test 전용, main publishable 산출물엔 의존성 없음).

## 소비측 입양 (문서화)

```java
// 게이트 (CRITICAL — 빌드 실패): 기존 한 줄
@ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRules.class);

// 건강 리포트 (빌드 안 죽임): 진단 API
JavaClasses classes = new ClassFileImporter().importPackages("com.ryuq.myservice");
HealthReport report = DomainHealthReporter.report(classes, DomainConventionRules.all());
System.out.println(report.toJson());   // → audit-sweep 스코어카드/CI 아티팩트
```

platform-archrules README에 "도메인 건강 리포트" 섹션 추가(룰·severity·점수 모델·자작 출처 명시).

## 비목표

- R13(ID 래핑 휴리스틱)·원시타입 전면 금지 — Tier 3, 제외.
- 일급 컬렉션 내부 계약·static 가변 상태(선례 없음)·의미 판단 룰 — 제외.
- frozen ratchet — 리포트 모델에선 불필요(빌드 안 죽이고 다 보여줌). 게이트(CRITICAL)는 기존
  `HexagonalArchRulesFrozen`이 이미 제공.
- CHANGELOG `[Unreleased] Added` 기재(구현 시).

## 검증 기준 (완료 정의)

- `Severity`·`DomainRule`·`DomainConventionRules`(R1~R3,R5~R12)·`Finding`·`HealthReport`·
  `DomainHealthReporter` 신설. Lombok은 기존 `DOMAIN_FRAMEWORK_FREE`에 흡수.
- fixture 기반: 각 룰 RED→GREEN, 리포터 score/findings 결정적 단위테스트 GREEN, CRITICAL 게이트 실패 확인.
- 전체 빌드·기존 ArchUnit GREEN(회귀 없음).
- platform-archrules README·CHANGELOG 갱신.
