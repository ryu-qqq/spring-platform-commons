package com.ryuqqq.platform.outbox.exception;

/**
 * 일시 장애로 재시도를 연기해야 함을 알리는 흐름제어 시그널.
 *
 * <p>건별 dispatch({@code notify})에서 외부 일시 장애(예: Circuit Breaker OPEN)일 때 던진다. 릴레이는 retry
 * 횟수를 소진하지 않고 defer 한다. 특정 라이브러리에 의존하지 않는다.
 */
public class OutboxDispatchDeferredException extends RuntimeException {

    public OutboxDispatchDeferredException(String message) {
        super(message);
    }

    public OutboxDispatchDeferredException(String message, Throwable cause) {
        super(message, cause);
    }
}
