package com.ryuqqq.platform.scheduler.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.scheduler.aspect.SchedulerLoggingAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link PlatformSchedulerAutoConfiguration} 자동설정 검증.
 *
 * <p>{@link ApplicationContextRunner}로 (1) 빈 등록, (2) {@code @ConditionalOnMissingBean} 양보,
 * (3) {@code @ConditionalOnClass(ProceedingJoinPoint)} 클래스패스 부재 시 비활성화,
 * (4) {@link MeterRegistry} 부재/존재 시 모두 빈이 등록되는지를 검증한다.
 */
class PlatformSchedulerAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PlatformSchedulerAutoConfiguration.class));

    @Test
    @DisplayName("SchedulerLoggingAspect 빈이 자동 등록된다 (MeterRegistry 없이도)")
    void registersAspectWithoutMeterRegistry() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SchedulerLoggingAspect.class);
            // MeterRegistry는 optional — 없어도 핵심 로깅 빈은 등록된다.
            assertThat(context).doesNotHaveBean(MeterRegistry.class);
        });
    }

    @Test
    @DisplayName("MeterRegistry 빈이 있으면 그대로 주입되어 Aspect가 등록된다")
    void registersAspectWithMeterRegistry() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulerLoggingAspect.class);
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                });
    }

    @Test
    @DisplayName("소비측이 동일 타입 Aspect 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnAspect() {
        runner.withBean(
                        "customAspect",
                        SchedulerLoggingAspect.class,
                        () -> new SchedulerLoggingAspect(null))
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulerLoggingAspect.class);
                    assertThat(context.getBeanNamesForType(SchedulerLoggingAspect.class))
                            .containsExactly("customAspect");
                });
    }

    @Test
    @DisplayName("클래스패스에 ProceedingJoinPoint(aspectj) 가 없으면 @ConditionalOnClass 로 자동설정이 비활성화된다")
    void backsOffWhenAspectjNotOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(ProceedingJoinPoint.class))
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(PlatformSchedulerAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(SchedulerLoggingAspect.class);
                });
    }
}
