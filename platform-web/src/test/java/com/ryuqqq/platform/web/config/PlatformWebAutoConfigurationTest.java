package com.ryuqqq.platform.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.web.error.ErrorMapperRegistry;
import com.ryuqqq.platform.web.error.GlobalExceptionHandler;
import com.ryuqqq.platform.web.filter.RequestContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class PlatformWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PlatformWebAutoConfiguration.class));

    @Test
    @DisplayName("서블릿 웹 환경에서 표준 web 빈이 자동 등록된다")
    void registersBeansInServletWebApp() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(ErrorMapperRegistry.class)
                .hasSingleBean(GlobalExceptionHandler.class)
                .hasSingleBean(RequestContextFilter.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean으로 양보한다")
    void backsOffWhenUserDefinesOwnBean() {
        runner.withBean("customFilter", RequestContextFilter.class, RequestContextFilter::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestContextFilter.class);
                    assertThat(context.getBeanNamesForType(RequestContextFilter.class))
                            .containsExactly("customFilter");
                });
    }
}
