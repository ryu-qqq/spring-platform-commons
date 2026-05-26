package com.ryuqqq.platform.resilient;

import java.util.function.Function;

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
     * fallback 람다를 받는 오버로드. {@link ExternalCallException} 계열이 발생하면
     * fallback을 호출하여 그 반환값을 사용자에게 돌려준다.
     *
     * <p>호출 시점에 예외별 분기를 작성할 수 있다. fallback이 다시 예외를 던지면
     * 그대로 전파된다. 메트릭은 fallback 호출 여부와 무관하게 failure로 이미 기록된다.
     *
     * <pre>
     * ReviewDto r = client.execute(req, ReviewDto.class, ex -&gt; {
     *     if (ex instanceof CircuitOpenException) return cachedReview();
     *     if (ex instanceof ServerException)    return emptyReview();
     *     throw ex;
     * });
     * </pre>
     *
     * @param request      요청 정보
     * @param responseType 응답 타입
     * @param fallback     {@link ExternalCallException} 발생 시 호출되는 람다
     * @param <T>          응답 타입
     * @return 정상 응답 또는 fallback 반환값
     */
    default <T> T execute(ExternalRequest request,
                          Class<T> responseType,
                          Function<ExternalCallException, T> fallback) {
        try {
            return execute(request, responseType);
        } catch (ExternalCallException ex) {
            return fallback.apply(ex);
        }
    }

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
