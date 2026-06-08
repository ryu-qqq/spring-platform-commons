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
