# platform-outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FileFlow 의 generic Queue Outbox relay 추상화를 신규 모듈 `platform-outbox` 로 승격한다 (relay 템플릿 + `QueueOutboxAdapter<O>` SPI + DTO 2종 + zero-config 자동설정). Queue-only 1단계.

**Architecture:** 신규 멀티모듈 `platform-outbox` (패키지 `com.ryuqqq.platform.outbox`), application 레이어 오케스트레이션. `QueueOutboxRelayTemplate.relay(batchSize, adapter)` 가 claim→enqueue→bulkMark 흐름을 캡슐화하고, 도메인 의존부는 `QueueOutboxAdapter<O>` SPI 로 위임. 반환형은 기존 `platform-common-application` 의 `SchedulerBatchProcessingResult` 재사용. 메트릭은 Micrometer `MeterRegistry`(optional)로 `outbox.relay` 카운터 기록.

**Tech Stack:** Java 21, Spring Boot 3.5.6 autoconfigure, Micrometer 1.14.3, JUnit5 + AssertJ + Mockito, Gradle 멀티모듈(version catalog).

**Spec:** `docs/superpowers/specs/2026-06-08-platform-outbox-design.md`

---

## File Structure

```text
platform-outbox/
  build.gradle                                                    # SDK 모듈 빌드 (Task 1)
  src/main/java/com/ryuqqq/platform/outbox/
    package-info.java                                             # 모듈 설명 (Task 1)
    dto/OutboxEnqueueCommand.java                                 # businessId+idempotencyKey (Task 2)
    dto/OutboxBatchSendResult.java                               # 배치 발행 결과 (Task 2)
    spi/QueueOutboxAdapter.java                                   # 도메인 의존부 SPI (Task 3)
    QueueOutboxRelayTemplate.java                                 # relay 흐름 (Task 4)
    config/PlatformOutboxAutoConfiguration.java                  # @AutoConfiguration (Task 5)
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports   # (Task 5)
  src/test/java/com/ryuqqq/platform/outbox/
    dto/OutboxBatchSendResultTest.java                           # (Task 2)
    QueueOutboxRelayTemplateTest.java                            # (Task 4)
    config/PlatformOutboxAutoConfigurationTest.java              # (Task 5)
settings.gradle                                                   # include 추가 (Task 1)
```

루트 `build.gradle` 의 `sdkProjects`(`:platform-*`)가 java-library·maven-publish·sources/javadoc 를 자동 적용하므로 모듈 `build.gradle` 에 plugin 선언 불필요 (platform-scheduler 와 동일).

---

## Task 1: 모듈 스캐폴드

**Files:**
- Modify: `settings.gradle`
- Create: `platform-outbox/build.gradle`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/package-info.java`

- [ ] **Step 1: settings.gradle 에 모듈 등록**

`include 'platform-security'` 줄 바로 아래에 추가:

```groovy
include 'platform-outbox'
```

그리고 `project(':platform-security').projectDir = file('platform-security')` 줄 바로 아래에 추가:

```groovy
project(':platform-outbox').projectDir = file('platform-outbox')
```

- [ ] **Step 2: build.gradle 작성**

Create `platform-outbox/build.gradle`:

```groovy
// Application 레이어 Outbox SDK — generic Queue relay 템플릿 + QueueOutboxAdapter SPI + DTO.
// persistence·SQS 구현은 소비측이 SPI 로 제공. Callback relay 는 2단계로 deferred.

publishing {
    publications {
        maven(MavenPublication) {
            pom {
                name = 'Platform Outbox'
                description = 'Application 레이어 Outbox SDK — generic Queue relay 템플릿 + QueueOutboxAdapter SPI + 자동설정.'
            }
        }
    }
}

