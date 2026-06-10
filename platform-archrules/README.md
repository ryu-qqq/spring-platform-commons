# platform-archrules

**헥사고날 아키텍처 경계를 강제하는 이식 가능한 ArchUnit 규칙 라이브러리 — root 패키지 무관.**

소비측이 `@AnalyzeClasses` + `@ArchTest` 로 붙이기만 하면 도메인 순수성·application 격리·레이어 의존
방향을 빌드(test)에서 자동 검증한다. 규칙은 **상대 패키지 매처**(`..domain..` 등)로 작성되어 어떤
root 패키지(`com.ryuqq.<service>`·`com.ryuqqq.platform`…)에서도 동일하게 동작한다.

## 역할

각 헥사고날 서비스가 반복해서 직접 작성하던 ArchUnit 규칙을 **한곳에 수렴**시킨다. 이 모듈은 동작
로직이 아니라 **검증 규칙(`ArchRule` 상수) 3종**을 제공하고, 위반 시 소비측 빌드를 실패시켜 경계
침범을 enforce 한다. 적용 범위(어떤 패키지를 스캔할지)·점진 도입 속도는 모두 소비측이 결정한다.

- **이식 가능** — 절대 패키지를 하드코딩하지 않고 `..domain..`·`..application..`·`..adapter.in..`·
  `..adapter.out..`·`..bootstrap..` 상대 매처만 쓴다. root 패키지가 무엇이든 그대로 적용된다.
- **규칙 상수 노출** — `HexagonalArchRules` 의 `public static final ArchRule` 3개를 소비측이
  `@ArchTest static final ArchRule` 로 가져다 쓴다.
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

## 소비 방법

소비 레포의 테스트에 규칙을 `@ArchTest` 로 선언한다 (`packages` 는 소비측 root).

```java
@AnalyzeClasses(packages = "com.ryuqq.marketplace")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRule layers      = HexagonalArchRules.HEXAGONAL_LAYERS;
    @ArchTest static final ArchRule domainPure  = HexagonalArchRules.DOMAIN_FRAMEWORK_FREE;
    @ArchTest static final ArchRule appIsolated = HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE;
}
```

위반이 있으면 해당 test 가 실패한다 = enforce. **점진 도입**은 규칙을 하나씩 추가하거나,
잡힌 항목을 `@ArchIgnore` 로 일시 보류하는 식으로 소비측이 속도를 조절한다.

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
