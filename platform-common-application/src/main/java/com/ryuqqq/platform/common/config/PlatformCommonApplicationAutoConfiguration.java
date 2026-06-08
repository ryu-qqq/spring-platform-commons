package com.ryuqqq.platform.common.config;

import com.ryuqqq.platform.common.factory.CommonVoFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Platform Common Application 자동 설정 — 무상태 공통 헬퍼 빈을 zero-config 로 등록한다.
 *
 * <p>{@link CommonVoFactory} 를 컴포넌트 스캔 의존 없이 제공해, 소비측 스캔 범위와 무관하게 주입되게 한다.
 * 소비측이 동일 타입 빈을 정의하면 {@link ConditionalOnMissingBean} 으로 양보한다.
 */
@AutoConfiguration
public class PlatformCommonApplicationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommonVoFactory commonVoFactory() {
        return new CommonVoFactory();
    }
}
