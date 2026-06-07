package com.ryuqqq.platform.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Platform Security 자동 설정 — Service Token 인증 컴포넌트를 등록한다.
 *
 * <p>{@code SecurityFilterChain} 은 등록하지 않는다 — 소비측이 자기 체인을 소유하고, {@link
 * ServiceTokenSecurity#applyDefaults} 로 공통부를 적용한 뒤 본 자동설정의 필터·entrypoint·handler 빈을
 * 주입한다. 소비측이 동일 타입 빈을 정의하면 {@link ConditionalOnMissingBean} 으로 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(
            ServiceTokenProperties properties) {
        return new ServiceTokenAuthenticationFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAuthenticationEntryPoint serviceTokenAuthenticationEntryPoint(
            ObjectMapper objectMapper) {
        return new ServiceTokenAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAccessDeniedHandler serviceTokenAccessDeniedHandler(
            ObjectMapper objectMapper) {
        return new ServiceTokenAccessDeniedHandler(objectMapper);
    }
}
