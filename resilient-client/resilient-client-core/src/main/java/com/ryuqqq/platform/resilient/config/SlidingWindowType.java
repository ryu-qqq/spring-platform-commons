package com.ryuqqq.platform.resilient.config;

/** Resilience4j CircuitBreaker sliding window type. */
public enum SlidingWindowType {
    COUNT_BASED,
    TIME_BASED;

    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType toResilience4j() {
        return switch (this) {
            case COUNT_BASED ->
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
                            .COUNT_BASED;
            case TIME_BASED ->
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
                            .TIME_BASED;
        };
    }
}
