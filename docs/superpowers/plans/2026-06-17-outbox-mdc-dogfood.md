# platform-outbox MdcPropagating dogfood Implementation Plan

> **스냅샷:** 2026-06-17

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PerItem outbox relay의 병렬 발송(`CompletableFuture.runAsync`)에 `MdcPropagating.wrap`을 적용해 relay 스레드의 traceId를 워커 발송 스레드로 전파한다.

**Architecture:** `PerItemOutboxRelayTemplate.dispatchAll`의 `runAsync` 람다를 `MdcPropagating.wrap(...)`으로 감싸고, outbox에 `platform-observability` implementation 의존을 추가한다. 기존 테스트의 `FakeAdapter.notify`(워커 스레드 실행)가 본 traceId를 기록해 전파를 검증한다.

**Tech Stack:** Java, slf4j MDC, MdcPropagating(platform-observability), JUnit5, AssertJ.

## Global Constraints

- **`MdcPropagating`은 `implementation` 의존** — outbox 내부 구현에서만 사용, 공개 API 미노출. `platform-outbox`의 `api`는 `platform-common-application`만 유지.
- **PerItem만 적용** — `BatchOutboxRelayTemplate`은 워커 스레드 병렬이 없어 제외.
- 기존 `PerItemOutboxRelayTemplate` 동작(success/deferred/permanent/fail 4분기)·생성자·`relay(int, adapter)` 시그니처 무변경.
- 모듈 테스트: `./gradlew :platform-outbox:test`.
- outbox test는 `spring-boot-starter-test`(logback-classic transitive)라 MDC 실제 동작 — 별도 바인딩 불필요.

---

## File Structure

- **Modify** `platform-outbox/build.gradle` — `implementation project(':platform-observability')` 추가.
- **Modify** `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java` — `MdcPropagating` import + `dispatchAll`의 `runAsync` 람다 wrap.
- **Modify** `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java` — `FakeAdapter`에 traceId 수집 필드 + 전파 테스트 추가.

---

## Task 1: 전파 테스트 + MdcPropagating.wrap 적용 + 의존 추가

**Files:**
- Modify: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java`
- Modify: `platform-outbox/build.gradle`
- Modify: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java`

**Interfaces:**
- Consumes: `MdcPropagating.wrap(Runnable) -> Runnable` (platform-observability, 기존). 기존 `relay(int, adapter)`·`FakeAdapter`.
- Produces: (없음 — 최종 동작 변경)

- [ ] **Step 1: 전파 테스트 작성 (FakeAdapter 수집 필드 + @Test)**

`PerItemOutboxRelayTemplateTest.java` 상단 import에 추가:
```java
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.MDC;
```

`FakeAdapter` 클래스 필드 영역(예: `final List<String> permanent = new ArrayList<>();` 다음)에 추가:
```java
        final ConcurrentLinkedQueue<String> notifyTraceIds = new ConcurrentLinkedQueue<>();
```

`FakeAdapter.notify` 메서드 본문 **첫 줄**에 추가(워커 스레드에서 본 traceId 기록):
```java
            notifyTraceIds.add(String.valueOf(MDC.get("traceId")));
```
(즉 `public void notify(String url, String payload, String idempotencyKey) {` 바로 다음 줄)

클래스 끝(마지막 `}` 직전)에 전파 테스트 추가:
```java
    @Test
    @DisplayName("relay는 제출 스레드의 MDC(traceId)를 워커 발송 스레드로 전파한다")
    void propagatesMdcToWorker() {
        FakeAdapter a = new FakeAdapter();
        claim(a, "o1", "t1", "success");

        MDC.put("traceId", "T-OUTBOX");
        try {
            template.relay(10, a);
        } finally {
            MDC.remove("traceId");
        }

        assertThat(a.notifyTraceIds).containsExactly("T-OUTBOX");
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인(전파 부재)**

Run: `./gradlew :platform-outbox:test --tests '*PerItemOutboxRelayTemplateTest*'`
Expected: FAIL — `propagatesMdcToWorker`에서 `notifyTraceIds`가 `["null"]`(워커 스레드에 traceId 전파 안 됨) → `containsExactly("T-OUTBOX")` 불일치. (기존 테스트는 통과.)

- [ ] **Step 3: build.gradle 의존 추가**

`platform-outbox/build.gradle`의 `dependencies` 블록, `implementation libs.slf4j.api` 다음 줄에 추가:
```groovy
    implementation project(':platform-observability')
