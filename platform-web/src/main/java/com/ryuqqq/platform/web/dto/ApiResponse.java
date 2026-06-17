package com.ryuqqq.platform.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;

import com.ryuqqq.platform.observability.MdcKeys;

/**
 * 표준 API 성공 응답 (API-CTR-004). 실패 응답은 {@link org.springframework.http.ProblemDetail}.
 *
 * @param data 응답 payload
 * @param timestamp ISO-8601 응답 시각
 * @param requestId 요청 추적 ID ({@code MDC traceId} 또는 UUID)
 */
public record ApiResponse<T>(T data, String timestamp, String requestId) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Instant.now().toString(), resolveRequestId());
    }

    public static ApiResponse<Void> ofSuccess() {
        return of(null);
    }

    private static String resolveRequestId() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
