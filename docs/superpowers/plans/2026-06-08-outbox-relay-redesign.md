# platform-outbox 2단계 (트랜스포트 중립 relay 재설계) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** platform-outbox의 Queue 트랜스포트 어휘를 중립화(N1)하고, 공유 `OutboxStore<O>` 수명주기 base 위에 Batch·PerItem 두 트랜스포트 relay 템플릿을 둔다 (+ `OutboxStatus`·`OutboxRetryPolicy`·dispatch 예외).

**Architecture:** 공유 base SPI `OutboxStore<O>`(claim·bulkMark·release)를 두 어댑터가 상속한다. 배치는 `BatchOutboxAdapter`(+dispatchBatch)·`BatchOutboxRelayTemplate`(1단계 로직 개명). 건별은 `PerItemOutboxAdapter<O,T,P>`(+notify·preload·defer·permanent)·`PerItemOutboxRelayTemplate`(executor 병렬 4분기). 1단계 `Queue*`는 소비자 0이라 파괴적 개명.

**Tech Stack:** Java 21, Spring Boot 3.5.6 autoconfigure, Micrometer 1.14.3, JUnit5 + AssertJ, Gradle 멀티모듈.

**Spec:** `docs/superpowers/specs/2026-06-08-outbox-relay-redesign-design.md`

---

## File Structure

```text
platform-common-domain/src/main/java/com/ryuqqq/platform/common/outbox/
  OutboxStatus.java                                    # enum (Task 1)
platform-outbox/src/main/java/com/ryuqqq/platform/outbox/
  spi/OutboxStore.java                                 # 공유 수명주기 base (Task 2)
  spi/BatchOutboxAdapter.java                          # extends OutboxStore (Task 2)
  spi/PerItemOutboxAdapter.java                        # extends OutboxStore (Task 4)
  dto/OutboxDispatchCommand.java                       # (Task 2, 1단계 OutboxEnqueueCommand 개명)
  dto/OutboxBatchDispatchResult.java                   # (Task 2, 1단계 OutboxBatchSendResult 개명)
  OutboxRetryPolicy.java                               # record (Task 3)
  exception/OutboxDispatchDeferredException.java       # (Task 3)
  exception/OutboxDispatchPermanentException.java      # (Task 3)
  BatchOutboxRelayTemplate.java                        # (Task 2, 1단계 QueueOutboxRelayTemplate 개명)
  PerItemOutboxRelayTemplate.java                      # (Task 5)
  config/PlatformOutboxAutoConfiguration.java          # (Task 2, Batch 빈으로 개명)
삭제: spi/QueueOutboxAdapter.java · QueueOutboxRelayTemplate.java · dto/OutboxEnqueueCommand.java · dto/OutboxBatchSendResult.java (Task 2)
```

---

## Task 1: OutboxStatus enum (→ common-domain)

**Files:**
- Create: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/outbox/OutboxStatus.java`
- Test: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/outbox/OutboxStatusTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-common-domain/src/test/java/com/ryuqqq/platform/common/outbox/OutboxStatusTest.java`:

```java
package com.ryuqqq.platform.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxStatusTest {

    @Test
    @DisplayName("outbox 수명주기 4상태를 가진다")
    void hasFourStates() {
        assertThat(OutboxStatus.values())
                .containsExactly(
                        OutboxStatus.PENDING,
                        OutboxStatus.PROCESSING,
                        OutboxStatus.SENT,
                        OutboxStatus.FAILED);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*OutboxStatusTest'`
Expected: FAIL — `OutboxStatus` 없음 (컴파일 에러).

- [ ] **Step 3: 구현**

Create `platform-common-domain/src/main/java/com/ryuqqq/platform/common/outbox/OutboxStatus.java`:

```java
package com.ryuqqq.platform.common.outbox;

/** 아웃박스 처리 상태. PENDING → PROCESSING → SENT | FAILED. */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*OutboxStatusTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/outbox/ platform-common-domain/src/test/java/com/ryuqqq/platform/common/outbox/
git commit -m "feat(outbox): OutboxStatus enum → common-domain

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 배치 stack 중립 개명 (N1 해소) + 공유 OutboxStore base

> 1단계 `Queue*` 파일을 삭제하고 중립 이름으로 재작성한다. 소비자 0이라 deprecation 별칭 불필요. 로직은 1단계와 동일(개명만).

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/OutboxStore.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/BatchOutboxAdapter.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxDispatchCommand.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxBatchDispatchResult.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/BatchOutboxRelayTemplate.java`
- Modify: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java`
- Modify: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/package-info.java`
- Delete: `spi/QueueOutboxAdapter.java`, `QueueOutboxRelayTemplate.java`, `dto/OutboxEnqueueCommand.java`, `dto/OutboxBatchSendResult.java`
- Test: rename `QueueOutboxRelayTemplateTest.java`→`BatchOutboxRelayTemplateTest.java`, `OutboxBatchSendResultTest.java`→`OutboxBatchDispatchResultTest.java`; update `PlatformOutboxAutoConfigurationTest.java`

- [ ] **Step 1: 테스트 개명·갱신 (실패 상태로)**

`git mv` 후 참조 치환:

```bash
cd platform-outbox/src/test/java/com/ryuqqq/platform/outbox
git mv QueueOutboxRelayTemplateTest.java BatchOutboxRelayTemplateTest.java
git mv dto/OutboxBatchSendResultTest.java dto/OutboxBatchDispatchResultTest.java
cd -
```

`BatchOutboxRelayTemplateTest.java`에서 전역 치환: `QueueOutboxRelayTemplate`→`BatchOutboxRelayTemplate`, `QueueOutboxAdapter`→`BatchOutboxAdapter`, `OutboxBatchSendResult`→`OutboxBatchDispatchResult`, `OutboxEnqueueCommand`→`OutboxDispatchCommand`, `enqueueBatch`→`dispatchBatch`, `enqueueResult`→`dispatchResult`, `enqueueException`→`dispatchException`, `enqueued`→`dispatched`, class `FakeQueueOutboxAdapter`→`FakeBatchOutboxAdapter`. (`implements QueueOutboxAdapter<TestOutbox>`→`implements BatchOutboxAdapter<TestOutbox>`.)

