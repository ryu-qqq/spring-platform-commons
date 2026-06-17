# platform-observability MdcPropagating (MDC 비동기 전파) — 설계

> **스냅샷:** 2026-06-17

> 비동기 실행(ExecutorService·CompletableFuture)에서 MDC(traceId 등)가 유실되는 fleet 공통 통증을
> 해결하는 순수 slf4j 유틸을 추가한다. 작성: 2026-06-17 · 상태: 승인됨(설계) → 구현 계획 단계

## 배경·동기

`platform-observability`는 현재 `MdcKeys`(MDC 키 SSOT)만 제공하고 의존성이 0이다. 그러나 비동기
경계(`ExecutorService.submit`·`CompletableFuture`·`@Async`)를 넘으면 워커 스레드에 제출 스레드의 MDC가
전파되지 않아 **traceId가 유실**된다 — fleet 거의 모든 서비스의 공통 통증. 표준 해법(MDC 스냅샷 캡처 →
워커에서 복원)을 재사용 가능한 유틸로 한 번만 구현한다.

## 범위 — MdcPropagating 정적 유틸 + slf4j-api 의존

`platform-observability`에 **`MdcPropagating`** 클래스 1개 추가. `slf4j-api`를 의존에 추가한다(현재 모듈은
의존 0). 공개 API는 `java.util.concurrent`/`java.lang` 타입만 노출하고 slf4j `org.slf4j.MDC`는 **내부
구현에서만** 사용하므로 **`implementation` 의존**(public 표면에 slf4j 안 나옴). 소비측은 이미 로깅으로
slf4j 런타임을 보유한다.

> 설계 원칙(백로그 기록): observability의 "의존성 0"은 의도가 아닌 우연(MdcKeys가 마침 String)이다.
> 진짜 경계는 "Spring/JPA 금지"이지 "의존성 0"이 아니며, slf4j는 로깅 파사드라 observability 모듈에
> 자연스럽다.

## API 표면 (3개 정적 메서드)

```java
public final class MdcPropagating {
    private MdcPropagating() {}

    /** 제출 시점 MDC 스냅샷을 워커 스레드에 복원해 실행하는 Runnable로 감싼다. */
    public static Runnable wrap(Runnable task);

    /** 제출 시점 MDC 스냅샷을 워커 스레드에 복원해 실행하는 Callable로 감싼다. */
    public static <V> Callable<V> wrap(Callable<V> task);

    /** 모든 execute(Runnable) 호출에 MDC 전파를 자동 적용하는 Executor로 감싼다. */
    public static Executor wrap(Executor delegate);
}
```

- `wrap(Runnable)`/`wrap(Callable)`: `ExecutorService.submit`·`Executor.execute`에 넘길 작업을 감싼다.
- `wrap(Executor)`: Executor 자체를 감싸 내부적으로 `execute(wrap(command))`를 호출 — 소비측이 매 작업마다
  wrap하지 않아도 된다.

## 캡처/복원 시맨틱 (스레드풀 재사용 안전 — 누수 0)

- **제출 스레드**: `wrap` 호출 시점에 `MDC.getCopyOfContextMap()`으로 스냅샷 캡처(`null` 가능 → 빈
  컨텍스트로 취급).
- **워커 스레드** 실행 시:
  1. 워커의 기존 MDC 백업(`MDC.getCopyOfContextMap()`)
  2. 캡처본 복원 — 캡처가 non-null이면 `MDC.setContextMap(captured)`, null이면 `MDC.clear()`
  3. 작업 실행
  4. **`finally`로 워커 기존 MDC 원복** — 백업이 non-null이면 `setContextMap(backup)`, null이면 `clear()`
- 효과: 스레드풀 재사용 시 워커가 원래 갖던 컨텍스트를 보존하고, 작업 간 traceId 누수가 없다. 예외가 나도
  `finally`로 원복 보장.

## 비목표 (YAGNI)

- `wrap(Supplier<T>)` (CompletableFuture `supplyAsync`) — 이번 범위 제외.
- `ExecutorService`/`ScheduledExecutorService` 전체 래퍼 — 제외(`Executor` 래퍼로 `execute` 케이스 커버,
  submit은 `wrap(Callable/Runnable)`로 명시 wrap).
- **platform-outbox dogfood 적용** — 후속 별도 작업(outbox가 observability에 의존하게 되는 모듈 변경).
- micrometer 메트릭 헬퍼 — 드리프트 증거 없어 보류(백로그 기록).

## 호환성·버전

추가만(기존 `MdcKeys` 무변경) → 비파괴. connectly observability는 v0.1.0 발행됨 → 재승격 시 v0.2.x
마이너(후속, 본 범위 밖). `slf4j-api` 의존 추가는 소비측에 영향 없음(이미 보유).

## 테스트

순수 자바 단위 테스트 + 실제 스레드 전파 검증:

- `wrap(Runnable)`: 제출 스레드에서 set한 MDC 값(`traceId`)이 **별도 워커 스레드**
  (`Executors.newSingleThreadExecutor`)에서 보이는지 — `CountDownLatch`로 동기화해 워커가 읽은 값 단언.
- 워커가 작업 전 갖던 MDC 값이 작업 후 **원복**되는지(스레드풀 재사용 누수 없음).
- `wrap(Callable)`: 반환값 정상 + MDC 전파.
- `wrap(Executor)`: 감싼 Executor의 `execute`가 자동 전파.
- 빈 MDC(캡처 `null`) 케이스 — 워커에서 빈 컨텍스트, 예외 없음.
- 작업이 예외를 던질 때 `finally`로 워커 MDC 원복.
- **slf4j 바인딩 필요**: `MDC`가 실제 저장/조회하려면 테스트에 slf4j 바인딩(logback-classic 또는
  slf4j-simple)을 `testRuntimeOnly`로 추가한다(없으면 NOP adapter라 전파를 검증할 수 없음). 구체 바인딩은
  구현 계획에서 버전 카탈로그/BOM 가용분으로 확정.

## 영향 범위

- 변경: `platform-observability` — `MdcPropagating` 1개 추가, `build.gradle`에 `implementation slf4j-api`
  + `testRuntimeOnly` slf4j 바인딩. `MdcKeys` 등 기존 무변경.
- 다른 모듈·소비처 무영향.

---

*최종 갱신: 2026-06-17 — 초판(설계 승인). MdcPropagating wrap(Runnable/Callable/Executor), slf4j implementation 의존, outbox dogfood·Supplier·메트릭 헬퍼는 비목표.*
