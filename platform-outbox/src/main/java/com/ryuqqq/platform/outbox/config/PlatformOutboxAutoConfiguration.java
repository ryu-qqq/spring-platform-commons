package com.ryuqqq.platform.outbox.config;

import com.ryuqqq.platform.common.scheduler.SchedulerBatchProcessingResult;
import com.ryuqqq.platform.outbox.BatchOutboxRelayTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Outbox 자동 설정 — 배치 트랜스포트 {@link BatchOutboxRelayTemplate} 빈을 등록한다.
 *
 * <p>{@link MeterRegistry} 는 optional. 건별 트랜스포트({@code PerItemOutboxRelayTemplate})는 executor·
 * defer 윈도가 소비자별이라 소비측이 직접 인스턴스화한다 — 여기서 등록하지 않는다.
 */
@AutoConfiguration
@ConditionalOnClass(SchedulerBatchProcessingResult.class)
public class PlatformOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BatchOutboxRelayTemplate batchOutboxRelayTemplate(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new BatchOutboxRelayTemplate(meterRegistryProvider.getIfAvailable());
    }
}
