package com.ryuqqq.platform.resilient.spring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot properties 바인딩.
 *
 * <pre>
 * resilient:
 *   client:
 *     clients:
 *       callback:
 *         circuit-breaker:
 *           failure-rate-threshold: 50
 *           slow-call-duration-threshold: 3s
 *           slow-call-rate-threshold: 80
 *           sliding-window-size: 20
 *           wait-duration-in-open-state: 60s
 *           permitted-calls-in-half-open-state: 5
 *           minimum-number-of-calls: 10
 *         retry:
 *           max-attempts: 3
 *           initial-backoff: 100ms
 *           backoff-multiplier: 2.0
 * </pre>
 */
@ConfigurationProperties(prefix = "resilient.client")
public class ResilientClientProperties {

    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    public Map<String, ClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientProperties> clients) {
        this.clients = clients;
    }

    public static class ClientProperties {

        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        private RetryProperties retry = new RetryProperties();

        public CircuitBreakerProperties getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public RetryProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryProperties retry) {
            this.retry = retry;
        }
    }

    public static class CircuitBreakerProperties {

        private float failureRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
        private float slowCallRateThreshold = 80;
        private int slidingWindowSize = 20;
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);
        private int permittedCallsInHalfOpenState = 5;
        private int minimumNumberOfCalls = 10;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedCallsInHalfOpenState() {
            return permittedCallsInHalfOpenState;
        }

        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
            this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }
    }

    public static class RetryProperties {

        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(100);
        private double backoffMultiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }
}
