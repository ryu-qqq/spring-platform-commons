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

    /**
     * business id·멱등키 배치를 발행하고 성공·실패 분리 결과를 반환.
     *
     * <p><b>계약:</b> 입력 command 의 모든 business id 는 결과의 {@code successIds} 또는
     * {@code failedEntries} 중 정확히 한 곳에 포함되어야 한다. 어느 쪽에도 없는 항목은 PROCESSING 상태로
     * 남아 stuck 될 수 있다(템플릿이 SENT/FAILED 어디로도 전이시키지 않음).
     *
     * <p>또한 {@code businessId} 는 <b>한 배치 내에서 유일</b>해야 한다 — 템플릿이 success/failed 매칭
     * 상관키로 사용하므로, 같은 배치에 동일 businessId 가 둘 이상이면 함께 (오)마킹된다. 배치 내 유일성이
     * 보장되지 않는 도메인은 businessId 에 outbox 별 유일 값(예: idempotencyKey)을 싣는다.
     */
    OutboxBatchSendResult enqueueBatch(List<OutboxEnqueueCommand> commands);

    /** 성공한 outbox 들을 PROCESSING → SENT 로 일괄 마킹. */
    void bulkMarkSent(List<String> outboxIds, Instant now);

    /** 실패한 outbox 들을 일괄 마킹 (재시도 증가 또는 dead-letter 는 구현체 정책). */
    void bulkMarkFailed(List<String> outboxIds, Instant now, String errorMessage);

    /** enqueueBatch 인프라 예외 시 PROCESSING → PENDING 무차감 복귀 (retry_count 유지). */
    void bulkReleaseToPending(List<String> outboxIds);
}