`dto/OutboxBatchDispatchResultTest.java`에서: `OutboxBatchSendResult`→`OutboxBatchDispatchResult`.

`config/PlatformOutboxAutoConfigurationTest.java`에서: `QueueOutboxRelayTemplate`→`BatchOutboxRelayTemplate`, `new QueueOutboxRelayTemplate(`→`new BatchOutboxRelayTemplate(`.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:compileTestJava`
Expected: FAIL — 새 타입(BatchOutboxRelayTemplate 등) 없음 (컴파일 에러).

- [ ] **Step 3: 공유 base + 배치 SPI/DTO 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/OutboxStore.java`:

```java
package com.ryuqqq.platform.outbox.spi;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 릴레이의 트랜스포트 무관 수명주기 base SPI (소비측 구현).
 *
 * <p>claim → mark(SENT/FAILED) → release 전이는 발행 채널(배치 큐·건별 HTTP 등)과 무관하게 공통이다.
 * 트랜스포트별 어댑터({@link BatchOutboxAdapter}·{@code PerItemOutboxAdapter})가 이 base 를 상속해
 * 자기 dispatch 메서드를 더한다.
 *
 * @param <O> 도메인 Outbox 타입
 */
public interface OutboxStore<O> {

    /** 로그 라벨 (예: "다운로드"). */
    String label();

    /** 메트릭 태그용 파이프라인 식별자 — 저카디널리티. */
    String pipeline();

    /** outbox 자체의 ID — bulkMark 대상. */
    String outboxId(O outbox);

    /** PENDING → PROCESSING 원자 claim 후 도메인 객체 목록 반환. */
    List<O> claimPendingMessages(int batchSize);

    /** 성공한 outbox 들을 PROCESSING → SENT 로 일괄 마킹. */
    void bulkMarkSent(List<String> outboxIds, Instant now);

    /** 실패한 outbox 들을 일괄 마킹 (재시도 증가 또는 dead-letter 는 구현체 정책). */
    void bulkMarkFailed(List<String> outboxIds, Instant now, String errorMessage);

    /** dispatch 인프라 예외 시 PROCESSING → PENDING 무차감 복귀 (retry_count 유지). */
    void bulkReleaseToPending(List<String> outboxIds);
}
```

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxDispatchCommand.java`:

```java
package com.ryuqqq.platform.outbox.dto;

/** 배치 dispatch 시 발행에 필요한 business id + 멱등키 쌍. */
public record OutboxDispatchCommand(String businessId, String idempotencyKey) {}
```

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxBatchDispatchResult.java`:

```java
package com.ryuqqq.platform.outbox.dto;

import java.util.List;

/** 배치 dispatch 결과 — 건별 성공/실패를 추적한다. */
public record OutboxBatchDispatchResult(List<String> successIds, List<FailedEntry> failedEntries) {

    /** null 입력은 빈 리스트로 정규화하고 방어적 복사로 불변성을 보장한다. */
    public OutboxBatchDispatchResult {
        successIds = (successIds == null) ? List.of() : List.copyOf(successIds);
        failedEntries = (failedEntries == null) ? List.of() : List.copyOf(failedEntries);
    }

    /** 발행 실패 항목 — business id 와 에러 메시지. */
    public record FailedEntry(String id, String errorMessage) {}

    public static OutboxBatchDispatchResult allSuccess(List<String> ids) {
        return new OutboxBatchDispatchResult(ids, List.of());
    }

    public static OutboxBatchDispatchResult of(
            List<String> successIds, List<FailedEntry> failedEntries) {
        return new OutboxBatchDispatchResult(successIds, failedEntries);
    }

    public boolean hasFailures() {
        return !failedEntries.isEmpty();
    }
}
```

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/BatchOutboxAdapter.java`:

```java
package com.ryuqqq.platform.outbox.spi;

import com.ryuqqq.platform.outbox.dto.OutboxBatchDispatchResult;
import com.ryuqqq.platform.outbox.dto.OutboxDispatchCommand;
import java.util.List;

/**
 * 배치 발행 트랜스포트의 Outbox 어댑터 — {@link OutboxStore} 수명주기에 배치 dispatch 를 더한다.
 *
 * @param <O> 도메인 Outbox 타입
 */
public interface BatchOutboxAdapter<O> extends OutboxStore<O> {

    /** outbox 가 참조하는 비즈니스 ID — 발행 메시지 본문이자 dispatchResult 매칭 키. */
    String businessId(O outbox);

    /** 릴레이·발행 메시지에 전달할 멱등키. */
    String idempotencyKey(O outbox);

    /**
     * business id·멱등키 배치를 발행하고 성공·실패 분리 결과를 반환.
     *
     * <p><b>계약:</b> 입력 command 의 모든 business id 는 결과의 {@code successIds} 또는
     * {@code failedEntries} 중 정확히 한 곳에 포함되어야 한다. 어느 쪽에도 없는 항목은 PROCESSING 상태로
     * 남아 stuck 될 수 있다. 또한 {@code businessId} 는 한 배치 내에서 유일해야 한다(매칭 상관키) —
     * 유일성이 보장되지 않으면 businessId 에 outbox 별 유일 값(예: idempotencyKey)을 싣는다.
     */
    OutboxBatchDispatchResult dispatchBatch(List<OutboxDispatchCommand> commands);
}
```

- [ ] **Step 4: 배치 템플릿 구현 + 자동설정·package-info 갱신, 구 파일 삭제**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/BatchOutboxRelayTemplate.java`:

