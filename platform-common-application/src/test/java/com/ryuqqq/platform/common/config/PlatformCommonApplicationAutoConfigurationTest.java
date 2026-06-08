package com.ryuqqq.platform.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.common.factory.CommonVoFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformCommonApplicationAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PlatformCommonApplicationAutoConfiguration.class));

    @Test
    @DisplayName("CommonVoFactory 빈이 컴포넌트 스캔 없이 zero-config 로 등록된다")
    void registersCommonVoFactory() {
        runner.run(context -> assertThat(context).hasSingleBean(CommonVoFactory.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwn() {
        runner.withBean("customFactory", CommonVoFactory.class, CommonVoFactory::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommonVoFactory.class);
                    assertThat(context.getBeanNamesForType(CommonVoFactory.class))
                            .containsExactly("customFactory");
                });
    }
}
