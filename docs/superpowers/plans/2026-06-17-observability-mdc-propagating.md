# platform-observability MdcPropagating Implementation Plan

> **스냅샷:** 2026-06-17

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비동기 경계에서 MDC(traceId 등)를 워커 스레드로 전파하는 `MdcPropagating.wrap(Runnable/Callable/Executor)` 정적 유틸을 `platform-observability`에 추가한다.

**Architecture:** 제출 스레드에서 `MDC.getCopyOfContextMap()` 스냅샷을 캡처해 워커 스레드에서 복원하고, 작업 후 워커의 기존 MDC를 `finally`로 원복(스레드풀 재사용 누수 방지). 공개 API는 java 표준 타입만, slf4j는 내부 구현에서만 사용.

**Tech Stack:** Java, slf4j-api(`org.slf4j.MDC`), JUnit5, AssertJ. 테스트는 logback-classic 바인딩 필요.

## Global Constraints

- **slf4j는 `implementation` 의존** — 공개 API(`wrap`)는 `java.util.concurrent`/`java.lang` 타입만 노출. `org.slf4j.MDC`는 내부에서만. 좌표: `libs.slf4j.api` (다른 platform 모듈과 동일 패턴).
- **Spring/JPA 금지** — observability는 프레임워크 비의존(slf4j는 로깅 파사드라 허용). Spring/JPA/Jackson import 금지.
- **추가만 (비파괴)** — 기존 `MdcKeys` 무변경.
- **캡처/복원 시맨틱**: 제출 시 캡처(null 가능) → 워커에서 ①기존 백업 ②캡처 복원(null이면 clear) ③실행 ④finally로 기존 원복(null이면 clear).
- **클래스는 `final` + private 생성자** (정적 유틸).
- 모듈 테스트: `./gradlew :platform-observability:test`.

---

## File Structure

- **Modify** `platform-observability/build.gradle` — `implementation libs.slf4j.api` + `testRuntimeOnly libs.logback.classic`.
- **Create** `platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java` — wrap 3종 + `setOrClear` 헬퍼.
- **Create** `platform-observability/src/test/resources/logback-test.xml` — 테스트 로그 noise 억제(root OFF).
- **Create** `platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java` — 전파·원복·엣지 테스트.

---

## Task 1: 의존성·스캐폴딩 + wrap(Runnable) + 캡처/복원 헬퍼

**Files:**
- Modify: `platform-observability/build.gradle`
- Create: `platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java`
- Create: `platform-observability/src/test/resources/logback-test.xml`
- Create: `platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java`

**Interfaces:**
- Consumes: (없음)
- Produces: `MdcPropagating.wrap(Runnable) -> Runnable` (static). `private static void setOrClear(Map<String,String>)`.

- [ ] **Step 1: build.gradle 의존 추가** — `dependencies` 블록 맨 위(testImplementation 줄들 앞)에 추가:

```groovy
    implementation libs.slf4j.api

    testRuntimeOnly libs.logback.classic
```

- [ ] **Step 2: logback-test.xml 생성 (테스트 noise 억제)**

`platform-observability/src/test/resources/logback-test.xml`:

```xml
<configuration>
    <root level="OFF"/>
</configuration>
```

- [ ] **Step 3: 실패하는 테스트 작성**

`platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java`:

```java
package com.ryuqqq.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcPropagatingTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("wrap(Runnable): 제출 스레드 MDC가 워커 스레드에 전파된다")
    void runnablePropagates() throws Exception {
        MDC.put("traceId", "T-123");
        AtomicReference<String> seen = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(MdcPropagating.wrap(() -> seen.set(MDC.get("traceId")))).get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(seen.get()).isEqualTo("T-123");
    }

    @Test
    @DisplayName("wrap(Runnable): 워커의 기존 MDC가 작업 후 원복된다(누수 없음)")
    void runnableRestoresWorkerContext() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> MDC.put("traceId", "WORKER-OWN")).get();

            MDC.put("traceId", "SUBMITTER");
            pool.submit(MdcPropagating.wrap(() -> {})).get();

            AtomicReference<String> after = new AtomicReference<>();
            pool.submit(() -> after.set(MDC.get("traceId"))).get();
            assertThat(after.get()).isEqualTo("WORKER-OWN");
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("wrap(Runnable): 빈 MDC(캡처 null)도 예외 없이 전파")
    void runnableEmptyContext() throws Exception {
        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("INIT");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(MdcPropagating.wrap(() -> seen.set(MDC.get("traceId")))).get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("wrap(Runnable): 작업이 예외를 던져도 제출 스레드 MDC는 보존")
    void runnableRestoresOnException() {
        MDC.put("traceId", "T-1");
        Runnable wrapped = MdcPropagating.wrap(() -> {
            throw new RuntimeException("boom");
        });
        try {
            wrapped.run();
        } catch (RuntimeException ignored) {
            // expected
        }
        assertThat(MDC.get("traceId")).isEqualTo("T-1");
    }
}
```

