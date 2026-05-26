package com.ryuqqq.platform.bootstrap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBootstrapEnvironmentPostProcessorTest {

    @Test
    @DisplayName("기존 import 없으면 bootstrap yml만 등록")
    void mergeConfigImport_whenEmpty() {
        assertThat(PlatformBootstrapEnvironmentPostProcessor.mergeConfigImport(null))
            .isEqualTo("optional:classpath:application-bootstrap.yml");
        assertThat(PlatformBootstrapEnvironmentPostProcessor.mergeConfigImport("  "))
            .isEqualTo("optional:classpath:application-bootstrap.yml");
    }

    @Test
    @DisplayName("기존 import 있으면 bootstrap yml을 append")
    void mergeConfigImport_whenExisting() {
        assertThat(PlatformBootstrapEnvironmentPostProcessor.mergeConfigImport("classpath:app.yml"))
            .isEqualTo("classpath:app.yml,optional:classpath:application-bootstrap.yml");
    }

    @Test
    @DisplayName("이미 bootstrap yml이 있으면 중복 append 하지 않음")
    void mergeConfigImport_whenAlreadyPresent() {
        String existing = "optional:classpath:application-bootstrap.yml,classpath:app.yml";
        assertThat(PlatformBootstrapEnvironmentPostProcessor.mergeConfigImport(existing))
            .isSameAs(existing);
    }
}
