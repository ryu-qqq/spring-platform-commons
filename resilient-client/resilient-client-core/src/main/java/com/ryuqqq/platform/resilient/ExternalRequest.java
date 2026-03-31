package com.ryuqqq.platform.resilient;

import java.util.Map;

/**
 * 외부 HTTP 요청 정보.
 *
 * @param url     요청 URL
 * @param method  HTTP 메서드
 * @param headers 요청 헤더
 * @param body    요청 본문 (nullable)
 */
public record ExternalRequest(
    String url,
    HttpMethod method,
    Map<String, String> headers,
    Object body
) {

    public static ExternalRequest post(String url, Object body) {
        return new ExternalRequest(url, HttpMethod.POST, Map.of(), body);
    }

    public static ExternalRequest post(String url, Map<String, String> headers, Object body) {
        return new ExternalRequest(url, HttpMethod.POST, headers, body);
    }

    public static ExternalRequest get(String url) {
        return new ExternalRequest(url, HttpMethod.GET, Map.of(), null);
    }

    public static ExternalRequest get(String url, Map<String, String> headers) {
        return new ExternalRequest(url, HttpMethod.GET, headers, null);
    }

    public static ExternalRequest put(String url, Object body) {
        return new ExternalRequest(url, HttpMethod.PUT, Map.of(), body);
    }

    public static ExternalRequest delete(String url) {
        return new ExternalRequest(url, HttpMethod.DELETE, Map.of(), null);
    }
}
