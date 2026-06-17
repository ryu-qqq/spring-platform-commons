# platform-archrules

**헥사고날 아키텍처 경계를 강제하는 이식 가능한 ArchUnit 규칙 라이브러리 — root 패키지 무관.**

소비측이 `@AnalyzeClasses` + `ArchTests.in(...)` 한 줄로 붙이기만 하면 도메인 순수성·application 격리·
레이어 의존 방향을 빌드(test)에서 자동 검증한다. 규칙은 **상대 패키지 매처**(`..domain..` 등)로 작성되어
어떤 root 패키지(`com.ryuqq.<service>`·`com.ryuqqq.platform`…)에서도 동일하게 동작한다.

## 역할

각 헥사고날 서비스가 반복해서 직접 작성하던 ArchUnit 규칙을 **한곳에 수렴**시킨다. 이 모듈은 동작
로직이 아니라 **검증 규칙(`ArchRule` 상수) 3종**을 제공하고, 위반 시 소비측 빌드를 실패시켜 경계
침범을 enforce 한다. 적용 범위(어떤 패키지를 스캔할지)·점진 도입 속도는 모두 소비측이 결정한다.

- **이식 가능** — 절대 패키지를 하드코딩하지 않고 `..domain..`·`..application..`·`..adapter.in..`·
  `..adapter.out..`·`..bootstrap..` 상대 매처만 쓴다. root 패키지가 무엇이든 그대로 적용된다.
- **한 줄 입양 표면** — strict(`HexagonalArchRules`)·frozen(`HexagonalArchRulesFrozen`) 두 묶음을
  `ArchTests.in(...)` 한 줄로 가져다 쓴다. 개별 `ArchRule` 상수 직접 참조도 그대로 동작한다.
- **self-test 보장** — 모듈 자체 테스트가 compliant/violation 픽스처로 규칙의 양/음성을 검증한다
  (규칙이 실제로 잡고, 정상 코드는 통과시키는지).

## 제공 규칙 (확장점)

`com.ryuqqq.platform.archrules.HexagonalArchRules` 의 3개 상수.

| 규칙 | 의미 | 검사 대상 |
|------|------|-----------|
| `DOMAIN_FRAMEWORK_FREE` | 도메인은 프레임워크 비의존(순수 자바) | `..domain..` 가 Spring/JPA/Hibernate/Jackson/Servlet/QueryDSL 에 의존하면 위반 |
| `APPLICATION_NO_WEB_OR_PERSISTENCE` | application은 웹/영속 스택에 직접 의존 안 함 (포트로만 통신) | `..application..` 가 `spring.web`/servlet/JPA/Hibernate/`data.jpa`/QueryDSL 에 의존하면 위반 |
| `HEXAGONAL_LAYERS` | 레이어 의존 방향 — 안쪽으로만 | adapter→application→domain, `bootstrap` 만 조립 루트 |

`HEXAGONAL_LAYERS` 의 허용 방향:

```
Domain        ← Application, AdapterIn, AdapterOut, Bootstrap
Application   ← AdapterIn, AdapterOut, Bootstrap
AdapterIn     ← Bootstrap
AdapterOut    ← Bootstrap
Bootstrap     ← (아무도 의존 못 함 = 최외곽 조립 루트)
```

`withOptionalLayers(true)` 로 두어, 소비측에 일부 레이어 패키지가 없어도 통과한다. `DOMAIN_FRAMEWORK_FREE`·
`APPLICATION_NO_WEB_OR_PERSISTENCE` 는 `allowEmptyShould(true)` 로 대상 패키지가 비어도 실패하지 않는다.

**입양 표면 2종:** `HexagonalArchRules`(strict, 그린필드) · `HexagonalArchRulesFrozen`(frozen ratchet, 브라운필드). 둘 다 `ArchTests.in(...)` 한 줄로 적용.

## 소비 방법 (한 줄 Apply)

규칙 묶음을 `ArchTests.in(...)` 한 줄로 당겨쓴다. 레거시 유무로 strict/frozen을 고른다.

