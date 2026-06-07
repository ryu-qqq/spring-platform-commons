package com.ryuqqq.platform.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway {@code X-Trace-Id}를 MDC에 등록하고 응답 헤더로 echo한다.
 *
 * <p>{@link com.ryuqqq.platform.web.config.PlatformWebAutoConfiguration}가 {@code @Bean}으로 등록한다.
 * Filter 빈은 Spring Boot가 서블릿 필터로 자동 등록하며 {@code @Order}로 순서를 지킨다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId != null && !traceId.isBlank()) {
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
