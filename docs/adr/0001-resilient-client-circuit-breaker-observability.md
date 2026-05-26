# 0001. Resilient Client — Circuit Breaker 운영 가시성 보강 (state transition 로그 + execute() fallback 람다 오버로드)

- 상태: Accepted
- 날짜: 2026-05-26

## 맥락 (Context)

resilient-client SDK는 Resilience4j CircuitBreaker + Retry를 래핑한 외부 호출 SDK다 (`spring-platform-commons/resilient-client/`). 우아한형제들 기술블로그 "개발자 의식의 흐름대로 적용해보는 서킷브레이커" (raw: `/Users/ryu-qqq/Documents/ryu-qqq-wiki/raw/개발자 의식의 흐름대로 적용해보는 서킷브레이커  우아한형제들 기술블로그.md`) 의 권장 패턴 6단계와 우리 SDK를 매핑한 결과, 5/6 단계는 글 권고와 같거나 더 풍부한 수준으로 구현되어 있으나 다음 2개 갭이 식별됨.

**갭 1 — CB state transition 이벤트 리스너 부재**

- 현 코드: `resilient-client-core/.../DefaultResilientClient.java:61-63`에서 Retry의 `onRetry`만 등록. `CircuitBreaker.getEventPublisher()` 호출 자체가 없음.
- 영향: `resilient_client_circuit_breaker_state` Gauge 메트릭으로 현재 상태는 관측되지만, **CLOSED→OPEN 전환 시점 로그가 안 찍힘** → 사후 장애 분석이 메트릭 시계열에 100% 의존. 글의 핵심 단계("로그로 찍히면 좋겠는데?")가 통째로 빠짐.

**갭 2 — 명시적 fallback API 부재**

- 현 코드: `ResilientClient.execute(req, type)`은 예외를 던지고 호출자가 try-catch로 처리.
- 영향: 글의 `@CircuitBreaker(fallbackMethod="fallback")` 패턴(Exception별 fallback 분기) 부재. 호출자가 SDK 예외 계층을 직접 catch해야 하므로 사용 boilerplate ↑.

## 결정 동인 (Decision Drivers)

- **운영 가시성** — CB 상태 전이 시점을 로그로 확보해 메트릭 + 로그 이중 근거로 사후 장애 분석 가능해야 한다.
- **호출자 boilerplate 최소화** — fallback 분기를 호출 시점에 자연스럽게 표현할 수 있어야 한다.
- **브레이킹 변경 금지** — 기존 사용처 영향 0.
- **YAGNI** — 현재 발현된 요구만 반영, 확장 포인트는 필요해질 때 별도 ADR로.
- **기존 컨벤션 일관성** — Retry `onRetry` 등록 패턴과 같은 자리·같은 방식으로 CB 이벤트 등록.

## 검토한 옵션 (Considered Options)

### 옵션 A — CB 이벤트 publisher에 default 자동 로그 등록 (생성자 한 곳)

`DefaultResilientClient` 생성자에서 `circuitBreaker.getEventPublisher()`로 `onStateTransition`, `onCallNotPermitted`, `onError` 핸들러를 등록. 기본 ON, 사용자 코드 변경 없음.

- 장점: 최소 침습, 기존 Retry 등록 패턴과 시그니처·위치 일관.
- 단점: 로그 포맷/레벨 끄기 API가 없음 → 노이즈 발생 시 코드 수정 필요.

### 옵션 B — `ResilientClientEventListener` 인터페이스 + 빌더 다중 등록

빌더에 `.eventListener(...)`로 listener 다중 등록, `DefaultLoggingEventListener`를 기본 구현으로 제공. Slack 알림·커스텀 메트릭 등 확장 가능.

- 장점: 확장성 ↑.
- 단점: 현재 발현된 요구가 아님. API 표면 확장. 학습 비용.

### 옵션 X — `execute()`에 fallback 람다 오버로드 추가

```java
<T> T execute(ExternalRequest request, Class<T> responseType,
              Function<ExternalCallException, T> fallback);
```

`ExternalCallException` 발생 시 fallback 람다를 호출하고 그 반환값을 사용자에게 돌려준다. 람다가 다시 예외를 던지면 그대로 전파. 메트릭은 이미 failure로 기록된 상태에서 fallback 호출 (record는 그대로 — fallback은 비즈니스 대응).

```java
ReviewDto r = client.execute(req, ReviewDto.class, ex -> {
    if (ex instanceof CircuitOpenException) return cachedReview();
    if (ex instanceof ServerException)    return emptyReview();
    throw ex;
});
```

- 장점: 호출 시점 의도가 코드에 드러남. 호출별 fallback 분기 자연스러움.
- 단점: 사용자가 SDK 예외 타입을 instanceof로 분기 → 예외 클래스 리네이밍 시 사용처 영향.

### 옵션 Y — 빌더에 default fallback 등록 — `.fallback((req, ex) -> ...)`

