package com.ryuqqq.platform.template.adapter.out.client.example.client;

import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.resilient.spring.ResilientClientRestSupport;
import com.ryuqqq.platform.template.adapter.out.client.example.config.ExampleClientProperties;
import com.ryuqqq.platform.template.port.out.ExampleExternalPort;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Example outbound adapter using {@link ResilientClient} (CB + retry + metrics).
 *
 * <p>Requires {@code resilient.client.clients.example.base-url} (auto-registers {@code
 * exampleResilientClient}).
 */
@Component
@DependsOn("resilientClientRegistry")
@ConditionalOnProperty(prefix = "resilient.client.clients.example", name = "base-url")
public class ExampleExternalClientAdapter implements ExampleExternalPort {

    private final ResilientClient resilientClient;
    private final ExampleClientProperties properties;

    public ExampleExternalClientAdapter(
            @Qualifier("exampleResilientClient") ResilientClient resilientClient,
            ExampleClientProperties properties) {
        this.resilientClient = resilientClient;
        this.properties = properties;
    }

    @Override
    public String fetchHealthBody() {
        return resilientClient.execute(
                ResilientClientRestSupport.getRequest(properties.getHealthPath()), String.class);
    }
}
