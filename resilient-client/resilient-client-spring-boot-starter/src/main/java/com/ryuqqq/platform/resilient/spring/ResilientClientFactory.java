package com.ryuqqq.platform.resilient.spring;

import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.RequestSender;
import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.resilient.ResilientClientBuilder;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.CircuitBreakerProperties;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.ClientProperties;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.RetryProperties;

/**
 * Properties 기반 {@link ResilientClient} 팩토리.
 *
 * <p>Spring 환경에서 {@code application.yml}에 정의된 클라이언트 설정과
 * 사용자가 제공하는 {@link RequestSender}를 결합하여 ResilientClient를 생성한다.
 *
 * <pre>
 * // @Configuration 에서 사용
 * {@literal @}Bean
 * public ResilientClient callbackClient(ResilientClientFactory factory,
 *                                       RestClient callbackRestClient) {
 *     return factory.create("callback", request -> {
 *         // RestClient 기반 전송 로직
 *     });
 * }
 * </pre>
 */
public class ResilientClientFactory {

    private final ResilientClientProperties properties;
    private final MetricsRecorder metricsRecorder;

    public ResilientClientFactory(ResilientClientProperties properties,
                                  MetricsRecorder metricsRecorder) {
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * properties에 정의된 설정으로 ResilientClient를 생성한다.
     *
     * @param clientName properties의 clients 키 (예: "callback")
     * @param sender     전송 구현체
     * @return 설정이 적용된 ResilientClient
     */
    public ResilientClient create(String clientName, RequestSender sender) {
        ClientProperties clientProps = properties.getClients().get(clientName);

        ResilientClientBuilder builder = ResilientClient.builder()
            .name(clientName)
            .sender(sender)
            .metricsRecorder(metricsRecorder);

        if (clientProps != null) {
            applyCircuitBreaker(builder, clientProps.getCircuitBreaker());
            applyRetry(builder, clientProps.getRetry());
        }

        return builder.build();
    }

    private void applyCircuitBreaker(ResilientClientBuilder builder,
                                     CircuitBreakerProperties cbProps) {
        builder.circuitBreaker(cb -> cb
            .failureRateThreshold(cbProps.getFailureRateThreshold())
            .slowCallDurationThreshold(cbProps.getSlowCallDurationThreshold())
            .slowCallRateThreshold(cbProps.getSlowCallRateThreshold())
            .slidingWindowSize(cbProps.getSlidingWindowSize())
            .waitDurationInOpenState(cbProps.getWaitDurationInOpenState())
            .permittedCallsInHalfOpenState(cbProps.getPermittedCallsInHalfOpenState())
            .minimumNumberOfCalls(cbProps.getMinimumNumberOfCalls()));
    }

    private void applyRetry(ResilientClientBuilder builder, RetryProperties retryProps) {
        builder.retry(retry -> retry
            .maxAttempts(retryProps.getMaxAttempts())
            .initialBackoff(retryProps.getInitialBackoff())
            .backoffMultiplier(retryProps.getBackoffMultiplier()));
    }
}
