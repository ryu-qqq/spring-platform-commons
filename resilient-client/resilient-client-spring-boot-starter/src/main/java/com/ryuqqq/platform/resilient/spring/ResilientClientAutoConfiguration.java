package com.ryuqqq.platform.resilient.spring;

import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.metrics.ResilientClientMetricsBinder;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Resilient Client SDK Spring Boot 자동 설정.
 *
 * <ul>
 *   <li>{@link ResilientClientProperties} 바인딩</li>
 *   <li>{@link MeterRegistry} 빈이 있으면 Micrometer 메트릭 자동 연동</li>
 *   <li>{@link ResilientClientFactory} 빈 등록</li>
 * </ul>
 */
@AutoConfiguration
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
            MetricsRecorder metricsRecorder) {
        return new ResilientClientFactory(properties, metricsRecorder);
    }
}
