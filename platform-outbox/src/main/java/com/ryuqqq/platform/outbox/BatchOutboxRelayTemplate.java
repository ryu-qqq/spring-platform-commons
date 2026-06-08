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
