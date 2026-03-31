package com.ryuqqq.platform.resilient.exception;

/**
 * 5xx 서버 오류. CB 기록 O, Retry O.
 */
public class ServerException extends ExternalCallException {

    private final int statusCode;

    public ServerException(int statusCode, String message) {
        super(message, true, true);
        this.statusCode = statusCode;
    }

    public ServerException(int statusCode, String message, Throwable cause) {
        super(message, cause, true, true);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
