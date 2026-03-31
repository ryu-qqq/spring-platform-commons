package com.ryuqqq.platform.resilient;

import com.ryuqqq.platform.resilient.exception.ExternalCallException;

/**
 * 메트릭 기록 인터페이스. core 모듈에 위치하여 선택적 메트릭 통합을 지원한다.
 *
 * <p>metrics 모듈의 {@code ResilientClientMetricsBinder}가 Micrometer 기반 구현체를 제공하며,
 * 메트릭이 불필요한 경우 {@link #NOOP}이 사용된다.
 */
public interface MetricsRecorder {

    /** 요청 성공 시 호출 */
    void recordSuccess(String clientName, HttpMethod method, long durationNanos, boolean retried);

    /** 요청 실패 시 호출 */
    void recordFailure(String clientName, HttpMethod method, long durationNanos,
                       ExternalCallException exception, boolean retried);

    /** CircuitBreaker 상태 메트릭을 바인딩한다. 기본 구현은 아무것도 하지 않는다. */
    default void bindCircuitBreaker(String clientName, Object circuitBreaker) { }

    /** 아무것도 하지 않는 NoOp 구현체 */
    MetricsRecorder NOOP = new MetricsRecorder() {
        @Override
        public void recordSuccess(String n, HttpMethod m, long d, boolean r) { }

        @Override
        public void recordFailure(String n, HttpMethod m, long d, ExternalCallException e, boolean r) { }
    };
}