```java
package com.ryuqqq.platform.outbox;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.dto.OutboxBatchDispatchResult;
import com.ryuqqq.platform.outbox.dto.OutboxDispatchCommand;
import com.ryuqqq.platform.outbox.spi.BatchOutboxAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 배치 발행 트랜스포트의 Outbox 릴레이 공통 흐름.
 *
 * <p>claim → {@link BatchOutboxAdapter#dispatchBatch(List)} → 성공·실패 분리 bulkMark. 예외 안전: try 는
 * dispatchBatch 만 감싼다 — bulk 마킹은 try 밖에서 실행되어 markSent 예외가 claim 전체를 markFailed 로 덮어쓰지
 * 않는다. dispatch 인프라 예외는 retry 무차감으로 {@link BatchOutboxAdapter#bulkReleaseToPending(List)} 복귀.
 */
public class BatchOutboxRelayTemplate {

    private static final Logger log = LoggerFactory.getLogger(BatchOutboxRelayTemplate.class);
    private static final int MAX_ERROR_MESSAGES_IN_SUMMARY = 3;
    private static final String METRIC_RELAY = "outbox.relay";

    /** nullable — null 이면 메트릭 no-op. */
    private final MeterRegistry meterRegistry;

    public BatchOutboxRelayTemplate(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <O> SchedulerBatchProcessingResult relay(int batchSize, BatchOutboxAdapter<O> adapter) {
        List<O> claimed = adapter.claimPendingMessages(batchSize);
        if (claimed == null || claimed.isEmpty()) {
            return SchedulerBatchProcessingResult.empty();
        }

        List<String> claimedOutboxIds = claimed.stream().map(adapter::outboxId).toList();

        OutboxBatchDispatchResult dispatchResult;
        try {
            List<OutboxDispatchCommand> commands =
                    claimed.stream()
                            .map(
                                    o ->
                                            new OutboxDispatchCommand(
                                                    adapter.businessId(o), adapter.idempotencyKey(o)))
                            .toList();
            dispatchResult = adapter.dispatchBatch(commands);
        } catch (Exception e) {
            log.error(
                    "{} 배치 발행 중 인프라 예외, PROCESSING → PENDING 무차감 복귀: count={}",
                    adapter.label(),
                    claimedOutboxIds.size(),
                    e);
            adapter.bulkReleaseToPending(claimedOutboxIds);
            return SchedulerBatchProcessingResult.of(claimed.size(), 0, 0);
        }

        return applyDispatchResults(claimed, dispatchResult, adapter);
    }

    private <O> SchedulerBatchProcessingResult applyDispatchResults(
            List<O> claimed, OutboxBatchDispatchResult dispatchResult, BatchOutboxAdapter<O> adapter) {
        Instant now = Instant.now();

        Set<String> successBusinessIds = new HashSet<>(dispatchResult.successIds());
        List<String> successOutboxIds =
                claimed.stream()
                        .filter(o -> successBusinessIds.contains(adapter.businessId(o)))
                        .map(adapter::outboxId)
                        .toList();
        adapter.bulkMarkSent(successOutboxIds, now);

        Set<String> failedBusinessIds =
                dispatchResult.failedEntries().stream()
                        .map(OutboxBatchDispatchResult.FailedEntry::id)
                        .collect(Collectors.toSet());
        List<String> failedOutboxIds =
                claimed.stream()
                        .filter(o -> failedBusinessIds.contains(adapter.businessId(o)))
                        .map(adapter::outboxId)
                        .toList();

        if (dispatchResult.hasFailures()) {
            String errorSummary = summarizeErrors(dispatchResult);
            adapter.bulkMarkFailed(failedOutboxIds, now, errorSummary);
            log.warn(
                    "{} 배치 발행 부분 실패: total={}, success={}, failed={}, error={}",
                    adapter.label(),
                    claimed.size(),
                    successOutboxIds.size(),
                    failedOutboxIds.size(),
                    errorSummary);
        }

        recordRelay(adapter.pipeline(), "success", successOutboxIds.size());
        recordRelay(adapter.pipeline(), "failure", failedOutboxIds.size());

        return SchedulerBatchProcessingResult.of(
                claimed.size(), successOutboxIds.size(), failedOutboxIds.size());
    }

    private void recordRelay(String pipeline, String result, int count) {
        if (meterRegistry == null || count == 0) {
            return;
        }
        try {
            Counter.builder(METRIC_RELAY)
                    .tag("pipeline", pipeline)
                    .tag("result", result)
                    .register(meterRegistry)
                    .increment(count);
        } catch (Exception e) {
            log.warn("relay 메트릭 기록 실패 (무시): {}", e.getMessage());
        }
    }

    private static String summarizeErrors(OutboxBatchDispatchResult dispatchResult) {
        return dispatchResult.failedEntries().stream()
                .map(OutboxBatchDispatchResult.FailedEntry::errorMessage)
                .distinct()
                .limit(MAX_ERROR_MESSAGES_IN_SUMMARY)
                .collect(Collectors.joining("; "));
    }
}
```

Modify `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java` — `QueueOutboxRelayTemplate`→`BatchOutboxRelayTemplate` 치환 (import·빈 타입·메서드명 `queueOutboxRelayTemplate`→`batchOutboxRelayTemplate`):

```java
package com.ryuqqq.platform.outbox.config;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.BatchOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Outbox 자동 설정 — 배치 트랜스포트 {@link BatchOutboxRelayTemplate} 빈을 등록한다.
 *
 * <p>{@link MeterRegistry} 는 optional. 건별 트랜스포트({@code PerItemOutboxRelayTemplate})는 executor·
 * defer 윈도가 소비자별이라 소비측이 직접 인스턴스화한다 — 여기서 등록하지 않는다.
 */
@AutoConfiguration
@ConditionalOnClass(SchedulerBatchProcessingResult.class)
public class PlatformOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BatchOutboxRelayTemplate batchOutboxRelayTemplate(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new BatchOutboxRelayTemplate(meterRegistryProvider.getIfAvailable());
    }
}
```

