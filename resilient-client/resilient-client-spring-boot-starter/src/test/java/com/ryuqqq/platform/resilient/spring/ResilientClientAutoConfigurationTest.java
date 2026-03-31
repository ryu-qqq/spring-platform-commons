package com.ryuqqq.platform.resilient.spring;

import java.util.Map;

import com.ryuqqq.platform.resilient.MetricsRecorder;
import com.ryuqqq.platform.resilient.RawResponse;
import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.resilient.metrics.ResilientClientMetricsBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResilientClientAutoConfiguration.class));

    @Test
    @DisplayName("MeterRegistry 없으면 NOOP MetricsRecorder 등록")
    void noMeterRegistry_noopMetrics() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MetricsRecorder.class);
            assertThat(context).hasSingleBean(ResilientClientFactory.class);
            assertThat(context.getBean(MetricsRecorder.class))
                .isSameAs(MetricsRecorder.NOOP);
        });
    }

    @Test
    @DisplayName("MeterRegistry 있으면 ResilientClientMetricsBinder 등록")
    void withMeterRegistry_micrometerMetrics() {
        runner.withBean(SimpleMeterRegistry.class)
            .run(context -> {
                assertThat(context).hasSingleBean(MetricsRecorder.class);
                assertThat(context.getBean(MetricsRecorder.class))
                    .isInstanceOf(ResilientClientMetricsBinder.class);
            });
    }

    @Test
    @DisplayName("Properties 바인딩으로 Factory에서 ResilientClient 생성")
    void propertiesBinding() {
        runner.withPropertyValues(
                "resilient.client.clients.callback.circuit-breaker.failure-rate-threshold=30",
                "resilient.client.clients.callback.retry.max-attempts=5"
            )
            .run(context -> {
                ResilientClientFactory factory = context.getBean(ResilientClientFactory.class);

                ResilientClient client = factory.create("callback",
                    req -> new RawResponse(200, Map.of(), new byte[0]));

                assertThat(client).isNotNull();

                ResilientClientProperties props = context.getBean(ResilientClientProperties.class);
                assertThat(props.getClients()).containsKey("callback");
                assertThat(props.getClients().get("callback")
                    .getCircuitBreaker().getFailureRateThreshold()).isEqualTo(30f);
                assertThat(props.getClients().get("callback")
                    .getRetry().getMaxAttempts()).isEqualTo(5);
            });
    }

    @Test
    @DisplayName("Properties 없는 클라이언트도 기본값으로 생성 가능")
    void createWithoutProperties() {
        runner.run(context -> {
            ResilientClientFactory factory = context.getBean(ResilientClientFactory.class);

            ResilientClient client = factory.create("unknown",
                req -> new RawResponse(200, Map.of(), new byte[0]));

            assertThat(client).isNotNull();
        });
    }

    @Test
    @DisplayName("커스텀 MetricsRecorder 빈이 있으면 자동 설정 건너뜀")
    void customMetricsRecorderTakesPrecedence() {
        MetricsRecorder custom = MetricsRecorder.NOOP;

        runner.withBean(SimpleMeterRegistry.class)
            .withBean(MetricsRecorder.class, () -> custom)
            .run(context -> {
                assertThat(context).hasSingleBean(MetricsRecorder.class);
                assertThat(context.getBean(MetricsRecorder.class)).isSameAs(custom);
            });
    }
}
