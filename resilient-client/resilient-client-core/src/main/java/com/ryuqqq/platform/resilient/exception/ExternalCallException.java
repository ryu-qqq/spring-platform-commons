package com.ryuqqq.platform.resilient.exception;

/**
 * SDK 예외 계층의 최상위 클래스.
 * 외부 시스템 호출 중 발생하는 모든 예외의 부모.
 */
public abstract class ExternalCallException extends RuntimeException {

    private final boolean retriable;
    private final boolean recordFailure;

    protected ExternalCallException(String message, Throwable cause,
                                    boolean retriable, boolean recordFailure) {
        super(message, cause);
        this.retriable = retriable;
        this.recordFailure = recordFailure;
    }

    protected ExternalCallException(String message,
                                    boolean retriable, boolean recordFailure) {
        super(message);
        this.retriable = retriable;
        this.recordFailure = recordFailure;
    }

    /** Retry 대상 여부 */
    public boolean isRetriable() {
        return retriable;
    }

    /** CircuitBreaker 실패로 기록할지 여부 */
    public boolean shouldRecordFailure() {
        return recordFailure;
    }
}
