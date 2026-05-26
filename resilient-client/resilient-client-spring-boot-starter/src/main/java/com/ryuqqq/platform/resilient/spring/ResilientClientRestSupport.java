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

    public static RestClient buildRestClient(String baseUrl, TimeoutProperties timeout) {
        return buildRestClient(baseUrl, timeout, Map.of());
    }

    public static RestClient buildRestClient(
            String baseUrl, TimeoutProperties timeout, Map<String, String> defaultHeaders) {
        TimeoutProperties effective = timeout != null ? timeout : new TimeoutProperties();

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(effective.getConnect())
                        .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(effective.getRead());

        RestClient.Builder builder =
                RestClient.builder().baseUrl(baseUrl).requestFactory(factory);

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
