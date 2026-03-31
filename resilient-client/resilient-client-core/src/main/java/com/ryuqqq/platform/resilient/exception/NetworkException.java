package com.ryuqqq.platform.resilient.exception;

/**
 * 네트워크 오류 (타임아웃, 연결 실패 등). CB 기록 O, Retry O.
 */
public class NetworkException extends ExternalCallException {

    public NetworkException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
