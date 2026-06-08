package com.ryuqqq.platform.outbox.spi;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 릴레이의 트랜스포트 무관 수명주기 base SPI (소비측 구현).
 *
 * <p>claim → mark(SENT/FAILED) → release 전이는 발행 채널(배치 큐·건별 HTTP 등)과 무관하게 공통이다.
 * 트랜스포트별 어댑터({@link BatchOutboxAdapter}·{@code PerItemOutboxAdapter})가 이 base 를 상속해
 * 자기 dispatch 메서드를 더한다.
 *
 * @param <O> 도메인 Outbox 타입
 */
public interface OutboxStore<O> {

    /** 로그 라벨 (예: "다운로드"). */
    String label();

    /** 메트릭 태그용 파이프라인 식별자 — 저카디널리티. */
    String pipeline();

    /** outbox 자체의 ID — bulkMark 대상. */
    String outboxId(O outbox);

    /** PENDING → PROCESSING 원자 claim 후 도메인 객체 목록 반환. */
    List<O> claimPendingMessages(int batchSize);

    /** 성공한 outbox 들을 PROCESSING → SENT 로 일괄 마킹. */
    void bulkMarkSent(List<String> outboxIds, Instant now);

    /** 실패한 outbox 들을 일괄 마킹 (재시도 증가 또는 dead-letter 는 구현체 정책). */
    void bulkMarkFailed(List<String> outboxIds, Instant now, String errorMessage);

    /** dispatch 인프라 예외 시 PROCESSING → PENDING 무차감 복귀 (retry_count 유지). */
    void bulkReleaseToPending(List<String> outboxIds);
}
