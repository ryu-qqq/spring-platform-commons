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
