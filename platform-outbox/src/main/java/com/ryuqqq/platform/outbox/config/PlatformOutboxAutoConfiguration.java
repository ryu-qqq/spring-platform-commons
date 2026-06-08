package com.ryuqqq.platform.outbox.config;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.QueueOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Outbox 자동 설정 — {@link QueueOutboxRelayTemplate} 빈을 등록한다.
 *
 * <p>{@link MeterRegistry} 는 optional — 소비측(actuator/micrometer)이 제공하면 주입되고, 없으면 null
 * 로 메트릭 no-op 한다. 소비측이 동일 타입 빈을 정의하면 {@link ConditionalOnMissingBean} 으로 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(SchedulerBatchProcessingResult.class)
public class PlatformOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueueOutboxRelayTemplate queueOutboxRelayTemplate(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new QueueOutboxRelayTemplate(meterRegistryProvider.getIfAvailable());
    }
}
