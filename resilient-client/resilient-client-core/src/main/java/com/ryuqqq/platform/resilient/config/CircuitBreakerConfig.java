package com.ryuqqq.platform.resilient.config;

import java.time.Duration;

/**
 * CircuitBreaker 설정.
 * Resilience4j CircuitBreakerConfig로 변환하여 사용된다.
 */
public class CircuitBreakerConfig {

    private float failureRateThreshold = 50;
    private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
    private float slowCallRateThreshold = 80;
    private int slidingWindowSize = 20;
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);
    private int permittedCallsInHalfOpenState = 5;
    private int minimumNumberOfCalls = 10;

    public CircuitBreakerConfig failureRateThreshold(float threshold) {
        this.failureRateThreshold = threshold;
        return this;
    }

    public CircuitBreakerConfig slowCallDurationThreshold(Duration duration) {
        this.slowCallDurationThreshold = duration;
        return this;
    }

    public CircuitBreakerConfig slowCallRateThreshold(float threshold) {
        this.slowCallRateThreshold = threshold;
        return this;
    }

    public CircuitBreakerConfig slidingWindowSize(int size) {
        this.slidingWindowSize = size;
        return this;
    }

    public CircuitBreakerConfig waitDurationInOpenState(Duration duration) {
        this.waitDurationInOpenState = duration;
        return this;
    }

    public CircuitBreakerConfig permittedCallsInHalfOpenState(int calls) {
        this.permittedCallsInHalfOpenState = calls;
        return this;
    }

    public CircuitBreakerConfig minimumNumberOfCalls(int calls) {
        this.minimumNumberOfCalls = calls;
        return this;
    }

    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig toResilience4j() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .slowCallDurationThreshold(slowCallDurationThreshold)
            .slowCallRateThreshold(slowCallRateThreshold)
            .slidingWindowSize(slidingWindowSize)
            .waitDurationInOpenState(waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .recordException(e -> e instanceof com.ryuqqq.platform.resilient.exception.ExternalCallException ece
                && ece.shouldRecordFailure())
            .build();
    }
}
