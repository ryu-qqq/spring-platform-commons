package com.ryuqqq.platform.resilient.exception;

/**
 * 4xx 클라이언트 오류 (400 제외). CB 기록 X, Retry X (영구 실패).
 */
public class ClientException extends ExternalCallException {

    private final int statusCode;

    public ClientException(int statusCode, String message) {
        super(message, false, false);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
