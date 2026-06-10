package com.ryuqqq.platform.resilient.spring;

import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.RequestSender;
import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.resilient.ResilientClientBuilder;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.CircuitBreakerProperties;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.ClientProperties;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.RetryProperties;

import org.springframework.web.client.RestClient;

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
    private final RestClient.Builder restClientBuilder;

    /**
     * @param restClientBuilder auto-configured {@link RestClient.Builder} (트레이싱 인터셉터 주입됨).
     *     RestClient-backed 클라이언트가 이 base 빌더에서 파생되어 W3C traceparent 전파가 동작한다.
     */
    public ResilientClientFactory(ResilientClientProperties properties,
                                  MetricsRecorder metricsRecorder,
                                  RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * @deprecated traceparent 자동 전파를 위해 auto-configured {@link RestClient.Builder}를 받는
     *     {@link #ResilientClientFactory(ResilientClientProperties, MetricsRecorder, RestClient.Builder)}를
     *     사용하라. 이 생성자는 plain {@code RestClient.builder()}로 폴백하여 RestClient-backed 클라이언트의
     *     트레이스 전파가 동작하지 않는다.
     */
    @Deprecated
    public ResilientClientFactory(ResilientClientProperties properties,
                                  MetricsRecorder metricsRecorder) {
        this(properties, metricsRecorder, RestClient.builder());
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

    /**
     * {@link ResilientClientRestSupport}로 RestClient를 만들고 properties의 timeout/CB/retry를 적용한다.
     *
     * @param clientName properties의 clients 키
     * @param baseUrl    RestClient base URL
     */
    public ResilientClient createRestClientBacked(String clientName, String baseUrl) {
        ClientProperties clientProps = properties.getClients().get(clientName);
        if (clientProps == null) {
            clientProps = new ClientProperties();
            clientProps.setBaseUrl(baseUrl);
        }
        return createRestClientBacked(clientName, clientProps);
    }

    /**
     * {@link ClientProperties}의 base-url, timeout, default-headers로 RestClient-backed client 생성.
     */
    public ResilientClient createRestClientBacked(String clientName, ClientProperties clientProps) {
        String baseUrl = clientProps.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("base-url is required for client: " + clientName);
        }
        RestClient restClient =
                ResilientClientRestSupport.buildRestClient(
                        restClientBuilder, baseUrl, clientProps.getTimeout(),
                        clientProps.getDefaultHeaders());
        return create(clientName, ResilientClientRestSupport.requestSender(restClient));
    }

    private void applyCircuitBreaker(ResilientClientBuilder builder,
                                     CircuitBreakerProperties cbProps) {
        builder.circuitBreaker(cb -> cb
            .failureRateThreshold(cbProps.getFailureRateThreshold())
            .slowCallDurationThreshold(cbProps.getSlowCallDurationThreshold())
            .slowCallRateThreshold(cbProps.getSlowCallRateThreshold())
            .slidingWindowSize(cbProps.getSlidingWindowSize())
            .slidingWindowType(cbProps.getSlidingWindowType())
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
