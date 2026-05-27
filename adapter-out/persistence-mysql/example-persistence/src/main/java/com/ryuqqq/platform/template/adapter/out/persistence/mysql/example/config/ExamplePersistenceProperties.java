package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Feature toggle for the example persistence adapter module. */
@ConfigurationProperties(prefix = "platform.example.persistence")
public class ExamplePersistenceProperties {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
