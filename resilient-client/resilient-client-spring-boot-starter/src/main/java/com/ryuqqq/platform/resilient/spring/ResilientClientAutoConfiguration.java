package com.ryuqqq.platform.resilient.spring;

import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.metrics.ResilientClientMetricsBinder;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * Resilient Client SDK Spring Boot 자동 설정.
 *
 * <ul>
 *   <li>{@link ResilientClientProperties} 바인딩</li>
 *   <li>{@link MeterRegistry} 빈이 있으면 Micrometer 메트릭 자동 연동</li>
 *   <li>{@link ResilientClientFactory} 빈 등록</li>
 *   <li>{@link ResilientClientBeansConfiguration} — YAML declarative client 빈</li>
 * </ul>
 */
@AutoConfiguration
@Import(ResilientClientBeansConfiguration.class)
@EnableConfigurationProperties(ResilientClientProperties.class)
public class ResilientClientAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MetricsRecorder.class)
    public MetricsRecorder resilientClientMetricsRecorder(MeterRegistry meterRegistry) {
        return new ResilientClientMetricsBinder(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(MetricsRecorder.class)
    public MetricsRecorder noopMetricsRecorder() {
        return MetricsRecorder.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilientClientFactory resilientClientFactory(
            ResilientClientProperties properties,
            MetricsRecorder metricsRecorder,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        // auto-configured RestClient.Builder(트레이싱 인터셉터 주입)가 있으면 그것을, 없으면(zero-config)
        // 기본 빌더로 폴백. RestClient-backed 클라이언트가 이 base에서 파생돼 traceparent 전파가 동작.
        // getIfUnique: 후보 0개 또는 2개 이상이면 plain 빌더로 폴백(NoUniqueBeanDefinitionException 회피).
        RestClient.Builder restClientBuilder =
                restClientBuilderProvider.getIfUnique(RestClient::builder);
        return new ResilientClientFactory(properties, metricsRecorder, restClientBuilder);
    }
}
