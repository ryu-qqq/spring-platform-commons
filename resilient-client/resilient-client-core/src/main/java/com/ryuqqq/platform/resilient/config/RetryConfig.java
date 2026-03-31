package com.ryuqqq.platform.resilient.config;

import java.time.Duration;

import com.ryuqqq.platform.resilient.exception.ExternalCallException;

/**
 * Retry 설정.
 * Resilience4j RetryConfig로 변환하여 사용된다.
 */
public class RetryConfig {

    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofMillis(100);
    private double backoffMultiplier = 2.0;

    public RetryConfig maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public RetryConfig initialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
        return this;
    }

    public RetryConfig backoffMultiplier(double multiplier) {
        this.backoffMultiplier = multiplier;
        return this;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public io.github.resilience4j.retry.RetryConfig toResilience4j() {
        return io.github.resilience4j.retry.RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialBackoff(initialBackoff.toMillis(), backoffMultiplier))
            .retryOnException(e -> e instanceof ExternalCallException ece && ece.isRetriable())
            .build();
    }
}
