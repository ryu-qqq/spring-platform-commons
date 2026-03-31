package com.ryuqqq.platform.resilient.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * CircuitBreaker 상태를 Micrometer Gauge로 노출하는 Binder.
 *
 * <p>메트릭: resilient_client_circuit_breaker_state
 * <ul>
 *   <li>0 = CLOSED</li>
 *   <li>1 = OPEN</li>
 *   <li>2 = HALF_OPEN</li>
 * </ul>
 */
public class CircuitBreakerMetricsBinder implements MeterBinder {

    private final CircuitBreaker circuitBreaker;
    private final String clientName;

    public CircuitBreakerMetricsBinder(CircuitBreaker circuitBreaker, String clientName) {
        this.circuitBreaker = circuitBreaker;
        this.clientName = clientName;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("resilient_client_circuit_breaker_state",
                circuitBreaker, cb -> mapState(cb.getState()))
            .tag("name", clientName)
            .description("CircuitBreaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
            .register(registry);
    }

    private double mapState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0.0;
            case OPEN -> 1.0;
            case HALF_OPEN -> 2.0;
            default -> -1.0;
        };
    }
}
