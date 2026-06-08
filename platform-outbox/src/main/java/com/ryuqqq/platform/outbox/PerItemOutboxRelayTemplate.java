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
