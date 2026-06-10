package com.ryuqqq.platform.resilient.spring;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.ryuqqq.platform.resilient.ExternalRequest;
import com.ryuqqq.platform.resilient.HttpMethod;
import com.ryuqqq.platform.resilient.RawResponse;
import com.ryuqqq.platform.resilient.RequestSender;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.TimeoutProperties;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient} + {@link RequestSender} helpers for adapter-out HTTP clients.
 *
 * <p>Connect/read timeout is applied at the HTTP transport layer; CB/retry remain in {@link
 * com.ryuqqq.platform.resilient.ResilientClient}.
 */
public final class ResilientClientRestSupport {

    private ResilientClientRestSupport() {}

    /**
     * 트레이스 전파 없는 편의 오버로드 — 새 {@link RestClient#builder()}로 생성한다.
     *
     * <p>W3C traceparent 자동 전파가 필요하면 auto-configured {@link RestClient.Builder}를 받는
     * {@link #buildRestClient(RestClient.Builder, String, TimeoutProperties, Map)}를 사용하라.
     */
    public static RestClient buildRestClient(String baseUrl, TimeoutProperties timeout) {
        return buildRestClient(RestClient.builder(), baseUrl, timeout, Map.of());
    }

    /** 트레이스 전파 없는 편의 오버로드. {@link #buildRestClient(RestClient.Builder, String, TimeoutProperties, Map)} 참고. */
    public static RestClient buildRestClient(
            String baseUrl, TimeoutProperties timeout, Map<String, String> defaultHeaders) {
        return buildRestClient(RestClient.builder(), baseUrl, timeout, defaultHeaders);
    }

    /**
     * auto-configured {@link RestClient.Builder}(트레이싱 인터셉터 주입됨)를 base로 받아
     * transport 타임아웃·기본 헤더만 덧대 {@link RestClient}를 만든다.
     *
     * <p>base 빌더를 {@code clone()}해 변형하므로 원본(공유/프로토타입 빈)을 오염시키지 않으며,
     * base에 실린 observation 인터셉터가 보존되어 W3C traceparent 전파가 동작한다.
     * connect/read 타임아웃(resilience)은 그대로 transport 레이어에 적용된다.
     */
    public static RestClient buildRestClient(
            RestClient.Builder base,
            String baseUrl,
            TimeoutProperties timeout,
            Map<String, String> defaultHeaders) {
        TimeoutProperties effective = timeout != null ? timeout : new TimeoutProperties();

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(effective.getConnect())
                        .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(effective.getRead());

        RestClient.Builder builder = base.clone().baseUrl(baseUrl).requestFactory(factory);

        if (defaultHeaders != null) {
            defaultHeaders.forEach(builder::defaultHeader);
        }
        builder.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        return builder.build();
    }

    public static RequestSender requestSender(RestClient restClient) {
        return request -> {
            RestClient.RequestBodySpec spec =
                    restClient
                            .method(org.springframework.http.HttpMethod.valueOf(request.method().name()))
                            .uri(request.url());

            request.headers().forEach(spec::header);

            if (request.body() != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(request.body());
            }

            return spec.exchange(
                    (req, res) -> {
                        byte[] body = res.getBody().readAllBytes();
                        return new RawResponse(
                                res.getStatusCode().value(),
                                res.getHeaders().toSingleValueMap(),
                                body != null ? body : new byte[0]);
                    });
        };
    }

    /** Convenience for GET probes without a request body. */
    public static ExternalRequest getRequest(String path) {
        return new ExternalRequest(path, HttpMethod.GET, Map.of(), null);
    }
}
