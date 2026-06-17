package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.observability.MdcKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * 보안 필터체인 예외(401/403)를 RFC 7807 {@link ProblemDetail} 로 직렬화한다.
 *
 * <p>platform-web {@code GlobalExceptionHandler} 와 동일한 포맷(title·type·instance·
 * timestamp·code·MDC traceId/spanId·{@code x-error-code} 헤더)을 사용한다. 필터체인 예외는
 * {@code @RestControllerAdvice} 를 타지 않으므로 여기서 직접 응답을 작성한다.
 */
final class ServiceTokenProblemDetailWriter {

    private final ObjectMapper objectMapper;

    ServiceTokenProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request)
            throws IOException {

        // 이미 커밋된 응답에 쓰면 IllegalStateException — 조기 리턴.
        if (response.isCommitted()) {
            return;
        }

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));

        String uri = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uri = uri + "?" + request.getQueryString();
        }
        // uri/queryString 은 raw 클라이언트 입력 — 불정 URI 로 인한 500 방지.
        try {
            pd.setInstance(URI.create(uri));
        } catch (IllegalArgumentException ex) {
            pd.setInstance(URI.create("/"));
        }

        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("code", code);

        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        if (traceId != null) {
            pd.setProperty(MdcKeys.TRACE_ID, traceId);
        }
        if (spanId != null) {
            pd.setProperty(MdcKeys.SPAN_ID, spanId);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-error-code", code);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
