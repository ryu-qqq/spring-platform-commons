package com.ryuqqq.platform.resilient.exception;

/**
 * CircuitBreaker가 OPEN 상태일 때 발생. Retry X.
 */
public class CircuitOpenException extends ExternalCallException {

    public CircuitOpenException(String message, Throwable cause) {
        super(message, cause, false, false);
    }

    public CircuitOpenException(String message) {
        super(message, false, false);
    }
}