Modify `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/package-info.java` 본문에서 `QueueOutboxRelayTemplate`→`BatchOutboxRelayTemplate`, `QueueOutboxAdapter`→`BatchOutboxAdapter`로 치환 (의미 동일, 링크만 갱신).

구 파일 삭제:

```bash
cd /Users/ryu-qqq/Documents/ryu-qqq/spring-platform-commons
git rm platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/QueueOutboxAdapter.java \
       platform-outbox/src/main/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplate.java \
       platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxEnqueueCommand.java \
       platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxBatchSendResult.java
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test`
Expected: PASS (배치 12 테스트 — 개명 후 동일).

- [ ] **Step 6: 커밋**

```bash
git add platform-outbox/ && git commit -m "refactor(outbox): Queue→Batch 중립 개명 + 공유 OutboxStore base (N1 해소)

abstraction-critic N1(트랜스포트 어휘 박힘) 해소. QueueOutboxAdapter→OutboxStore
+BatchOutboxAdapter, QueueOutboxRelayTemplate→BatchOutboxRelayTemplate,
enqueueBatch→dispatchBatch. 소비자 0이라 파괴적 개명.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: OutboxRetryPolicy + dispatch 예외 2종

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/OutboxRetryPolicy.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/exception/OutboxDispatchDeferredException.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/exception/OutboxDispatchPermanentException.java`
- Test: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/OutboxRetryPolicyTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/OutboxRetryPolicyTest.java`:

```java
package com.ryuqqq.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    @DisplayName("defaults: 5회·6시간")
    void defaults() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();

        assertThat(policy.maxRetries()).isEqualTo(5);
        assertThat(policy.maxDeferDuration()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    @DisplayName("of: 소비자 정책 override")
    void custom() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.of(3, Duration.ofMinutes(30));

        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.maxDeferDuration()).isEqualTo(Duration.ofMinutes(30));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:test --tests '*OutboxRetryPolicyTest'`
Expected: FAIL — `OutboxRetryPolicy` 없음.

- [ ] **Step 3: 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/OutboxRetryPolicy.java`:

```java
package com.ryuqqq.platform.outbox;

import java.time.Duration;

/**
 * Outbox 재시도 정책 — 소비자 구성 가능.
 *
 * @param maxRetries 일반 실패 재시도 최대 횟수 (초과 시 FAILED dead-letter). 소비자 store 구현이 적용.
 * @param maxDeferDuration 일시 장애 defer 최대 시간. 건별 릴레이 템플릿이 deferRetry 에 주입.
 */
public record OutboxRetryPolicy(int maxRetries, Duration maxDeferDuration) {

    /** 편의 기본값 — 5회·6시간. */
    public static OutboxRetryPolicy defaults() {
        return new OutboxRetryPolicy(5, Duration.ofHours(6));
    }

    public static OutboxRetryPolicy of(int maxRetries, Duration maxDeferDuration) {
        return new OutboxRetryPolicy(maxRetries, maxDeferDuration);
    }
}
```

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/exception/OutboxDispatchDeferredException.java`:

```java
package com.ryuqqq.platform.outbox.exception;

/**
 * 일시 장애로 재시도를 연기해야 함을 알리는 흐름제어 시그널.
 *
 * <p>건별 dispatch({@code notify})에서 외부 일시 장애(예: Circuit Breaker OPEN)일 때 던진다. 릴레이는 retry
 * 횟수를 소진하지 않고 defer 한다. 특정 라이브러리에 의존하지 않는다.
 */
public class OutboxDispatchDeferredException extends RuntimeException {

    public OutboxDispatchDeferredException(String message) {
        super(message);
    }

    public OutboxDispatchDeferredException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/exception/OutboxDispatchPermanentException.java`:

```java
package com.ryuqqq.platform.outbox.exception;

/**
 * 재시도가 무의미한 영구 실패를 알리는 흐름제어 시그널.
 *
 * <p>건별 dispatch({@code notify})에서 영구 실패(예: HTTP 4xx)일 때 던진다. 릴레이는 즉시 종결
 * (markFailedPermanently)한다. 외부로 전파되지 않는 내부 시그널.
 */
public class OutboxDispatchPermanentException extends RuntimeException {

    public OutboxDispatchPermanentException(String message) {
        super(message);
    }

