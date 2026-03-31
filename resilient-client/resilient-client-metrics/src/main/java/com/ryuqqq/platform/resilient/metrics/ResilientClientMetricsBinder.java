package com.ryuqqq.platform.resilient.metrics;

import java.util.concurrent.TimeUnit;

import com.ryuqqq.platform.resilient.HttpMethod;
import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Micrometer Binder 패턴 기반 메트릭 기록기.
 *
 * <p>메트릭 이름/태그 규격:
 * <ul>
 *   <li>resilient_client_duration_seconds - 요청 소요 시간 (Timer)</li>
 *   <li>resilient_client_total - 요청 총 건수 (Counter)</li>
 *   <li>resilient_client_errors_total - 에러 건수 (Counter)</li>
 *   <li>resilient_client_retry_total - Retry 건수 (Counter)</li>
 * </ul>
 */
public class ResilientClientMetricsBinder implements MetricsRecorder, MeterBinder {

    private static final String PREFIX = "resilient_client";

    private MeterRegistry registry;

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
    }

    public ResilientClientMetricsBinder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordSuccess(String clientName, HttpMethod method,
                              long durationNanos, boolean retried) {
        if (registry == null) return;

        String methodName = method.name();

        Timer.builder(PREFIX + "_duration_seconds")
            .tag("name", clientName)
            .tag("outcome", "success")
            .tag("method", methodName)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder(PREFIX + "_total")
            .tag("name", clientName)
            .tag("outcome", "success")
            .tag("method", methodName)
            .register(registry)
            .increment();

        String retryResult = retried ? "successful_with_retry" : "successful_without_retry";
        Counter.builder(PREFIX + "_retry_total")
            .tag("name", clientName)
            .tag("result", retryResult)
            .register(registry)
            .increment();
    }

    @Override
    public void recordFailure(String clientName, HttpMethod method,
                              long durationNanos, ExternalCallException exception,
                              boolean retried) {
        if (registry == null) return;

        String methodName = method.name();
        String exceptionName = exception.getClass().getSimpleName();

        Timer.builder(PREFIX + "_duration_seconds")
            .tag("name", clientName)
            .tag("outcome", "error")
            .tag("method", methodName)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder(PREFIX + "_total")
            .tag("name", clientName)
            .tag("outcome", "error")
            .tag("method", methodName)
            .register(registry)
            .increment();

        Counter.builder(PREFIX + "_errors_total")
            .tag("name", clientName)
            .tag("exception", exceptionName)
            .tag("method", methodName)
            .register(registry)
            .increment();

        Counter.builder(PREFIX + "_retry_total")
            .tag("name", clientName)
            .tag("result", "failed")
            .register(registry)
            .increment();
    }

    @Override
    public void bindCircuitBreaker(String clientName, Object circuitBreaker) {
        if (registry == null || !(circuitBreaker instanceof CircuitBreaker cb)) return;
        new CircuitBreakerMetricsBinder(cb, clientName).bindTo(registry);
    }
}
