package com.ryuqqq.platform.resilient;

import java.util.Map;

/**
 * HTTP 응답 원시 데이터.
 *
 * @param statusCode 응답 상태 코드
 * @param headers    응답 헤더
 * @param body       응답 본문 (바이트 배열)
 */
public record RawResponse(
    int statusCode,
    Map<String, String> headers,
    byte[] body
) {

    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean is4xx() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean is5xx() {
        return statusCode >= 500;
    }
}