    public OutboxDispatchPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*OutboxRetryPolicyTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/OutboxRetryPolicy.java platform-outbox/src/main/java/com/ryuqqq/platform/outbox/exception/ platform-outbox/src/test/java/com/ryuqqq/platform/outbox/OutboxRetryPolicyTest.java
git commit -m "feat(outbox): OutboxRetryPolicy + dispatch 예외(deferred/permanent) 시그널

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: PerItemOutboxAdapter SPI

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/PerItemOutboxAdapter.java`

> 단위 테스트 없음 — Task 5 의 건별 템플릿 테스트가 fake 구현체로 계약을 행사.

- [ ] **Step 1: 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/PerItemOutboxAdapter.java`:

```java
package com.ryuqqq.platform.outbox.spi;

import com.ryuqqq.platform.common.outbox.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 건별 발송 트랜스포트의 Outbox 어댑터 — {@link OutboxStore} 수명주기에 건별 dispatch·4분기 전이를 더한다.
 *
 * <p>건별(예: HTTP 콜백)은 배치와 달리 항목마다 성공/연기/영구실패/실패 4갈래로 갈리고 부모 작업 preload·
 * 페이로드 빌드가 필요하다.
 *
 * @param <O> 도메인 Outbox 타입
 * @param <T> 페이로드 빌드용 부모 작업 타입
 * @param <P> 외부에 발송할 페이로드 타입
 */
public interface PerItemOutboxAdapter<O, T, P> extends OutboxStore<O> {

    /** 페이로드 빌드를 위한 부모 작업 ID — preloadTasks 의 키. */
    String taskId(O outbox);

    /** outbox 상태 (deferRetry 후 FAILED 전이 감지용). */
    OutboxStatus outboxStatus(O outbox);

    /** 발송 대상 URL — 로그·재시도 식별용. */
    String callbackUrl(O outbox);

    /** 외부 생성 시각 — defer 경과시간 로깅용. */
    Instant createdAt(O outbox);

    /** 발송 시 전달할 멱등키. */
    String idempotencyKey(O outbox);

    /** N+1 회피용 batch 조회. 빈 ids 면 빈 Map. */
    Map<String, T> preloadTasks(List<String> taskIds);

    /** 외부에 발송할 페이로드 빌드. */
    P buildPayload(O outbox, T task);

    /**
     * 외부 건별 통지. 실패는 예외로 신호:
     * {@link com.ryuqqq.platform.outbox.exception.OutboxDispatchDeferredException}(일시→defer),
     * {@link com.ryuqqq.platform.outbox.exception.OutboxDispatchPermanentException}(영구→종결),
     * 그 외 예외(일반 실패→retry).
     */
    void notify(String url, P payload, String idempotencyKey);

    /** 일시 장애 — 도메인 deferRetry 호출 + persist. defer 한도는 템플릿이 주입. */
    void deferRetry(O outbox, Instant now, Duration maxDeferDuration);

    /** 영구 실패 — 도메인 markFailedPermanently 호출 + persist. */
    void markFailedPermanently(O outbox, String errorMessage, Instant now);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :platform-outbox:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/PerItemOutboxAdapter.java
git commit -m "feat(outbox): PerItemOutboxAdapter SPI (건별 트랜스포트, 4분기)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: PerItemOutboxRelayTemplate

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java`
- Test: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java`:

```java
package com.ryuqqq.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.common.outbox.OutboxStatus;
import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.exception.OutboxDispatchDeferredException;
import com.ryuqqq.platform.outbox.exception.OutboxDispatchPermanentException;
import com.ryuqqq.platform.outbox.spi.PerItemOutboxAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerItemOutboxRelayTemplateTest {

    /** 테스트용 도메인 outbox. dispatchOutcome 으로 fake notify 동작을 지정. */
    static final class TestOutbox {
        final String outboxId;
        final String taskId;
        final String url;
        final String idem;
        final Instant createdAt;
        String outcome = "success"; // success | deferred | permanent | fail | notask
        OutboxStatus status = OutboxStatus.PROCESSING;

        TestOutbox(String outboxId, String taskId) {
            this.outboxId = outboxId;
            this.taskId = taskId;
            this.url = "https://cb/" + outboxId;
            this.idem = "idem-" + outboxId;
            this.createdAt = Instant.parse("2026-06-08T00:00:00Z");
        }
    }

    static final class FakeAdapter implements PerItemOutboxAdapter<TestOutbox, String, String> {
        List<TestOutbox> toClaim = new ArrayList<>();
        final Map<String, String> tasks = new HashMap<>(); // taskId -> task

        final List<String> markedSent = new ArrayList<>();
        final List<String> markedFailed = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final List<String> deferred = new ArrayList<>();
        final List<String> permanent = new ArrayList<>();
        Duration deferDurationSeen;
        boolean preloadThrows = false;

        @Override public String label() { return "테스트콜백"; }
        @Override public String pipeline() { return "callback"; }
        @Override public String outboxId(TestOutbox o) { return o.outboxId; }
        @Override public List<TestOutbox> claimPendingMessages(int batchSize) { return toClaim; }
        @Override public void bulkMarkSent(List<String> ids, Instant now) { markedSent.addAll(ids); }
        @Override public void bulkMarkFailed(List<String> ids, Instant now, String msg) { markedFailed.addAll(ids); }
        @Override public void bulkReleaseToPending(List<String> ids) { released.addAll(ids); }

        @Override public String taskId(TestOutbox o) { return o.taskId; }
        @Override public OutboxStatus outboxStatus(TestOutbox o) { return o.status; }
        @Override public String callbackUrl(TestOutbox o) { return o.url; }
        @Override public Instant createdAt(TestOutbox o) { return o.createdAt; }
        @Override public String idempotencyKey(TestOutbox o) { return o.idem; }

        @Override
        public Map<String, String> preloadTasks(List<String> taskIds) {
            if (preloadThrows) throw new RuntimeException("DB down");
            Map<String, String> m = new HashMap<>();
            for (String id : taskIds) if (tasks.containsKey(id)) m.put(id, tasks.get(id));
            return m;
        }

        @Override public String buildPayload(TestOutbox o, String task) { return "payload:" + task; }

        @Override
        public void notify(String url, String payload, String idempotencyKey) {
            // outcome 은 url 로 역추적 (병렬 안전, outbox 식별)
            TestOutbox o = toClaim.stream().filter(x -> x.url.equals(url)).findFirst().orElseThrow();
            switch (o.outcome) {
                case "deferred" -> throw new OutboxDispatchDeferredException("CB OPEN");
                case "permanent" -> throw new OutboxDispatchPermanentException("4xx");
                case "fail" -> throw new RuntimeException("5xx");
                default -> { /* success */ }
            }
        }

        @Override
        public void deferRetry(TestOutbox o, Instant now, Duration maxDeferDuration) {
            deferred.add(o.outboxId);
            deferDurationSeen = maxDeferDuration;
        }

        @Override
        public void markFailedPermanently(TestOutbox o, String errorMessage, Instant now) {
            permanent.add(o.outboxId);
        }
    }

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final PerItemOutboxRelayTemplate template =
            new PerItemOutboxRelayTemplate(Duration.ofHours(6), executor, registry);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private TestOutbox claim(FakeAdapter a, String id, String taskId, String outcome) {
        TestOutbox o = new TestOutbox(id, taskId);
        o.outcome = outcome;
        a.toClaim.add(o);
        a.tasks.put(taskId, "T-" + taskId);
        return o;
    }

    @Test
    @DisplayName("claim 이 비면 empty(), preload 미호출")
    void emptyClaim() {
        FakeAdapter a = new FakeAdapter();
        SchedulerBatchProcessingResult r = template.relay(10, a);
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.empty());
        assertThat(a.markedSent).isEmpty();
    }

