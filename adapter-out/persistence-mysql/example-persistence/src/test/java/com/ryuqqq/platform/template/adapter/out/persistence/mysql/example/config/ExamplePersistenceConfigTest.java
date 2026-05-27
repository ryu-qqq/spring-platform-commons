package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.adapter.ExampleRecordQueryAdapter;
import com.ryuqqq.platform.template.port.out.ExampleRecordPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class ExamplePersistenceConfigTest {

    @Test
    @DisplayName("platform.example.persistence.enabled=false 이면 Adapter 미등록")
    void skipsWhenDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdapterScanOnlyConfig.class)
                .withPropertyValues("platform.example.persistence.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExampleRecordPort.class));
    }

    @Configuration
    @ComponentScan(basePackageClasses = ExampleRecordQueryAdapter.class)
    @EnableConfigurationProperties(ExamplePersistenceProperties.class)
    static class AdapterScanOnlyConfig {}
}
