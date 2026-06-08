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