    @Test
    @DisplayName("4분기: success→markSent, fail→markFailed, deferred→deferRetry, permanent→markFailedPermanently")
    void fourOutcomes() {
        FakeAdapter a = new FakeAdapter();
        claim(a, "o1", "t1", "success");
        claim(a, "o2", "t2", "fail");
        claim(a, "o3", "t3", "deferred");
        claim(a, "o4", "t4", "permanent");

        SchedulerBatchProcessingResult r = template.relay(10, a);

        assertThat(a.markedSent).containsExactly("o1");
        assertThat(a.markedFailed).containsExactly("o2");
        assertThat(a.deferred).containsExactly("o3");
        assertThat(a.permanent).containsExactly("o4");
        assertThat(a.deferDurationSeen).isEqualTo(Duration.ofHours(6));
        // total=4, success=1, failed = fail(1)+permanent(1) = 2
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(4, 1, 2));
    }

    @Test
    @DisplayName("부모 작업 preload 누락 항목은 failure 로 분기")
    void taskNotFoundIsFailure() {
        FakeAdapter a = new FakeAdapter();
        TestOutbox o = new TestOutbox("o1", "t1");
        o.outcome = "success";
        a.toClaim.add(o); // tasks 에 t1 안 넣음 → preload 결과 없음

        template.relay(10, a);

        assertThat(a.markedFailed).containsExactly("o1");
        assertThat(a.markedSent).isEmpty();
    }

