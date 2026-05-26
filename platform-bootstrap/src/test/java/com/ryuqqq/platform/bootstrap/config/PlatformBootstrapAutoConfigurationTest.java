package com.ryuqqq.platform.bootstrap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBootstrapAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformBootstrapAutoConfiguration.class));

    @Test
    @DisplayName("auto-configuration 컨텍스트가 정상 기동한다")
    void contextLoads() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
