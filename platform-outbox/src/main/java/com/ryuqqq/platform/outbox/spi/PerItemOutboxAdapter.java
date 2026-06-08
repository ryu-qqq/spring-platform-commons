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
