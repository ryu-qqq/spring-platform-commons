package com.ryuqqq.platform.resilient.exception;

/**
 * 400 Bad Request. CB 기록 X, Retry X (영구 실패).
 */
public class BadRequestException extends ExternalCallException {

    public BadRequestException(String message) {
        super(message, false, false);
    }
}
