package com.ryuqqq.platform.scheduler.config;

import com.ryuqqq.platform.scheduler.aspect.SchedulerLoggingAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Scheduler 자동 설정 — AOP(aspectj)가 있으면 {@link SchedulerLoggingAspect}를 등록한다.
 *
 * <p>{@link MeterRegistry}는 optional — 있으면 메트릭까지, 없으면 로깅·TraceId만 동작한다(핵심 로깅이
 * 메트릭 의존에 묶이지 않도록 {@link ObjectProvider}로 주입). 소비측이 동일 빈을 정의하면 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(ProceedingJoinPoint.class)
public class PlatformSchedulerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SchedulerLoggingAspect schedulerLoggingAspect(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new SchedulerLoggingAspect(meterRegistryProvider.getIfAvailable());
    }
}
