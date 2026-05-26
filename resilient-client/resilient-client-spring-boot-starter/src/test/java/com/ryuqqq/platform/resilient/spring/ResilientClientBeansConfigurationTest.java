package com.ryuqqq.platform.resilient.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.resilient.ResilientClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ResilientClientBeansConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ResilientClientAutoConfiguration.class));

    @Test
    @DisplayName("enabled + base-url 클라이언트는 ResilientClient 빈과 Registry에 등록")
    void autoRegistersClientBean() {
        runner.withPropertyValues(
                        "resilient.client.clients.callback.enabled=true",
                        "resilient.client.clients.callback.base-url=http://callback.test")
                .run(
                        context -> {
                            assertThat(context).hasBean("callbackResilientClient");
                            assertThat(context).hasSingleBean(ResilientClientRegistry.class);

                            ResilientClientRegistry registry =
                                    context.getBean(ResilientClientRegistry.class);
                            assertThat(registry.get("callback")).isNotNull();
                            assertThat(context.getBean("callbackResilientClient"))
                                    .isSameAs(registry.get("callback"));
                        });
    }

    @Test
    @DisplayName("base-url 없으면 빈 미등록")
    void skipsWithoutBaseUrl() {
        runner.withPropertyValues("resilient.client.clients.callback.enabled=true")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean("callbackResilientClient");
                            assertThat(context.getBean(ResilientClientRegistry.class).getClients())
                                    .isEmpty();
                        });
    }

    @Test
    @DisplayName("enabled=false 이면 빈 미등록")
    void skipsWhenDisabled() {
        runner.withPropertyValues(
                        "resilient.client.clients.callback.enabled=false",
                        "resilient.client.clients.callback.base-url=http://callback.test")
                .run(context -> assertThat(context).doesNotHaveBean("callbackResilientClient"));
    }

    @Test
    @DisplayName("auto-register-beans=false 이면 Registry만 비어 등록")
    void disablesAutoRegister() {
        runner.withPropertyValues(
                        "resilient.client.auto-register-beans=false",
                        "resilient.client.clients.callback.enabled=true",
                        "resilient.client.clients.callback.base-url=http://callback.test")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(ResilientClientRegistry.class);
                            assertThat(context).doesNotHaveBean("callbackResilientClient");
                        });
    }

    @Test
    @DisplayName("toBeanName 규칙")
    void beanNaming() {
        assertThat(ResilientClientRegistry.toBeanName("setofCommerce"))
                .isEqualTo("setofCommerceResilientClient");
    }
}
