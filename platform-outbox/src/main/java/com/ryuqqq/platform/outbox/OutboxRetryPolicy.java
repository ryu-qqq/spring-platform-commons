package com.ryuqqq.platform.outbox;

import java.time.Duration;
import java.util.Objects;

/**
 * Outbox 재시도 정책 — 소비자 구성 가능.
 *
 * @param maxRetries 일반 실패 재시도 최대 횟수 (초과 시 FAILED dead-letter). 소비자 store 구현이 적용.
 * @param maxDeferDuration 일시 장애 defer 최대 시간. 건별 릴레이 템플릿이 deferRetry 에 주입.
 */
public record OutboxRetryPolicy(int maxRetries, Duration maxDeferDuration) {

    public OutboxRetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        Objects.requireNonNull(maxDeferDuration, "maxDeferDuration must not be null");
    }

    /** 편의 기본값 — 5회·6시간. */
    public static OutboxRetryPolicy defaults() {
        return new OutboxRetryPolicy(5, Duration.ofHours(6));
    }

    public static OutboxRetryPolicy of(int maxRetries, Duration maxDeferDuration) {
        return new OutboxRetryPolicy(maxRetries, maxDeferDuration);
    }
}
