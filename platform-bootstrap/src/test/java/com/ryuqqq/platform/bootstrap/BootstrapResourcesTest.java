package com.ryuqqq.platform.bootstrap;

import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapResourcesTest {

    @Test
    @DisplayName("application-bootstrap.yml 이 classpath 에 존재한다")
    void applicationBootstrapYamlExists() throws Exception {
        try (InputStream in = resource("application-bootstrap.yml")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes())).contains("shutdown: graceful");
        }
    }

    @Test
    @DisplayName("logback-spring.xml 이 classpath 에 존재한다")
    void logbackSpringXmlExists() throws Exception {
        try (InputStream in = resource("logback-spring.xml")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes())).contains("LogstashEncoder");
        }
    }

    @Test
    @DisplayName("AutoConfiguration.imports 가 PlatformBootstrapAutoConfiguration 을 등록한다")
    void autoConfigurationImportsRegistered() throws Exception {
        try (InputStream in = resource(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes()))
                .contains("com.ryuqqq.platform.bootstrap.config.PlatformBootstrapAutoConfiguration");
        }
    }

    private InputStream resource(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }
}
