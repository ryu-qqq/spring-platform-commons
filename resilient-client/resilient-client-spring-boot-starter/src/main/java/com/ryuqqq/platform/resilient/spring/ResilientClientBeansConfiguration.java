package com.ryuqqq.platform.resilient.spring;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ryuqqq.platform.resilient.ResilientClient;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.ClientProperties;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code resilient.client.clients.*} YAML 선언으로 {@link ResilientClient} 빈을 자동 등록한다.
 *
 * <p>{@code enabled: true} + {@code base-url} 이 있는 항목만 등록. 동일 이름의 수동 {@code @Bean}이 있으면
 * 건너뛴다.
 */
@Configuration
@EnableConfigurationProperties(ResilientClientProperties.class)
@ConditionalOnProperty(
        prefix = "resilient.client",
        name = "auto-register-beans",
        havingValue = "true",
        matchIfMissing = true)
public class ResilientClientBeansConfiguration {

    @Bean
    public ResilientClientRegistry resilientClientRegistry(
            ResilientClientFactory factory,
            ResilientClientProperties properties,
            ConfigurableListableBeanFactory beanFactory) {

        Map<String, ResilientClient> registered = new LinkedHashMap<>();

        properties.getClients().forEach((clientKey, clientProps) -> {
            if (!clientProps.isAutoRegisterCandidate()) {
                return;
            }
            String beanName = ResilientClientRegistry.toBeanName(clientKey);
            if (beanFactory.containsBean(beanName)) {
                registered.put(clientKey, beanFactory.getBean(beanName, ResilientClient.class));
                return;
            }
            ResilientClient client = factory.createRestClientBacked(clientKey, clientProps);
            beanFactory.registerSingleton(beanName, client);
            registered.put(clientKey, client);
        });

        return new ResilientClientRegistry(registered);
    }
}
