package com.ryuqqq.platform.resilient;

import java.util.Objects;
import java.util.function.Consumer;

import com.ryuqqq.platform.resilient.config.CircuitBreakerConfig;
import com.ryuqqq.platform.resilient.config.RetryConfig;

/**
 * {@link ResilientClient}를 생성하는 빌더.
 */
public class ResilientClientBuilder {

    private String name;
    private RequestSender sender;
    private CircuitBreakerConfig circuitBreakerConfig;
    private RetryConfig retryConfig;
    private ResponseClassifier responseClassifier;
    private MetricsRecorder metricsRecorder;

    ResilientClientBuilder() {
    }

    /** 클라이언트 이름. 메트릭 태그, CB 이름에 사용된다. */
    public ResilientClientBuilder name(String name) {
        this.name = name;
        return this;
    }

    /** 전송 구현체를 주입한다. */
    public ResilientClientBuilder sender(RequestSender sender) {
        this.sender = sender;
        return this;
    }

    /** CircuitBreaker 설정을 커스터마이즈한다. */
    public ResilientClientBuilder circuitBreaker(Consumer<CircuitBreakerConfig> customizer) {
        this.circuitBreakerConfig = new CircuitBreakerConfig();
        customizer.accept(this.circuitBreakerConfig);
        return this;
    }

    /** Retry 설정을 커스터마이즈한다. */
    public ResilientClientBuilder retry(Consumer<RetryConfig> customizer) {
        this.retryConfig = new RetryConfig();
        customizer.accept(this.retryConfig);
        return this;
    }

    /** 커스텀 응답 분류기를 지정한다. */
    public ResilientClientBuilder responseClassifier(ResponseClassifier classifier) {
        this.responseClassifier = classifier;
        return this;
    }

    /** 메트릭 기록기를 지정한다. */
    public ResilientClientBuilder metricsRecorder(MetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
        return this;
    }

    public ResilientClient build() {
        Objects.requireNonNull(name, "name은 필수입니다");
        Objects.requireNonNull(sender, "sender는 필수입니다");

        if (circuitBreakerConfig == null) {
            circuitBreakerConfig = new CircuitBreakerConfig();
        }
        if (retryConfig == null) {
            retryConfig = new RetryConfig();
        }
        if (responseClassifier == null) {
            responseClassifier = new DefaultResponseClassifier();
        }
        if (metricsRecorder == null) {
            metricsRecorder = MetricsRecorder.NOOP;
        }

        return new DefaultResilientClient(
            name, sender, circuitBreakerConfig, retryConfig, responseClassifier, metricsRecorder);
    }
}
