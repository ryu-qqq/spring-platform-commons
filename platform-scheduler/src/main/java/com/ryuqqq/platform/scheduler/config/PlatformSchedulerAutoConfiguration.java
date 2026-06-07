package com.ryuqqq.platform.scheduler.config;

import com.ryuqqq.platform.scheduler.aspect.SchedulerLoggingAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Scheduler 자동 설정 — AOP(aspectj)와 {@link MeterRegistry}가 있으면
 * {@link SchedulerLoggingAspect}를 등록한다. MeterRegistry는 소비측(actuator/micrometer)이 제공.
 */
@AutoConfiguration
@ConditionalOnClass(ProceedingJoinPoint.class)
public class PlatformSchedulerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public SchedulerLoggingAspect schedulerLoggingAspect(MeterRegistry meterRegistry) {
        return new SchedulerLoggingAspect(meterRegistry);
    }
}