- [ ] **Step 4: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: 컴파일 실패 (`MdcPropagating` 클래스 없음).

- [ ] **Step 5: MdcPropagating 구현 (wrap(Runnable) + setOrClear)**

`platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java`:

```java
package com.ryuqqq.platform.observability;

import java.util.Map;
import org.slf4j.MDC;

/**
 * 비동기 경계에서 MDC(traceId 등)를 워커 스레드로 전파한다. 제출 스레드의 MDC 스냅샷을 캡처해
 * 워커에서 복원하고, 작업 후 워커의 기존 컨텍스트를 원복한다(스레드풀 재사용 누수 방지).
 *
 * <pre>{@code
 * executor.submit(MdcPropagating.wrap(() -> doWork()));
 * }</pre>
 */
public final class MdcPropagating {

    private MdcPropagating() {}

    /** 제출 시점 MDC 스냅샷을 워커 스레드에 복원해 실행하는 Runnable로 감싼다. */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            setOrClear(captured);
            try {
                task.run();
            } finally {
                setOrClear(backup);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: PASS (4 tests).

- [ ] **Step 7: 커밋**

```bash
git add platform-observability/build.gradle platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java platform-observability/src/test/resources/logback-test.xml platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java
git commit -m "feat(observability): MdcPropagating.wrap(Runnable) + 캡처/복원 + slf4j 의존"
```

---

## Task 2: wrap(Callable)

**Files:**
- Modify: `platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java`
- Modify: `platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java`

**Interfaces:**
- Consumes: `MdcPropagating.setOrClear`(기존 private).
- Produces: `MdcPropagating.wrap(Callable<V>) -> Callable<V>` (static).

- [ ] **Step 1: 실패하는 테스트 추가** — MdcPropagatingTest에 추가. 상단 import에 `import java.util.concurrent.Callable;` 추가:

```java
    @Test
    @DisplayName("wrap(Callable): MDC 전파 + 반환값 보존")
    void callablePropagatesAndReturns() throws Exception {
        MDC.put("traceId", "C-9");
        Callable<String> task = () -> MDC.get("traceId");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String result = pool.submit(MdcPropagating.wrap(task)).get();
            assertThat(result).isEqualTo("C-9");
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: 컴파일 실패 (`wrap(Callable)` 없음).

- [ ] **Step 3: wrap(Callable) 구현** — MdcPropagating에 import `import java.util.concurrent.Callable;` 추가하고, `wrap(Runnable)` 다음에 메서드 추가:

```java
    /** 제출 시점 MDC 스냅샷을 워커 스레드에 복원해 실행하는 Callable로 감싼다. */
    public static <V> Callable<V> wrap(Callable<V> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            setOrClear(captured);
            try {
                return task.call();
            } finally {
                setOrClear(backup);
            }
        };
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-observability/src/
git commit -m "feat(observability): MdcPropagating.wrap(Callable)"
```

---

## Task 3: wrap(Executor)

**Files:**
- Modify: `platform-observability/src/main/java/com/ryuqqq/platform/observability/MdcPropagating.java`
- Modify: `platform-observability/src/test/java/com/ryuqqq/platform/observability/MdcPropagatingTest.java`

**Interfaces:**
- Consumes: `MdcPropagating.wrap(Runnable)`(기존).
- Produces: `MdcPropagating.wrap(Executor) -> Executor` (static).

- [ ] **Step 1: 실패하는 테스트 추가** — MdcPropagatingTest에 추가. 상단 import에 `import java.util.concurrent.CountDownLatch;`·`import java.util.concurrent.Executor;` 추가:

```java
    @Test
    @DisplayName("wrap(Executor): 감싼 Executor의 execute가 MDC 자동 전파")
    void executorPropagates() throws Exception {
        MDC.put("traceId", "E-7");
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Executor tracing = MdcPropagating.wrap((Executor) pool);
            tracing.execute(() -> {
                seen.set(MDC.get("traceId"));
                latch.countDown();
            });
            latch.await(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(seen.get()).isEqualTo("E-7");
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: 컴파일 실패 (`wrap(Executor)` 없음).

- [ ] **Step 3: wrap(Executor) 구현** — MdcPropagating에 import `import java.util.concurrent.Executor;` 추가하고, `wrap(Callable)` 다음에 메서드 추가:

```java
    /** 모든 execute(Runnable) 호출에 MDC 전파를 자동 적용하는 Executor로 감싼다. */
    public static Executor wrap(Executor delegate) {
        return command -> delegate.execute(wrap(command));
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-observability:test --tests '*MdcPropagatingTest*'`
Expected: PASS (6 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-observability/src/
git commit -m "feat(observability): MdcPropagating.wrap(Executor)"
```

---

## Task 4: 모듈 전체 빌드 — 회귀·게이트 확인

**Files:** (없음 — 검증만)

- [ ] **Step 1: 모듈 전체 빌드**

Run: `./gradlew :platform-observability:build`
Expected: BUILD SUCCESSFUL — 기존 `MdcKeysTest` 포함 전부 통과 + Spotless 포맷 게이트 + SpotBugs 통과. (Spotless 위반이 남으면 `./gradlew :platform-observability:spotlessApply` 적용 후 그 포맷 변경만 `style: spotless 포맷` 별도 커밋하고 재빌드.)

- [ ] **Step 2: 테스트 출력 pristine 확인**

Run: `./gradlew :platform-observability:test --info 2>&1 | grep -iE "ERROR|WARN|exception" | grep -v "PASSED\|test"`
Expected: 출력 없음(logback root OFF로 로그 noise 억제됨). 있으면 원인 보고.

---

## Self-Review 결과

**Spec coverage:**
- wrap(Runnable)/wrap(Callable)/wrap(Executor) — Task 1·2·3 ✓
- slf4j implementation 의존 — Task 1 Step 1 ✓
- 캡처/복원 시맨틱(백업·복원·finally) — Task 1 Step 5 `setOrClear` + 각 wrap ✓
- 워커 MDC 누수 없음 테스트 — Task 1 `runnableRestoresWorkerContext` ✓
- 빈 MDC/예외 케이스 — Task 1 `runnableEmptyContext`·`runnableRestoresOnException` ✓
- slf4j 바인딩 testRuntimeOnly + noise 억제 — Task 1 Step 1·2 ✓
- 비목표(Supplier·ExecutorService 래퍼·outbox dogfood·메트릭) — 계획에 포함 안 함 ✓
- 비파괴(MdcKeys 무변경)·final+private ctor — Global Constraints + Task 1 Step 5 ✓

**Placeholder scan:** 모든 코드 스텝에 완전한 코드. "TBD"/"적절히" 없음. slf4j 바인딩 좌표(`libs.logback.classic`) 확정.

**Type consistency:** `setOrClear(Map<String,String>)` 시그니처가 wrap 3종에서 동일 호출. `wrap(Runnable)→Runnable`, `wrap(Callable<V>)→Callable<V>`, `wrap(Executor)→Executor` 시그니처가 테스트 호출과 일치. Executor 테스트의 `wrap((Executor) pool)` 캐스팅은 ExecutorService가 Executor·ExecutorService 양쪽이라 오버로드 모호성 방지 — 명시 캐스팅으로 해소. Callable 테스트는 `Callable<String> task` 변수로 오버로드 모호성 회피.

---

*최종 갱신: 2026-06-17 — 초판(구현 계획).*