dependencies {
    api project(':platform-common-application')

    implementation platform(libs.spring.boot.dependencies)
    implementation libs.spring.boot.autoconfigure
    implementation libs.micrometer.core
    implementation libs.slf4j.api

    testImplementation platform(libs.junit.bom)
    testImplementation libs.bundles.testing
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

> 비고: `api project(':platform-common-application')` — relay 반환형 `SchedulerBatchProcessingResult`
> 가 공개 API 표면에 노출되므로 api. persistence/SQS/web 의존성은 없다 (소비측이 SPI 로 제공).

- [ ] **Step 3: package-info.java 작성**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/package-info.java`:

```java
/**
 * Platform Outbox — generic Queue Outbox relay 공통 모듈 (application 레이어).
 *
 * <p>{@link com.ryuqqq.platform.outbox.QueueOutboxRelayTemplate} 이 claim → enqueue → bulkMark
 * 흐름을 캡슐화하고, 도메인 의존부(타입·ID 추출·발행·마킹)는 {@link
 * com.ryuqqq.platform.outbox.spi.QueueOutboxAdapter} SPI 로 위임한다. 소비측이 SPI 를 구현하면 여러
 * 도메인의 릴레이 흐름이 한 곳으로 수렴한다.
 *
 * <p>Callback Outbox relay 와 {@code OutboxStatus}·{@code OutboxRetryPolicy} 는 2단계로 분리되어 있다.
 */
package com.ryuqqq.platform.outbox;
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :platform-outbox:compileJava`
Expected: `BUILD SUCCESSFUL` (package-info 만 있어 통과). settings.gradle 인식 검증.

- [ ] **Step 5: 커밋**

```bash
git add settings.gradle platform-outbox/build.gradle platform-outbox/src/main/java/com/ryuqqq/platform/outbox/package-info.java
git commit -m "feat(outbox): platform-outbox 모듈 스캐폴드 (build-out P1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: DTO — OutboxEnqueueCommand · OutboxBatchSendResult

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxEnqueueCommand.java`
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxBatchSendResult.java`
- Test: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/dto/OutboxBatchSendResultTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/dto/OutboxBatchSendResultTest.java`:

```java
package com.ryuqqq.platform.outbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxBatchSendResultTest {

    @Test
    @DisplayName("allSuccess: 실패 없음, hasFailures=false")
    void allSuccess() {
        OutboxBatchSendResult result = OutboxBatchSendResult.allSuccess(List.of("a", "b"));

        assertThat(result.successIds()).containsExactly("a", "b");
        assertThat(result.failedEntries()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("of: 성공/실패 혼재, hasFailures=true")
    void ofWithFailures() {
        OutboxBatchSendResult result =
                OutboxBatchSendResult.of(
                        List.of("a"),
                        List.of(new OutboxBatchSendResult.FailedEntry("b", "timeout")));

        assertThat(result.successIds()).containsExactly("a");
        assertThat(result.failedEntries()).hasSize(1);
        assertThat(result.failedEntries().get(0).id()).isEqualTo("b");
        assertThat(result.failedEntries().get(0).errorMessage()).isEqualTo("timeout");
        assertThat(result.hasFailures()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:test --tests '*OutboxBatchSendResultTest'`
Expected: FAIL — `OutboxBatchSendResult` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: OutboxEnqueueCommand 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxEnqueueCommand.java`:

```java
package com.ryuqqq.platform.outbox.dto;

/** Queue outbox 릴레이 시 발행에 필요한 business id + 멱등키 쌍. */
public record OutboxEnqueueCommand(String businessId, String idempotencyKey) {}
```

- [ ] **Step 4: OutboxBatchSendResult 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/OutboxBatchSendResult.java`:

```java
package com.ryuqqq.platform.outbox.dto;

import java.util.List;

/** 아웃박스 배치 발행 결과 — 건별 성공/실패를 추적한다. */
public record OutboxBatchSendResult(List<String> successIds, List<FailedEntry> failedEntries) {

    /** 발행 실패 항목 — business id 와 에러 메시지. */
    public record FailedEntry(String id, String errorMessage) {}

    public static OutboxBatchSendResult allSuccess(List<String> ids) {
        return new OutboxBatchSendResult(ids, List.of());
    }

    public static OutboxBatchSendResult of(
            List<String> successIds, List<FailedEntry> failedEntries) {
        return new OutboxBatchSendResult(successIds, failedEntries);
    }

    public boolean hasFailures() {
        return !failedEntries.isEmpty();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*OutboxBatchSendResultTest'`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/dto/ platform-outbox/src/test/java/com/ryuqqq/platform/outbox/dto/
git commit -m "feat(outbox): OutboxEnqueueCommand·OutboxBatchSendResult DTO 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: SPI — QueueOutboxAdapter

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/QueueOutboxAdapter.java`

> SPI 인터페이스는 별도 단위 테스트가 없다 — Task 4 의 relay 템플릿 테스트가 fake 구현체로 계약을 행사한다.

- [ ] **Step 1: 인터페이스 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/QueueOutboxAdapter.java`:

```java
package com.ryuqqq.platform.outbox.spi;

import com.ryuqqq.platform.outbox.dto.OutboxBatchSendResult;
import com.ryuqqq.platform.outbox.dto.OutboxEnqueueCommand;
import java.time.Instant;
import java.util.List;

/**
 * Queue Outbox 릴레이의 도메인 의존부를 추상화한 어댑터 (소비측 구현).
 *
 * <p>{@link com.ryuqqq.platform.outbox.QueueOutboxRelayTemplate} 이 호출하는 도메인 의존 메서드를
 * 노출한다. 여러 도메인의 Queue 서비스가 이 인터페이스를 구현하면 그 위의 흐름(claim → enqueue →
 * bulkMark)이 한 곳으로 수렴한다.
 *
 * @param <O> 도메인 Outbox 타입
 */
public interface QueueOutboxAdapter<O> {

    /** 로그 라벨 (예: "다운로드", "변환"). */
    String label();

    /** 메트릭 태그용 파이프라인 식별자 — 저카디널리티. */
    String pipeline();

    /** outbox 자체의 ID — bulkMark 대상. */
    String outboxId(O outbox);

    /** outbox 가 참조하는 비즈니스 ID — 발행 메시지 본문이자 sendResult 매칭 키. */
    String businessId(O outbox);

    /** 릴레이·발행 메시지·콜백 헤더에 전달할 멱등키. */
    String idempotencyKey(O outbox);

    /** PENDING → PROCESSING 원자 claim 후 도메인 객체 목록 반환. */
    List<O> claimPendingMessages(int batchSize);

    /** business id·멱등키 배치를 발행하고 성공·실패 분리 결과를 반환. */
    OutboxBatchSendResult enqueueBatch(List<OutboxEnqueueCommand> commands);

    /** 성공한 outbox 들을 PROCESSING → SENT 로 일괄 마킹. */
    void bulkMarkSent(List<String> outboxIds, Instant now);

    /** 실패한 outbox 들을 일괄 마킹 (재시도 증가 또는 dead-letter 는 구현체 정책). */
    void bulkMarkFailed(List<String> outboxIds, Instant now, String errorMessage);

    /** enqueueBatch 인프라 예외 시 PROCESSING → PENDING 무차감 복귀 (retry_count 유지). */
    void bulkReleaseToPending(List<String> outboxIds);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :platform-outbox:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/spi/QueueOutboxAdapter.java
git commit -m "feat(outbox): QueueOutboxAdapter SPI 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: QueueOutboxRelayTemplate

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplate.java`
- Test: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplateTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplateTest.java`:

```java
package com.ryuqqq.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.dto.OutboxBatchSendResult;
import com.ryuqqq.platform.outbox.dto.OutboxEnqueueCommand;
import com.ryuqqq.platform.outbox.spi.QueueOutboxAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueueOutboxRelayTemplateTest {

    /** 테스트용 도메인 outbox. */
    record TestOutbox(String outboxId, String businessId, String idempotencyKey) {}

    /** 호출을 기록하는 fake SPI 구현체. */
    static class FakeQueueOutboxAdapter implements QueueOutboxAdapter<TestOutbox> {
        List<TestOutbox> toClaim = new ArrayList<>();
        OutboxBatchSendResult enqueueResult;
        RuntimeException enqueueException;

        List<OutboxEnqueueCommand> enqueued;
        final List<String> markedSent = new ArrayList<>();
        final List<String> markedFailed = new ArrayList<>();
        String failedErrorMessage;
        final List<String> released = new ArrayList<>();

        @Override public String label() { return "테스트"; }
        @Override public String pipeline() { return "test"; }
        @Override public String outboxId(TestOutbox o) { return o.outboxId(); }
        @Override public String businessId(TestOutbox o) { return o.businessId(); }
        @Override public String idempotencyKey(TestOutbox o) { return o.idempotencyKey(); }
        @Override public List<TestOutbox> claimPendingMessages(int batchSize) { return toClaim; }

        @Override
        public OutboxBatchSendResult enqueueBatch(List<OutboxEnqueueCommand> commands) {
            this.enqueued = commands;
            if (enqueueException != null) throw enqueueException;
            return enqueueResult;
        }

        @Override public void bulkMarkSent(List<String> ids, Instant now) { markedSent.addAll(ids); }
        @Override public void bulkMarkFailed(List<String> ids, Instant now, String msg) {
            markedFailed.addAll(ids);
            failedErrorMessage = msg;
        }
        @Override public void bulkReleaseToPending(List<String> ids) { released.addAll(ids); }
    }

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final QueueOutboxRelayTemplate template = new QueueOutboxRelayTemplate(registry);

    @Test
    @DisplayName("claim 이 비면 empty() 반환, enqueue 미호출")
    void emptyClaim() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();

        SchedulerBatchProcessingResult result = template.relay(10, adapter);

        assertThat(result).isEqualTo(SchedulerBatchProcessingResult.empty());
        assertThat(adapter.enqueued).isNull();
        assertThat(adapter.markedSent).isEmpty();
    }

    @Test
    @DisplayName("전건 성공 → bulkMarkSent 전체, markFailed 미호출, 결과 (n,n,0)")
    void allSuccess() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(
                new TestOutbox("o1", "b1", "k1"), new TestOutbox("o2", "b2", "k2"));
        adapter.enqueueResult = OutboxBatchSendResult.allSuccess(List.of("b1", "b2"));

        SchedulerBatchProcessingResult result = template.relay(10, adapter);

        assertThat(adapter.markedSent).containsExactly("o1", "o2");
        assertThat(adapter.markedFailed).isEmpty();
        assertThat(result).isEqualTo(SchedulerBatchProcessingResult.of(2, 2, 0));
        assertThat(adapter.enqueued)
                .containsExactly(
                        new OutboxEnqueueCommand("b1", "k1"),
                        new OutboxEnqueueCommand("b2", "k2"));
    }

    @Test
    @DisplayName("부분 실패 → success markSent, failed markFailed(errorSummary), 결과 (n,s,f)")
    void partialFailure() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(
                new TestOutbox("o1", "b1", "k1"), new TestOutbox("o2", "b2", "k2"));
        adapter.enqueueResult =
                OutboxBatchSendResult.of(
                        List.of("b1"),
                        List.of(new OutboxBatchSendResult.FailedEntry("b2", "timeout")));

        SchedulerBatchProcessingResult result = template.relay(10, adapter);

        assertThat(adapter.markedSent).containsExactly("o1");
        assertThat(adapter.markedFailed).containsExactly("o2");
        assertThat(adapter.failedErrorMessage).isEqualTo("timeout");
        assertThat(result).isEqualTo(SchedulerBatchProcessingResult.of(2, 1, 1));
    }

    @Test
    @DisplayName("enqueueBatch 예외 → bulkReleaseToPending 전체, markSent/Failed 미호출, 결과 (n,0,0)")
    void enqueueInfraException() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(
                new TestOutbox("o1", "b1", "k1"), new TestOutbox("o2", "b2", "k2"));
        adapter.enqueueException = new RuntimeException("SQS down");

        SchedulerBatchProcessingResult result = template.relay(10, adapter);

        assertThat(adapter.released).containsExactly("o1", "o2");
        assertThat(adapter.markedSent).isEmpty();
        assertThat(adapter.markedFailed).isEmpty();
        assertThat(result).isEqualTo(SchedulerBatchProcessingResult.of(2, 0, 0));
    }

    @Test
    @DisplayName("errorSummary 는 중복 제거 후 최대 3건을 \"; \" 로 합친다")
    void errorSummaryDedupAndLimit() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(
                new TestOutbox("o1", "b1", "k1"),
                new TestOutbox("o2", "b2", "k2"),
                new TestOutbox("o3", "b3", "k3"),
                new TestOutbox("o4", "b4", "k4"),
                new TestOutbox("o5", "b5", "k5"));
        adapter.enqueueResult =
                OutboxBatchSendResult.of(
                        List.of(),
                        List.of(
                                new OutboxBatchSendResult.FailedEntry("b1", "dup"),
                                new OutboxBatchSendResult.FailedEntry("b2", "dup"),
                                new OutboxBatchSendResult.FailedEntry("b3", "e3"),
                                new OutboxBatchSendResult.FailedEntry("b4", "e4"),
                                new OutboxBatchSendResult.FailedEntry("b5", "e5")));

        template.relay(10, adapter);

        // distinct(dup, e3, e4, e5) → limit 3 → "dup; e3; e4"
        assertThat(adapter.failedErrorMessage).isEqualTo("dup; e3; e4");
    }

    @Test
    @DisplayName("outbox.relay 카운터에 success/failure 가 기록된다")
    void recordsMetrics() {
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(
                new TestOutbox("o1", "b1", "k1"), new TestOutbox("o2", "b2", "k2"));
        adapter.enqueueResult =
                OutboxBatchSendResult.of(
                        List.of("b1"),
                        List.of(new OutboxBatchSendResult.FailedEntry("b2", "timeout")));

        template.relay(10, adapter);

        assertThat(
                        registry.get("outbox.relay")
                                .tag("pipeline", "test")
                                .tag("result", "success")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.get("outbox.relay")
                                .tag("pipeline", "test")
                                .tag("result", "failure")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("MeterRegistry 가 null 이어도 NPE 없이 동작한다")
    void nullMeterRegistry() {
        QueueOutboxRelayTemplate noMetricTemplate = new QueueOutboxRelayTemplate(null);
        FakeQueueOutboxAdapter adapter = new FakeQueueOutboxAdapter();
        adapter.toClaim = List.of(new TestOutbox("o1", "b1", "k1"));
        adapter.enqueueResult = OutboxBatchSendResult.allSuccess(List.of("b1"));

        SchedulerBatchProcessingResult result = noMetricTemplate.relay(10, adapter);

        assertThat(result).isEqualTo(SchedulerBatchProcessingResult.of(1, 1, 0));
        assertThat(adapter.markedSent).containsExactly("o1");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:test --tests '*QueueOutboxRelayTemplateTest'`
Expected: FAIL — `QueueOutboxRelayTemplate` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplate.java`:

```java
package com.ryuqqq.platform.outbox;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.dto.OutboxBatchSendResult;
import com.ryuqqq.platform.outbox.dto.OutboxEnqueueCommand;
import com.ryuqqq.platform.outbox.spi.QueueOutboxAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queue Outbox 릴레이의 공통 흐름.
 *
 * <p>처리 순서: ① {@link QueueOutboxAdapter#claimPendingMessages(int)} 로 PENDING → PROCESSING 원자
 * claim → ② {@link QueueOutboxAdapter#enqueueBatch(List)} 로 일괄 발행 → ③ 성공·실패 분리 후
 * bulkMarkSent / bulkMarkFailed.
 *
 * <p>예외 안전: try 는 {@code enqueueBatch} 만 감싼다 — bulk 마킹은 try 밖에서 실행되어 markSent 예외가
 * claim 전체를 markFailed 로 덮어쓰지 않는다. enqueueBatch 인프라 예외는 retry_count 를 소모하지 않고
 * {@link QueueOutboxAdapter#bulkReleaseToPending(List)} 로 PENDING 복귀한다.
 *
 * <p>도메인 의존 부분은 {@link QueueOutboxAdapter} 로 위임 — 여러 도메인 서비스가 같은 흐름을 복붙하지 않게 한다.
 */
public class QueueOutboxRelayTemplate {

    private static final Logger log = LoggerFactory.getLogger(QueueOutboxRelayTemplate.class);

    /** errorSummary 빌드 시 최대 에러 메시지 수 (DB 컬럼 길이 보호). */
    private static final int MAX_ERROR_MESSAGES_IN_SUMMARY = 3;

    /** relay 계측 — outbox_relay_total{pipeline,result}. */
    private static final String METRIC_RELAY = "outbox.relay";

    /** nullable — null 이면 메트릭 no-op. */
    private final MeterRegistry meterRegistry;

    public QueueOutboxRelayTemplate(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <O> SchedulerBatchProcessingResult relay(int batchSize, QueueOutboxAdapter<O> adapter) {
        List<O> claimed = adapter.claimPendingMessages(batchSize);
        if (claimed.isEmpty()) {
            return SchedulerBatchProcessingResult.empty();
        }

        List<String> claimedOutboxIds = claimed.stream().map(adapter::outboxId).toList();

        OutboxBatchSendResult sendResult;
        try {
            List<OutboxEnqueueCommand> commands =
                    claimed.stream()
                            .map(
                                    o ->
                                            new OutboxEnqueueCommand(
                                                    adapter.businessId(o), adapter.idempotencyKey(o)))
                            .toList();
            sendResult = adapter.enqueueBatch(commands);
        } catch (Exception e) {
            log.error(
                    "{} 큐 배치 발행 중 인프라 예외, PROCESSING → PENDING 무차감 복귀: count={}",
                    adapter.label(),
                    claimedOutboxIds.size(),
                    e);
            adapter.bulkReleaseToPending(claimedOutboxIds);
            return SchedulerBatchProcessingResult.of(claimed.size(), 0, 0);
        }

        return applySendResults(claimed, sendResult, adapter);
    }

    private <O> SchedulerBatchProcessingResult applySendResults(
            List<O> claimed, OutboxBatchSendResult sendResult, QueueOutboxAdapter<O> adapter) {
        Instant now = Instant.now();

        List<String> successOutboxIds =
                claimed.stream()
                        .filter(o -> sendResult.successIds().contains(adapter.businessId(o)))
                        .map(adapter::outboxId)
                        .toList();
        adapter.bulkMarkSent(successOutboxIds, now);

        List<String> failedOutboxIds =
                claimed.stream()
                        .filter(
                                o ->
                                        sendResult.failedEntries().stream()
                                                .anyMatch(f -> f.id().equals(adapter.businessId(o))))
                        .map(adapter::outboxId)
                        .toList();

        if (sendResult.hasFailures()) {
            String errorSummary = summarizeErrors(sendResult);
            adapter.bulkMarkFailed(failedOutboxIds, now, errorSummary);
            log.warn(
                    "{} 큐 배치 발행 부분 실패: total={}, success={}, failed={}, error={}",
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

    private static String summarizeErrors(OutboxBatchSendResult sendResult) {
        return sendResult.failedEntries().stream()
                .map(OutboxBatchSendResult.FailedEntry::errorMessage)
                .distinct()
                .limit(MAX_ERROR_MESSAGES_IN_SUMMARY)
                .collect(Collectors.joining("; "));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*QueueOutboxRelayTemplateTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplate.java platform-outbox/src/test/java/com/ryuqqq/platform/outbox/QueueOutboxRelayTemplateTest.java
git commit -m "feat(outbox): QueueOutboxRelayTemplate — claim→enqueue→bulkMark 흐름 + 예외 안전

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: PlatformOutboxAutoConfiguration

**Files:**
- Create: `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java`
- Create: `platform-outbox/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfigurationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-outbox/src/test/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfigurationTest.java`:

```java
package com.ryuqqq.platform.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.outbox.QueueOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformOutboxAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PlatformOutboxAutoConfiguration.class));

    @Test
    @DisplayName("MeterRegistry 없이도 relay 템플릿 빈이 등록된다")
    void registersWithoutMeterRegistry() {
        runner.run(context ->
                assertThat(context).hasSingleBean(QueueOutboxRelayTemplate.class));
    }

    @Test
    @DisplayName("MeterRegistry 가 있으면 함께 주입되어 등록된다")
    void registersWithMeterRegistry() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context ->
                        assertThat(context).hasSingleBean(QueueOutboxRelayTemplate.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnTemplate() {
        runner.withBean(
                        "customTemplate",
                        QueueOutboxRelayTemplate.class,
                        () -> new QueueOutboxRelayTemplate(null))
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueOutboxRelayTemplate.class);
                    assertThat(context.getBeanNamesForType(QueueOutboxRelayTemplate.class))
                            .containsExactly("customTemplate");
                });
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-outbox:test --tests '*PlatformOutboxAutoConfigurationTest'`
Expected: FAIL — `PlatformOutboxAutoConfiguration` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 자동설정 구현**

Create `platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java`:

```java
package com.ryuqqq.platform.outbox.config;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.QueueOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Outbox 자동 설정 — {@link QueueOutboxRelayTemplate} 빈을 등록한다.
 *
 * <p>{@link MeterRegistry} 는 optional — 소비측(actuator/micrometer)이 제공하면 주입되고, 없으면 null
 * 로 메트릭 no-op 한다. 소비측이 동일 타입 빈을 정의하면 {@link ConditionalOnMissingBean} 으로 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(SchedulerBatchProcessingResult.class)
public class PlatformOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueueOutboxRelayTemplate queueOutboxRelayTemplate(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new QueueOutboxRelayTemplate(meterRegistryProvider.getIfAvailable());
    }
}
```

- [ ] **Step 4: imports 파일 작성**

Create `platform-outbox/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```text
com.ryuqqq.platform.outbox.config.PlatformOutboxAutoConfiguration
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :platform-outbox:test --tests '*PlatformOutboxAutoConfigurationTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add platform-outbox/src/main/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfiguration.java platform-outbox/src/main/resources/META-INF/spring/ platform-outbox/src/test/java/com/ryuqqq/platform/outbox/config/PlatformOutboxAutoConfigurationTest.java
git commit -m "feat(outbox): PlatformOutboxAutoConfiguration zero-config 빈 등록 (MeterRegistry optional)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 전체 빌드 검증 + 백로그 갱신

**Files:**
- Modify: `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/build-out-backlog.md`

- [ ] **Step 1: 모듈 전체 테스트**

Run: `./gradlew :platform-outbox:test`
Expected: PASS (DTO 2 + relay 7 + autoconfig 3 = 12).

- [ ] **Step 2: 전체 빌드 (archrules·기존 모듈 포함)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. platform-archrules 및 기존 모든 모듈 테스트 통과.

> 실패 시 systematic-debugging 으로 원인 분석 후 수정 — 가정으로 넘어가지 말 것.

- [ ] **Step 3: 백로그 P1 항목 갱신**

`build-out-backlog.md` 우선순위 섹션에서 `platform-outbox` 줄을 갱신:

기존:
```markdown
- **P1** `platform-outbox` (relay template + adapter SPI; FileFlow Template 참조) ← **다음**
```

변경:
```markdown
- **P1** `platform-outbox` — ✅ **1단계 완료** (`feat/platform-outbox`). FF 단독 generic Queue relay 추상화를 platform 으로 승격: `QueueOutboxRelayTemplate`(claim→enqueue→bulkMark, 예외 안전)+`QueueOutboxAdapter<O>` SPI+DTO 2종, `SchedulerBatchProcessingResult` 재사용, MeterRegistry optional zero-config. **2단계(Callback relay·OutboxStatus·OutboxRetryPolicy)는 별도.** 다음=P2 또는 outbox 2단계.
```

변경 이력에 한 줄 추가:
```markdown
- 2026-06-08: P1 `platform-outbox` 1단계(Queue relay) 완료. generic 템플릿은 FF 단독 자산이라 "수렴"이 아닌 "승격"으로 설계, Queue-only 최소 범위. Callback relay 는 2단계로 분리.
```

- [ ] **Step 4: 백로그 커밋 (vault repo)**

```bash
cd /Users/ryu-qqq/Documents/ryu-qqq-wiki && git add wiki/projects/spring-platform-commons/build-out-backlog.md && git commit -m "docs(platform): platform-outbox 1단계(Queue relay) 완료 반영"
```

> vault 는 별도 repo 이므로 commit 위치 주의.

- [ ] **Step 5: 완료 보고**

`work-evaluator` 4축(가정금지·최소·범위·검증) self-check 후 완료 보고. Callback relay 2단계·서버 입양은 후속임을 명시.

---

## Self-Review (작성자 점검 결과)

- **Spec coverage:** §3 모듈배치→Task1, §4.1·4.2 DTO→Task2, §4.3 SPI→Task3, §4.4 relay 템플릿→Task4, §4.5 자동설정→Task5, §5 테스트(단위·DTO·슬라이스)→Task4/2/5, §6 검증기준→Task6. 전 항목 매핑됨.
- **Placeholder scan:** TBD/TODO 없음. 모든 코드 스텝은 완전한 소스 포함. 모든 코드 펜스에 언어 태그 부여(text/groovy/java).
- **Type consistency:** `OutboxBatchSendResult.FailedEntry(id, errorMessage)`·`successIds()`·`failedEntries()`·`hasFailures()`·`allSuccess()`·`of()`, `OutboxEnqueueCommand(businessId, idempotencyKey)`, `QueueOutboxAdapter` 메서드명, `SchedulerBatchProcessingResult.of/empty`, `QueueOutboxRelayTemplate(MeterRegistry)`·`relay(int, adapter)` 가 SPI·템플릿·테스트·자동설정에서 일관. 메트릭 이름 `outbox.relay`·태그 `pipeline`/`result` 일치.
- **의존성:** production 은 platform-common-application(api)+micrometer-core+autoconfigure+slf4j 만. persistence/SQS/web 무관 — 범위 준수.
