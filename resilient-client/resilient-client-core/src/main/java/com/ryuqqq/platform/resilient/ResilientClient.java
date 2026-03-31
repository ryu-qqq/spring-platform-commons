package com.ryuqqq.platform.resilient;

import com.ryuqqq.platform.resilient.exception.CircuitOpenException;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;

/**
 * Resilience 패턴이 적용된 외부 호출 클라이언트.
 *
 * <p>Circuit Breaker, Retry, 메트릭 수집, 예외 분류를 자동으로 처리하며,
 * 실제 전송 로직은 사용자가 제공하는 {@link RequestSender}에 위임한다.
 */
public interface ResilientClient {

    /**
     * Resilience 패턴이 적용된 요청을 실행한다.
     *
     * @param request      요청 정보
     * @param responseType 응답 타입
     * @param <T>          응답 타입
     * @return 역직렬화된 응답
     * @throws CircuitOpenException     CB OPEN 시
     * @throws ExternalCallException    재시도 소진 후 최종 실패
     */
    <T> T execute(ExternalRequest request, Class<T> responseType);

    /**
     * 응답이 필요 없는 요청 (콜백 알림 등).
     *
     * @param request 요청 정보
     * @throws CircuitOpenException     CB OPEN 시
     * @throws ExternalCallException    재시도 소진 후 최종 실패
     */
    void executeVoid(ExternalRequest request);

    /**
     * 빌더를 반환한다.
     */
    static ResilientClientBuilder builder() {
        return new ResilientClientBuilder();
    }
}
