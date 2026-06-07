package com.ryuqqq.platform.web.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import com.ryuqqq.platform.web.error.ErrorMapper;
import com.ryuqqq.platform.web.error.ErrorMapperRegistry;
import com.ryuqqq.platform.web.error.GlobalExceptionHandler;
import com.ryuqqq.platform.web.filter.RequestContextFilter;

/**
 * {@code com.ryuqqq.platform.web} 자동 설정. 서블릿 웹 애플리케이션이고 platform-web가 클래스패스에 있으면
 * 표준 응답/예외/추적 빈(ErrorMapperRegistry · GlobalExceptionHandler · RequestContextFilter)을 등록한다.
 *
 * <p>이전의 {@code @ComponentScan} 방식 대신 {@code @AutoConfiguration} + 명시적 {@code @Bean}으로 전환했다
 * (라이브러리 모범 — 소비측 패키지 스캔에 의존하지 않음). 소비측은 동일 타입 빈을 정의해 override할 수 있다
 * ({@link ConditionalOnMissingBean}).
 *
 * <p>{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}로 등록되어
 * platform-web 의존만 추가하면 zero-config로 활성화된다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrorMapperRegistry errorMapperRegistry(List<ErrorMapper> mappers) {
        return new ErrorMapperRegistry(mappers);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(ErrorMapperRegistry errorMapperRegistry) {
        return new GlobalExceptionHandler(errorMapperRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestContextFilter requestContextFilter() {
        return new RequestContextFilter();
    }
}
