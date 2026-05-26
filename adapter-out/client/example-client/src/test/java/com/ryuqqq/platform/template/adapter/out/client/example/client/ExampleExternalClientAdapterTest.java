package com.ryuqqq.platform.template.adapter.out.client.example.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.resilient.RawResponse;
import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.template.adapter.out.client.example.config.ExampleClientConfig;
import com.ryuqqq.platform.template.adapter.out.client.example.config.ExampleClientProperties;
import com.ryuqqq.platform.template.port.out.ExampleExternalPort;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExampleExternalClientAdapterTest {

    @Test
    @DisplayName("ResilientClient로 health body를 조회한다")
    void fetchHealthBody() {
        ResilientClient client =
                ResilientClient.builder()
                        .name("example")
                        .sender(req -> new RawResponse(200, Map.of(), "ok".getBytes()))
                        .build();
        ExampleClientProperties properties = new ExampleClientProperties();
        properties.setHealthPath("/health");

        ExampleExternalClientAdapter adapter = new ExampleExternalClientAdapter(client, properties);

        assertThat(adapter.fetchHealthBody()).isEqualTo("ok");
    }

    @Test
    @DisplayName("resilient.client.clients.example YAML이 있으면 Adapter 빈 등록")
    void registersAdapterFromYamlClient() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                com.ryuqqq.platform.resilient.spring.ResilientClientAutoConfiguration
                                        .class))
                .withUserConfiguration(ExampleClientConfig.class)
                .withPropertyValues(
                        "resilient.client.clients.example.enabled=true",
                        "resilient.client.clients.example.base-url=http://example.test")
                .run(
                        context -> {
                            assertThat(context).hasBean("exampleResilientClient");
                            assertThat(context).hasSingleBean(ExampleExternalPort.class);
                        });
    }

    @Test
    @DisplayName("example resilient client YAML 없으면 Adapter 미등록")
    void skipsWhenNoResilientClient() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                com.ryuqqq.platform.resilient.spring.ResilientClientAutoConfiguration
                                        .class))
                .withUserConfiguration(ExampleClientConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(ExampleExternalPort.class));
    }
}
