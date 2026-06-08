package com.ryuqqq.platform.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.outbox.BatchOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformOutboxAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PlatformOutboxAutoConfiguration.class));

    @Test
    @DisplayName("MeterRegistry 없이도 relay 템플릿 빈이 등록된다")
    void registersWithoutMeterRegistry() {
        runner.run(context ->
                assertThat(context).hasSingleBean(BatchOutboxRelayTemplate.class));
    }

    @Test
    @DisplayName("MeterRegistry 가 있으면 함께 주입되어 등록된다")
    void registersWithMeterRegistry() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context ->
                        assertThat(context).hasSingleBean(BatchOutboxRelayTemplate.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnTemplate() {
        runner.withBean(
                        "customTemplate",
                        BatchOutboxRelayTemplate.class,
                        () -> new BatchOutboxRelayTemplate(null))
                .run(context -> {
                    assertThat(context).hasSingleBean(BatchOutboxRelayTemplate.class);
                    assertThat(context.getBeanNamesForType(BatchOutboxRelayTemplate.class))
                            .containsExactly("customTemplate");
                });
    }
}
