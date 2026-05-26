package com.ryuqqq.platform.resilient.spring;

import com.ryuqqq.platform.resilient.config.SlidingWindowType;

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
 *         enabled: true
 *         base-url: https://api.example.com
 *         default-headers:
 *           X-Service-Name: my-service
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
 *         timeout:
 *           connect: 3s
 *           read: 10s
 * </pre>
 */
@ConfigurationProperties(prefix = "resilient.client")
public class ResilientClientProperties {

    /** {@code true}(기본)이면 {@code enabled + base-url} 클라이언트를 Spring 빈으로 등록한다. */
    private boolean autoRegisterBeans = true;

    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    public boolean isAutoRegisterBeans() {
        return autoRegisterBeans;
    }

    public void setAutoRegisterBeans(boolean autoRegisterBeans) {
        this.autoRegisterBeans = autoRegisterBeans;
    }

    public Map<String, ClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientProperties> clients) {
        this.clients = clients;
    }

    public static class ClientProperties {

        private boolean enabled = true;
        private String baseUrl;
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        private RetryProperties retry = new RetryProperties();
        private TimeoutProperties timeout = new TimeoutProperties();

        /** YAML auto-register: {@code enabled} and non-blank {@code base-url}. */
        public boolean isAutoRegisterCandidate() {
            return enabled && baseUrl != null && !baseUrl.isBlank();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Map<String, String> getDefaultHeaders() {
            return defaultHeaders;
        }

        public void setDefaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = defaultHeaders != null ? defaultHeaders : new LinkedHashMap<>();
        }

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

        public TimeoutProperties getTimeout() {
            return timeout;
        }

        public void setTimeout(TimeoutProperties timeout) {
            this.timeout = timeout;
        }
    }

    public static class TimeoutProperties {

        private Duration connect = Duration.ofSeconds(3);
        private Duration read = Duration.ofSeconds(10);

        public Duration getConnect() {
            return connect;
        }

        public void setConnect(Duration connect) {
            this.connect = connect;
        }

        public Duration getRead() {
            return read;
        }

        public void setRead(Duration read) {
            this.read = read;
        }
    }

    public static class CircuitBreakerProperties {

        private float failureRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(3);
        private float slowCallRateThreshold = 80;
        private int slidingWindowSize = 20;
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
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

        public SlidingWindowType getSlidingWindowType() {
            return slidingWindowType;
        }

        public void setSlidingWindowType(SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
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