    @Test
    @DisplayName("deferRetry 후 OutboxStatus.FAILED 면 최종 실패 로깅 경로(예외 없이 통과)")
    void deferThenFailedDetected() {
        FakeAdapter a = new FakeAdapter();
        TestOutbox o = claim(a, "o1", "t1", "deferred");
        o.status = OutboxStatus.FAILED; // deferRetry 결과 도메인이 FAILED 로 종결했다고 가정

        SchedulerBatchProcessingResult r = template.relay(10, a);

        assertThat(a.deferred).containsExactly("o1");
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(1, 0, 0)); // deferred 는 success/failed 아님
    }

    @Test
    @DisplayName("preload 인프라 예외 → 전체 bulkReleaseToPending, 결과 (n,0,0)")
    void preloadInfraException() {
        FakeAdapter a = new FakeAdapter();
        claim(a, "o1", "t1", "success");
        claim(a, "o2", "t2", "success");
        a.preloadThrows = true;

        SchedulerBatchProcessingResult r = template.relay(10, a);

        assertThat(a.released).containsExactlyInAnyOrder("o1", "o2");
        assertThat(a.markedSent).isEmpty();
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(2, 0, 0));
    }

    @Test
    @DisplayName("outbox.relay 카운터에 success/deferred/permanent_failure/failure 기록")
    void recordsMetrics() {
        FakeAdapter a = new FakeAdapter();
        claim(a, "o1", "t1", "success");
        claim(a, "o2", "t2", "fail");
        claim(a, "o3", "t3", "deferred");
        claim(a, "o4", "t4", "permanent");

        template.relay(10, a);

        assertThat(counter("success")).isEqualTo(1.0);
        assertThat(counter("failure")).isEqualTo(1.0);
        assertThat(counter("deferred")).isEqualTo(1.0);
        assertThat(counter("permanent_failure")).isEqualTo(1.0);
    }

    private double counter(String result) {
        return registry.get("outbox.relay").tag("pipeline", "callback").tag("result", result).counter().count();
    }

    @Test
    @DisplayName("MeterRegistry null 이어도 NPE 없이 동작")
    void nullMeterRegistry() {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            PerItemOutboxRelayTemplate t = new PerItemOutboxRelayTemplate(Duration.ofHours(6), ex, null);
            FakeAdapter a = new FakeAdapter();
            claim(a, "o1", "t1", "success");

            SchedulerBatchProcessingResult r = t.relay(10, a);

            assertThat(a.markedSent).containsExactly("o1");
            assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(1, 1, 0));
        } finally {
            ex.shutdownNow();
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:test --tests '*PerItemOutboxRelayTemplateTest'`
Expected: FAIL — `PerItemOutboxRelayTemplate` 없음.

- [ ] **Step 3: 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java`:

```java
package com.ryuqqq.platform.outbox;

import com.ryuqqq.platform.common.outbox.OutboxStatus;
import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.exception.OutboxDispatchDeferredException;
import com.ryuqqq.platform.outbox.exception.OutboxDispatchPermanentException;
import com.ryuqqq.platform.outbox.spi.PerItemOutboxAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 건별 발송 트랜스포트의 Outbox 릴레이 공통 흐름.
 *
 * <p>claim → preloadTasks(N+1 회피) → {@code executor} 병렬 발송, 결과 4분기(success/deferred/permanent/
 * failure) → applyResults(success·failure 는 bulk, deferred 는 deferRetry, permanent 는
 * markFailedPermanently). 예외 안전: dispatch 단계만 try 로 좁혀 bulk 마킹 후 예외가 SENT 를 덮지 않게 한다.
 *
 * <p>이 클래스는 자동설정 빈이 아니다 — executor·defer 윈도가 소비자별이라 소비측이 인스턴스화한다.
 */
public class PerItemOutboxRelayTemplate {

    private static final Logger log = LoggerFactory.getLogger(PerItemOutboxRelayTemplate.class);
    private static final String METRIC_RELAY = "outbox.relay";
    private static final String FAILED_BULK_MESSAGE = "Dispatch failed";
    private static final String PERMANENT_FAILURE_MESSAGE = "Permanent dispatch failure";

    private final Duration maxDeferDuration;
    private final ExecutorService executor;
    /** nullable — null 이면 메트릭 no-op. */
    private final MeterRegistry meterRegistry;

    public PerItemOutboxRelayTemplate(
            Duration maxDeferDuration, ExecutorService executor, MeterRegistry meterRegistry) {
        this.maxDeferDuration = maxDeferDuration;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
    }

    public <O, T, P> SchedulerBatchProcessingResult relay(
            int batchSize, PerItemOutboxAdapter<O, T, P> adapter) {
        List<O> claimed = adapter.claimPendingMessages(batchSize);
        if (claimed == null || claimed.isEmpty()) {
            return SchedulerBatchProcessingResult.empty();
        }

        DispatchResults<O> results;
        try {
            List<String> taskIds = claimed.stream().map(adapter::taskId).distinct().toList();
            Map<String, T> taskById = adapter.preloadTasks(taskIds);
            results = dispatchAll(claimed, taskById, adapter);
        } catch (Exception e) {
            log.error(
                    "{} 건별 배치 발송 중 인프라 예외, PROCESSING → PENDING 무차감 복귀: count={}",
                    adapter.label(),
                    claimed.size(),
                    e);
            adapter.bulkReleaseToPending(claimed.stream().map(adapter::outboxId).toList());
            return SchedulerBatchProcessingResult.of(claimed.size(), 0, 0);
        }

        applyResults(results, adapter);
        recordRelayResults(adapter.pipeline(), results);
        return SchedulerBatchProcessingResult.of(
                claimed.size(),
                results.successes.size(),
                results.failures.size() + results.permanentFailures.size());
    }

    private <O, T, P> DispatchResults<O> dispatchAll(
            List<O> claimed, Map<String, T> taskById, PerItemOutboxAdapter<O, T, P> adapter) {
        DispatchResults<O> results = new DispatchResults<>();
        List<CompletableFuture<Void>> futures =
                claimed.stream()
                        .map(
                                outbox ->
                                        CompletableFuture.runAsync(
                                                () -> dispatchOne(outbox, taskById, adapter, results),
                                                executor))
                        .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return results;
    }

    private <O, T, P> void dispatchOne(
            O outbox,
            Map<String, T> taskById,
            PerItemOutboxAdapter<O, T, P> adapter,
            DispatchResults<O> results) {
        try {
            T task = taskById.get(adapter.taskId(outbox));
            if (task == null) {
                throw new IllegalStateException(
                        "Parent task not found: outboxId="
                                + adapter.outboxId(outbox)
                                + ", taskId="
                                + adapter.taskId(outbox));
            }
            P payload = adapter.buildPayload(outbox, task);
            adapter.notify(adapter.callbackUrl(outbox), payload, adapter.idempotencyKey(outbox));
            results.successes.add(outbox);
        } catch (OutboxDispatchDeferredException e) {
            log.warn(
                    "{} 일시 중단, 재시도 연기: outboxId={}, url={}",
                    adapter.label(),
                    adapter.outboxId(outbox),
                    adapter.callbackUrl(outbox));
            results.deferred.add(outbox);
        } catch (OutboxDispatchPermanentException e) {
            log.warn(
                    "{} 영구 실패: outboxId={}, url={}",
                    adapter.label(),
                    adapter.outboxId(outbox),
                    adapter.callbackUrl(outbox),
                    e);
            results.permanentFailures.add(outbox);
        } catch (Exception e) {
            log.error(
                    "{} 전송 실패: outboxId={}, url={}",
                    adapter.label(),
                    adapter.outboxId(outbox),
                    adapter.callbackUrl(outbox),
                    e);
            results.failures.add(outbox);
        }
    }

    private <O, T, P> void applyResults(
            DispatchResults<O> results, PerItemOutboxAdapter<O, T, P> adapter) {
        Instant now = Instant.now();

        if (!results.successes.isEmpty()) {
            adapter.bulkMarkSent(idsOf(results.successes, adapter), now);
        }
        if (!results.failures.isEmpty()) {
            adapter.bulkMarkFailed(idsOf(results.failures, adapter), now, FAILED_BULK_MESSAGE);
        }
        for (O outbox : results.deferred) {
            adapter.deferRetry(outbox, now, maxDeferDuration);
            if (adapter.outboxStatus(outbox) == OutboxStatus.FAILED) {
                log.error(
                        "{} defer 한도 초과 최종 실패: outboxId={}, url={}, 경과={}분",
                        adapter.label(),
                        adapter.outboxId(outbox),
                        adapter.callbackUrl(outbox),
                        Duration.between(adapter.createdAt(outbox), now).toMinutes());
            }
        }
        for (O outbox : results.permanentFailures) {
            adapter.markFailedPermanently(outbox, PERMANENT_FAILURE_MESSAGE, now);
        }
    }

    private void recordRelayResults(String pipeline, DispatchResults<?> results) {
        recordRelay(pipeline, "success", results.successes.size());
        recordRelay(pipeline, "deferred", results.deferred.size());
        recordRelay(pipeline, "permanent_failure", results.permanentFailures.size());
        recordRelay(pipeline, "failure", results.failures.size());
    }

    private void recordRelay(String pipeline, String result, int count) {
        if (meterRegistry == null || count == 0) {
            return;
        }
        try {
            Counter.builder(METRIC_RELAY)
                    .tag("pipeline", pipeline)
                    .tag("result", result)
                    .register(meterRegistry)
                    .increment(count);
        } catch (Exception e) {
            log.warn("relay 메트릭 기록 실패 (무시): {}", e.getMessage());
        }
    }

    private static <O> List<String> idsOf(
            Collection<O> outboxes, PerItemOutboxAdapter<O, ?, ?> adapter) {
        return outboxes.stream().map(adapter::outboxId).toList();
    }

    /** dispatch 결과 4분기 가변 컨테이너. 병렬 add 이므로 concurrent collection. */
    private static final class DispatchResults<O> {
        final ConcurrentLinkedQueue<O> successes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<O> failures = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<O> deferred = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<O> permanentFailures = new ConcurrentLinkedQueue<>();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*PerItemOutboxRelayTemplateTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplate.java platform-outbox/src/test/java/com/ryuqqq/platform/outbox/PerItemOutboxRelayTemplateTest.java
git commit -m "feat(outbox): PerItemOutboxRelayTemplate — executor 병렬 4분기 건별 릴레이

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 전체 빌드 + 재게이트(abstraction-critic) + 백로그

**Files:**
- Modify: `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/build-out-backlog.md`

- [ ] **Step 1: 모듈 전체 테스트**

Run: `./gradlew :platform-outbox:test :platform-common-domain:test`
Expected: PASS (outbox: 배치 12 + RetryPolicy 2 + PerItem 7 = 21; common-domain: OutboxStatus 1 추가).

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. archrules·기존 모듈 포함.

> 실패 시 systematic-debugging 으로 분석 후 수정 — 가정 금지.

- [ ] **Step 3: abstraction-critic 재게이트 (dogfood 루프 닫기)**

`abstraction-critic` 을 재실행해 outbox 추상화가 **CLEAN 수렴**하는지 확인한다. 오케스트레이터가 general-purpose 서브에이전트로 다음을 지시:

> 너는 abstraction-critic 으로 동작한다. `.claude/agents/abstraction-critic.md` + `.claude/references/conventions/shared-sdk-abstraction-review.md` 를 Read 하고, platform-outbox 공개 표면(spi/OutboxStore·BatchOutboxAdapter·PerItemOutboxAdapter, BatchOutboxRelayTemplate·PerItemOutboxRelayTemplate, dto/*, OutboxRetryPolicy, exception/*, config/*)을 6축으로 채점하라. 직전 dogfood 의 N1(Queue 어휘)·S1(저장소+트랜스포트 융합)이 해소됐는지 특히 확인. commonality 보조: 여전히 FF 단독 소비자. JSON findings 출력.

Expected: N1 finding 사라짐(neutral 네이밍), S1 finding 사라지거나 info(공유 OutboxStore + 트랜스포트별 분리). verdict CLEAN 또는 잔여 minor만. major 재출현 시 systematic-debugging.

- [ ] **Step 4: 백로그 갱신**

`build-out-backlog.md` 에서 outbox 항목 2단계 완료를 반영:

기존 1단계 줄 끝에 추가:
```markdown
- **P1** `platform-outbox` — ✅ **2단계 완료** (`feat/outbox-relay-redesign`). 트랜스포트 중립 개명(Queue→Batch, N1 해소) + 공유 `OutboxStore<O>` base + `BatchOutboxRelayTemplate`·`PerItemOutboxRelayTemplate`(executor 4분기) + `OutboxStatus`(→common-domain)·`OutboxRetryPolicy`·dispatch 예외. abstraction-critic 재게이트로 CLEAN 수렴 확인.
```

변경 이력 추가:
```markdown
- 2026-06-08: P1 `platform-outbox` 2단계 완료. abstraction-critic dogfood 가 잡은 N1(트랜스포트 어휘)·S1(seam)을 트랜스포트 중립 개명 + 공유 OutboxStore + Batch/PerItem 2 템플릿으로 해소. 설계 게이트(abstraction-critic) 첫 실전 적용 사례.
```

- [ ] **Step 5: 백로그 커밋 (vault repo)**

```bash
cd /Users/ryu-qqq/Documents/ryu-qqq-wiki && git add wiki/projects/spring-platform-commons/build-out-backlog.md && git commit -m "docs(platform): platform-outbox 2단계(트랜스포트 중립 relay) 완료 반영"
```

- [ ] **Step 6: 완료 보고**

work-evaluator 4축 self-check 후 완료 보고. 재게이트 결과(CLEAN 수렴 여부)를 명시.

---

## Self-Review (작성자 점검 결과)

- **Spec coverage:** §3.1 공유코어→Task1·3 + Task2(OutboxStore), §3.2 배치→Task2, §3.3 건별→Task4·5, §4 와이어링→Task2(autoconfig), §5 테스트→Task1~5, §6 마이그레이션→Task2(구파일 삭제), §7 재게이트→Task6 Step3. 전 항목 매핑됨.
- **Placeholder scan:** TBD/TODO 없음. 모든 코드 스텝 완전. 코드펜스 언어 태그 부여.
- **Type consistency:** `OutboxStore<O>`(label·pipeline·outboxId·claimPendingMessages·bulkMarkSent·bulkMarkFailed·bulkReleaseToPending) ← BatchOutboxAdapter(+businessId·idempotencyKey·dispatchBatch)·PerItemOutboxAdapter(+taskId·outboxStatus·callbackUrl·createdAt·idempotencyKey·preloadTasks·buildPayload·notify·deferRetry(O,Instant,Duration)·markFailedPermanently). 템플릿·테스트가 동일 시그니처 사용. `OutboxBatchDispatchResult`·`OutboxDispatchCommand`·`OutboxRetryPolicy.defaults/of`·`OutboxStatus`·예외 2종 일관. `deferRetry`의 `Duration maxDeferDuration` 파라미터를 SPI·템플릿·테스트가 일치(fake 의 deferDurationSeen 검증).
- **메트릭:** 두 템플릿 공통 `outbox.relay{pipeline,result}`, 건별은 deferred·permanent_failure 추가 — 테스트로 검증.
