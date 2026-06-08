package com.ryuqqq.platform.outbox;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.dto.OutboxBatchSendResult;
import com.ryuqqq.platform.outbox.dto.OutboxEnqueueCommand;
import com.ryuqqq.platform.outbox.spi.QueueOutboxAdapter;
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
        if (claimed == null || claimed.isEmpty()) {
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

        Set<String> successBusinessIds = new HashSet<>(sendResult.successIds());
        List<String> successOutboxIds =
                claimed.stream()
                        .filter(o -> successBusinessIds.contains(adapter.businessId(o)))
                        .map(adapter::outboxId)
                        .toList();
        adapter.bulkMarkSent(successOutboxIds, now);

        Set<String> failedBusinessIds =
                sendResult.failedEntries().stream()
                        .map(OutboxBatchSendResult.FailedEntry::id)
                        .collect(Collectors.toSet());
        List<String> failedOutboxIds =
                claimed.stream()
                        .filter(o -> failedBusinessIds.contains(adapter.businessId(o)))
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
