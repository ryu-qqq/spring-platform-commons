package com.ryuqqq.platform.web.filter;

import com.ryuqqq.platform.common.observability.MdcKeys;
import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 게이트웨이 전달 헤더에서 표준 trace context(traceId·userId·tenantId)를 MDC에 채운다.
 *
 * <p>traceId는 {@code X-Trace-Id}가 없으면 생성하고 응답 헤더로 echo 한다. 키·헤더 이름은 {@link MdcKeys}
 * SSOT를 따른다. spanId는 추적 계측 소유라 여기서 set 하지 않는다.
 *
 * <p>{@link com.ryuqqq.platform.web.config.PlatformWebAutoConfiguration}가 {@code @Bean}으로 등록한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = headerOrNull(request, MdcKeys.TRACE_ID_HEADER);
            if (traceId == null) {
                // W3C Trace Context/OTel 호환: 32자리 소문자 hex (UUID 16바이트, 하이픈 제거).
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            MDC.put(MdcKeys.TRACE_ID, traceId);
            response.setHeader(MdcKeys.TRACE_ID_HEADER, traceId);

            putIfPresent(request, MdcKeys.USER_ID_HEADER, MdcKeys.USER_ID);
            putIfPresent(request, MdcKeys.TENANT_ID_HEADER, MdcKeys.TENANT_ID);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String headerOrNull(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : null;
    }

    private static void putIfPresent(HttpServletRequest request, String header, String mdcKey) {
        String value = headerOrNull(request, header);
        if (value != null) {
            MDC.put(mdcKey, value);
        }
    }
}
