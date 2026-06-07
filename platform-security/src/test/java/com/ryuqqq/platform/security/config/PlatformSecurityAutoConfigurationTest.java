package com.ryuqqq.platform.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withConfiguration(
                            AutoConfigurations.of(PlatformSecurityAutoConfiguration.class))
                    .withPropertyValues("security.service-token.secret=test-secret");

    @Test
    @DisplayName("필터·entrypoint·handler 빈이 자동 등록된다")
    void registersBeans() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(ServiceTokenAuthenticationFilter.class)
                .hasSingleBean(ServiceTokenAuthenticationEntryPoint.class)
                .hasSingleBean(ServiceTokenAccessDeniedHandler.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnFilter() {
        runner.withBean(
                        "customFilter",
                        ServiceTokenAuthenticationFilter.class,
                        () -> {
                            ServiceTokenProperties p = new ServiceTokenProperties();
                            p.setSecret("test-secret");
                            return new ServiceTokenAuthenticationFilter(p);
                        })
                .run(context -> {
                    assertThat(context).hasSingleBean(ServiceTokenAuthenticationFilter.class);
                    assertThat(context.getBeanNamesForType(ServiceTokenAuthenticationFilter.class))
                            .containsExactly("customFilter");
                });
    }
}