```

- [ ] **Step 4: PerItemOutboxRelayTemplate에 wrap 적용**

import 추가(기존 import 블록에 — Spotless가 정렬):
```java
import com.ryuqqq.platform.observability.MdcPropagating;
```

`dispatchAll` 메서드의 `runAsync` 호출을 수정:
```java
        List<CompletableFuture<Void>> futures =
                claimed.stream()
                        .map(
                                outbox ->
                                        CompletableFuture.runAsync(
                                                MdcPropagating.wrap(
                                                        () -> dispatchOne(outbox, taskById, adapter, results)),
                                                executor))
                        .toList();
```
(즉 기존 `() -> dispatchOne(outbox, taskById, adapter, results)`를 `MdcPropagating.wrap(...)`으로 감쌈.)

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*PerItemOutboxRelayTemplateTest*'`
Expected: PASS — `propagatesMdcToWorker` 포함 전체 통과. (`notifyTraceIds`가 `["T-OUTBOX"]`.)

- [ ] **Step 6: 커밋**

```bash
git add platform-outbox/build.gradle platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java
git commit -m "feat(outbox): PerItem relay 병렬 발송에 MdcPropagating 적용 (traceId 전파 dogfood)"
```

---

## Task 2: 모듈 전체 빌드 — 회귀·게이트 확인

**Files:** (없음 — 검증만)

- [ ] **Step 1: 모듈 전체 빌드**

Run: `./gradlew :platform-outbox:build`
Expected: BUILD SUCCESSFUL — 기존 outbox 테스트(4분기·preload·autoconfig 등) 전부 통과 + Spotless + SpotBugs. (Spotless 위반 시 `./gradlew :platform-outbox:spotlessApply` 적용 후 그 포맷 변경만 `style: spotless 포맷` 별도 커밋하고 재빌드.)

- [ ] **Step 2: 의존 추가 확인 (api 누수 없음)**

Run: `./gradlew :platform-outbox:dependencies --configuration api 2>&1 | grep -i observability || echo "api에 observability 없음(정상)"`
Expected: "api에 observability 없음(정상)" — observability는 implementation으로만 들어가 소비측 api로 새지 않음.

---

## Self-Review 결과

**Spec coverage:**
- PerItem runAsync에 MdcPropagating.wrap 적용 — Task 1 Step 4 ✓
- observability implementation 의존 — Task 1 Step 3 ✓
- 전파 검증 테스트(워커 스레드 traceId) — Task 1 Step 1·5 ✓
- Batch 제외 — 계획에 포함 안 함 ✓
- 기존 동작 무변경(4분기 등) — Task 2 회귀 확인 ✓
- implementation api 누수 없음 — Task 2 Step 2 ✓

**Placeholder scan:** 모든 코드 스텝에 완전한 코드. "TBD" 없음.

**Type consistency:** `MdcPropagating.wrap(Runnable)→Runnable`이 `runAsync(Runnable, Executor)`의 첫 인자 타입과 일치(람다는 Runnable). `notifyTraceIds`(ConcurrentLinkedQueue<String>)는 `containsExactly("T-OUTBOX")`와 호환(Collection). `relay(10, a)`·`claim(a, id, taskId, outcome)`는 기존 테스트 시그니처와 일치. RED는 전파 부재(`["null"]`), GREEN은 전파(`["T-OUTBOX"]`)로 결정적.
