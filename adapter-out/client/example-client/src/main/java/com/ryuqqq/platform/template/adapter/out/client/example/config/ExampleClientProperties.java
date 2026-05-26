package com.ryuqqq.platform.template.adapter.out.client.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Example adapter domain settings (HTTP client wiring is {@code resilient.client.clients.example}).
 *
 * <pre>
 * example-client:
 *   health-path: /health
 *
 * resilient:
 *   client:
 *     clients:
 *       example:
 *         enabled: true
 *         base-url: http://localhost:8089
 * </pre>
 */
@ConfigurationProperties(prefix = "example-client")
public class ExampleClientProperties {

    private String healthPath = "/health";

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }
}
