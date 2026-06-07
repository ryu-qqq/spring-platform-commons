package com.ryuqqq.platform.security.config;

import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Service Token 인증을 위한 {@code HttpSecurity} 공통 설정 헬퍼.
 *
 * <p>소비측은 자기 {@code @Bean SecurityFilterChain} 안에서 {@link #applyDefaults} 로 공통부(csrf
 * disable·stateless·필터 등록·예외처리)를 적용한 뒤, 자신의 {@code authorizeHttpRequests} 경로 규칙과
 * 부가 설정(cors·추가 필터)을 더한다. 모듈이 {@code SecurityFilterChain} 을 직접 등록하지 않으므로 빈
 * 충돌이 없고 소비측이 체인을 완전히 통제한다.
 */
public final class ServiceTokenSecurity {

    private ServiceTokenSecurity() {}

    /**
     * csrf disable · stateless 세션 · ServiceToken 필터 등록 ·
     * ProblemDetail entrypoint/accessDeniedHandler 예외처리를 {@code http} 에 적용한다.
     *
     * <p>{@code authorizeHttpRequests} 는 적용하지 않는다 — 경로 인가 규칙은 소비측이 정의한다.
     */
    public static void applyDefaults(
            HttpSecurity http,
            ServiceTokenAuthenticationFilter filter,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler));
    }
}
