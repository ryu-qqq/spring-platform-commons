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
        boolean preloadReturnsNull = false;

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
            if (preloadReturnsNull) return null;
            if (preloadThrows) throw new RuntimeException("DB down");
            Map<String, String> m = new HashMap<>();
            for (String id : taskIds) if (tasks.containsKey(id)) m.put(id, tasks.get(id));
            return m;
        }

        @Override public String buildPayload(TestOutbox o, String task) { return "payload:" + task; }

        @Override
        public void notify(String url, String payload, String idempotencyKey) {
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
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(4, 1, 2));
    }

    @Test
    @DisplayName("부모 작업 preload 누락 항목은 failure 로 분기")
    void taskNotFoundIsFailure() {
        FakeAdapter a = new FakeAdapter();
        TestOutbox o = new TestOutbox("o1", "t1");
        o.outcome = "success";
        a.toClaim.add(o); // tasks 에 t1 안 넣음

        template.relay(10, a);

        assertThat(a.markedFailed).containsExactly("o1");
        assertThat(a.markedSent).isEmpty();
    }

    @Test
    @DisplayName("deferRetry 후 OutboxStatus.FAILED 면 최종 실패 로깅 경로(예외 없이 통과)")
    void deferThenFailedDetected() {
        FakeAdapter a = new FakeAdapter();
        TestOutbox o = claim(a, "o1", "t1", "deferred");
        o.status = OutboxStatus.FAILED;

        SchedulerBatchProcessingResult r = template.relay(10, a);

        assertThat(a.deferred).containsExactly("o1");
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(1, 0, 0));
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
    @DisplayName("preloadTasks 가 null 반환 → 건별 오전이 대신 전체 bulkReleaseToPending, 결과 (n,0,0)")
    void preloadReturnsNull() {
        FakeAdapter a = new FakeAdapter();
        claim(a, "o1", "t1", "success");
        claim(a, "o2", "t2", "success");
        a.preloadReturnsNull = true;

        SchedulerBatchProcessingResult r = template.relay(10, a);

        assertThat(a.released).containsExactlyInAnyOrder("o1", "o2");
        assertThat(a.markedSent).isEmpty();
        assertThat(a.markedFailed).isEmpty();
        assertThat(r).isEqualTo(SchedulerBatchProcessingResult.of(2, 0, 0));
    }

    @Test
    @DisplayName("생성자: maxDeferDuration·executor null 이면 즉시 예외(fail-fast)")
    void constructorRejectsNullDeps() {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> new PerItemOutboxRelayTemplate(null, ex, registry))
                    .isInstanceOf(NullPointerException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    new PerItemOutboxRelayTemplate(
                                            Duration.ofHours(6), null, registry))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            ex.shutdownNow();
        }
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