### 그린필드 (레거시 없음) — strict

```java
@AnalyzeClasses(packages = "com.ryuqq.newservice")
class HexagonalArchitectureTest {
    @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRules.class);
}
```

처음부터 위반 0을 강제한다. 위반이 하나라도 있으면 빌드 실패.

### 브라운필드 (레거시 위반 있음) — frozen ratchet

```java
@AnalyzeClasses(packages = "com.ryuqq.marketplace")
class HexagonalArchitectureTest {
    @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRulesFrozen.class);
}
```

첫 실행이 현재 위반을 baseline으로 **동결**하고, 이후엔 **신규 위반만** 실패시킨다. 위반을 고치면
baseline에서 자동 제거되어 되돌아갈 수 없다(ratchet) → 레거시를 통째로 고치지 않고도 입양 가능.

> 개별 규칙을 직접 참조하던 기존 방식(`HexagonalArchRules.HEXAGONAL_LAYERS` 등)도 그대로 동작한다.

### violation-store 설정 (frozen 사용 시 필수)

소비 레포 `src/test/resources/archunit.properties`:

```properties
freeze.store.default.path=archunit_store           # baseline 텍스트 파일 위치(레포에 커밋)
freeze.store.default.allowStoreCreation=true        # 로컬 최초 1회 baseline 생성용
freeze.refreeze=false                                # 신규 위반을 store에 자동 흡수 금지(=실패시킴)
```

운용:

1. 로컬에서 최초 1회 테스트 실행 → `archunit_store/`에 baseline 생성.
2. baseline 파일을 git에 **커밋**(소비 레포 소유 — 팀 간 baseline 공유). 본 라이브러리 self-test가
   store를 `build/`에 버리는 것과 반대다(소비측은 커밋, self-test는 격리).
3. CI에서는 우발적 baseline 생성을 막기 위해 `freeze.store.default.allowStoreCreation=false`로
   두는 것을 권장(신규 위반이 store에 조용히 흡수되지 않게 함).

전제: 소비측 코드가 `..domain..`·`..application..`·`..adapter.in..`·`..adapter.out..`·`..bootstrap..`
패키지 컨벤션을 따라야 매처가 레이어를 인식한다.

## self-test (모듈 내부 품질 보증)

규칙이 "실제로 잡는지"를 모듈 스스로 검증한다. `src/test` 의 픽스처 두 묶음과 테스트로 양/음성을 건다.

| 픽스처 | 패키지 | 역할 |
|--------|--------|------|
| compliant | `…fixture.compliant.{domain,application,adapter.in,adapter.out}` | 규약을 지키는 코드 — 3개 규칙 모두 통과해야 함 |
| violation | `…fixture.violation.{domain,application}` | 일부러 위반한 코드 — 규칙이 잡아야 함 (positive control) |

`HexagonalArchRulesTest` 검증 내용:

- compliant 픽스처는 3개 규칙을 모두 통과한다.
- domain 이 Spring 에 의존하면 `DOMAIN_FRAMEWORK_FREE` 가 `AssertionError` 로 잡는다
  (`fixture.violation.domain.SpringCoupledDomain`).
- domain 이 application(바깥쪽)에 의존하면 `HEXAGONAL_LAYERS` 가 잡는다
  (`fixture.violation.domain.LeakyDomain → application.LeakyApp`).

규칙을 고치거나 추가할 때는 이 픽스처에 대응 시나리오를 함께 넣어 self-test 로 회귀를 막는다.

freezing 동작은 `FreezingBehaviorTest`가 in-memory store로 검증한다(레거시 동결·신규 위반 차단·prune 후 회귀 차단). frozen/strict 한 줄 표면은 `FrozenSuiteArchTest`·`StrictSuiteArchTest`가 compliant 픽스처로 end-to-end 확인한다.

## 도메인 작성 컨벤션 + 건강 리포트 (하이브리드)