클라이언트 인스턴스 단위로 fallback을 한 번에 묶어 등록.

- 장점: 호출부 boilerplate ↓.
- 단점: 같은 클라이언트로 여러 API 호출 시 호출별 fallback 분기가 어렵고, 빌더 시점에 모든 호출 경우의 수를 알기 어렵다.

### 옵션 Z — 옵션 X + 옵션 Y 동시 도입

가장 유연하나 작업량 ↑ + API 표면 확장 → 학습 비용 ↑.

## 결정 (Decision Outcome)

### 결정 1: 옵션 A — CB state transition 자동 로그 등록

- **위치**: `DefaultResilientClient` 생성자 — 기존 `retry.getEventPublisher().onRetry(...)` 등록부 옆에 CB 이벤트 publisher 등록 추가.
- **등록 이벤트**:
  - `onStateTransition` → `log.info("[{}] CB {} -> {}", name, fromState, toState)`
  - `onCallNotPermitted` → `log.warn("[{}] CB call not permitted", name)`
  - `onError` → `log.warn("[{}] CB error: {}", name, throwable)` (CB 내부 에러 — 일반 호출 실패와 구분 필요)
- **기본 ON**, 사용자 코드 변경 없음, 끄기/포맷 변경 API는 v2까지 보류.

**이유**: 옵션 B(listener 인터페이스) 대비 현재 요구를 정확히 충족하면서 API 표면을 늘리지 않음. 글의 권고("로그로 찍히면 좋겠는데?")가 default 자동 로그만으로 충족된다. listener가 필요해지는 시점(Slack 알림 등)에 v2로 도입.

### 결정 2: 옵션 X — execute() fallback 람다 오버로드 추가

- **인터페이스 시그니처 추가** (`ResilientClient.java`):
  ```java
  <T> T execute(ExternalRequest request, Class<T> responseType,
                Function<ExternalCallException, T> fallback);
  ```
- **동작**:
  - 기존 `execute(req, type)`은 그대로 유지 (예외 전파).
  - 새 오버로드는 `ExternalCallException` 발생 시 fallback 람다를 호출하고 그 반환값을 사용자에게 돌려준다. 람다가 다시 예외를 던지면 그대로 전파.
  - fallback 호출은 메트릭에는 이미 failure로 기록된 상태에서 일어남 (record는 그대로 → fallback은 비즈니스 대응).

**이유**: 옵션 Y(빌더 default fallback)는 호출별 분기 표현이 어렵고 빌더 시점에 모든 경우의 수를 알 수 없다. 람다 overload가 호출 시점 의도를 더 명확하게 표현. 옵션 Z(둘 다)는 작업량·학습 비용이 커서, overload 하나로 시작하고 빌더 default 요구가 명확해지면 v2.

## 결과 (Consequences)

### 긍정 (Positive)

- 운영 시 CB 상태 전이를 SLF4J 로그로 즉시 확인 가능 → 사후 장애 분석 시 메트릭 + 로그 이중 근거.
- fallback 람다로 호출자 boilerplate 감소, 호출 시점 의도가 코드에 드러남.
- 브레이킹 변경 없음 — 기존 사용처 영향 0.

### 부정 (Negative / Risks)

- 로그 볼륨 — CB 상태 전이는 빈번하지 않으므로 영향 미미하나, `onError` 이벤트는 환경에 따라 노이즈가 될 수 있음. 노이즈가 발견되면 레벨 조정으로 대응.
- fallback 람다 시그니처에 `Function<ExternalCallException, T>`를 노출 → 사용자가 SDK 예외 타입(`CircuitOpenException` 등)을 `instanceof`로 분기하는 사용 패턴 정착. 예외 계층 리네이밍 시 사용처 영향 ↑ → 예외 클래스 이름 변경 자제.

### 후속 (Follow-ups)

- 단위 테스트 추가 (`ResilientClientTest`): state transition 로그 검증, fallback 호출 검증, fallback이 예외 재던지면 전파 검증.
- 통합 테스트 시 CB 강제 OPEN 시나리오 추가.
- wiki `projects/spring-platform-commons/resilient-client.md` 갱신 — 상태 전이 로그·fallback overload 섹션 추가 (wiki-curator 작업).
- v2 후보: Listener 인터페이스, 빌더 default fallback — 요구 발현 시 별도 ADR.

## 백링크 (Links)

- 비교 근거: `[[개발자 의식의 흐름대로 적용해보는 서킷브레이커  우아한형제들 기술블로그]]` (raw, wiki 미승격)
- SDK 스펙: `[[wiki/projects/spring-platform-commons/resilient-client]]`
- 컨벤션: `[[wiki/conventions/java-springboot-hexagonal/modules/client]]`
- 코드: `resilient-client/resilient-client-core/src/main/java/com/ryuqqq/platform/resilient/DefaultResilientClient.java`, `ResilientClient.java`
