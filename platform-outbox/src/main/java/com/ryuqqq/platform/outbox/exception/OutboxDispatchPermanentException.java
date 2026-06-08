package com.ryuqqq.platform.outbox.exception;

/**
 * 재시도가 무의미한 영구 실패를 알리는 흐름제어 시그널.
 *
 * <p>건별 dispatch({@code notify})에서 영구 실패(예: HTTP 4xx)일 때 던진다. 릴레이는 즉시 종결
 * (markFailedPermanently)한다. 외부로 전파되지 않는 내부 시그널.
 */
public class OutboxDispatchPermanentException extends RuntimeException {

    public OutboxDispatchPermanentException(String message) {
        super(message);
    }

    public OutboxDispatchPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