레이어 경계 룰(위)과 별개로, **도메인 레이어 작성 컨벤션**을 `DomainConventionRules`로 제공한다.
전달은 **하이브리드** — 심각한 위반만 빌드를 막고, 나머지는 빌드를 죽이지 않고 **진단·점수화**한다.

> **출처:** 우아한형제들 근거는 계층 구조 원칙뿐이며(= `DOMAIN_FRAMEWORK_FREE`와 정렬), 아래 작성
> 룰은 marketplace 코드 전수조사로 귀납한 **자작** 컨벤션이다.

### 룰 (상대 매처 `..domain..`·`..aggregate..` 등)

| 룰 | severity | 의미 |
|----|----------|------|
| `NO_TIME_IN_DOMAIN` | HIGH | 도메인은 `Instant.now()` 등 현재 시각을 직접 읽지 않고 주입받는다 |
| `DOMAIN_EXCEPTIONS_EXTEND_BASE` | HIGH | `*Exception`은 `DomainException` 상속 |
| `DOMAIN_PACKAGE_SLICES` | HIGH | 도메인은 `aggregate/vo/id/exception/query` 슬라이스만(`.event` 금지) |
| `NO_SETTERS_IN_DOMAIN` | MEDIUM | `set*` 금지(불변) |
| `AGGREGATE_IS_CLASS` | MEDIUM | Aggregate는 record가 아닌 class |
| `AGGREGATE_CTORS_NOT_PUBLIC` | MEDIUM | 생성자 private(정적 팩토리 강제) |
| `NO_RAW_COLLECTION_FIELDS_IN_AGGREGATE` | MEDIUM | 일급 컬렉션(raw `Collection`/`Map` 필드 금지) |
| `VO_FIELDS_NOT_PUBLIC` | MEDIUM | VO public 필드 금지 |
| `ID_TYPES_ARE_RECORDS` | LOW | `..id..`는 record |
| `ERRORCODE_TYPES_ARE_ENUMS` | LOW | `*ErrorCode`는 enum |
| `SORT_SEARCH_KEYS_ARE_ENUMS` | LOW | `*SortKey`/`*SearchField`는 enum |

`CRITICAL`(프레임워크 의존·Lombok)은 위 `DOMAIN_FRAMEWORK_FREE` 게이트가 빌드 실패로 막는다.

### 건강 점수

빌드를 죽이지 않고 진단한다. 점수는 **실패한 컨벤션 차원** 기준(룰당 1회 감점, HIGH −10·MEDIUM −5·
LOW −2)이라 한 클래스의 다발 위반으로 폭발하지 않는다. 위반 개수·핫스팟은 `findings`에서 본다.

```java
JavaClasses classes = new ClassFileImporter().importPackages("com.ryuq.myservice");
HealthReport report = DomainHealthReporter.report(classes, DomainConventionRules.all());
System.out.println(report.toJson());   // {"score":83,"findings":[...]} → 스코어카드/CI 아티팩트
```

게이트(빌드 실패)가 필요하면 strict 표면(`ArchTests.in(HexagonalArchRules.class)`)이 CRITICAL을 막고,
특정 작성 룰을 승격하려면 소비측이 해당 `ArchRule` 상수를 `@ArchTest`로 직접 추가한다.

## 의존성

런타임은 ArchUnit JUnit5 확장만 `api` 로 전이한다 (소비측 테스트 클래스패스에 노출).

```groovy
api libs.archunit.junit5
```

테스트 전용으로 spring-context 를 두는데, 이는 **위반 픽스처가 Spring 의존을 흉내 내기 위함**일 뿐
모듈 런타임 의존이 아니다.

## 비목표

- 적용 범위(스캔 패키지)·점진 도입 속도 결정 — 소비측 책임.
- 네이밍 컨벤션(클래스 접미사 등)·순환 의존·특정 어노테이션 규칙 — 이 모듈 범위 밖(필요 시 소비측이 별도 규칙 추가).
- 패키지 컨벤션 자체 강제 — 매처는 컨벤션을 전제할 뿐, 패키지 구조를 만들어 주지 않는다.
