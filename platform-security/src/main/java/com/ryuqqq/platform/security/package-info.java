/**
 * Platform Security — 내부 서비스 간 servlet 인증 공통 모듈.
 *
 * <p>{@code X-Service-Token} 기반 {@link
 * com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter} 를 설정 가능한 superset 으로
 * 제공한다. 헤더모델·role·principal·enabled·경로스코프는 {@code security.service-token.*} 로 설정한다.
 *
 * <p>{@code SecurityFilterChain} 은 소비측이 소유하며, {@link
 * com.ryuqqq.platform.security.config.ServiceTokenSecurity#applyDefaults} 로 공통부(csrf disable·
 * stateless·필터 등록·예외처리)를 적용한다.
 */
package com.ryuqqq.platform.security;
