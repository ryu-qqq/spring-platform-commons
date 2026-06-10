package com.ryuqqq.platform.resilient.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ryuqqq.platform.resilient.ExternalRequest;
import com.ryuqqq.platform.resilient.HttpMethod;
import com.ryuqqq.platform.resilient.RawResponse;
import com.ryuqqq.platform.resilient.RequestSender;
import com.ryuqqq.platform.resilient.spring.ResilientClientProperties.TimeoutProperties;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResilientClientRestSupportTest {

    @Test
    @DisplayName("buildRestClient는 baseUrl과 timeout으로 RestClient를 생성한다")
    void buildRestClient() {
        TimeoutProperties timeout = new TimeoutProperties();
        timeout.setConnect(Duration.ofSeconds(2));
        timeout.setRead(Duration.ofSeconds(7));

        RestClient client = ResilientClientRestSupport.buildRestClient("http://example.test", timeout);

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("buildRestClient(base)는 전달받은 base 빌더의 인터셉터(트레이싱 등)를 보존한다")
    void buildRestClientPreservesBaseBuilderInterceptors() {
        // 트레이싱 인터셉터를 흉내내는 인터셉터 — 호출되면 마크하고 즉시 200 응답(네트워크 X)
        AtomicBoolean baseInterceptorRan = new AtomicBoolean(false);
        RestClient.Builder base =
                RestClient.builder()
                        .requestInterceptor(
                                (request, body, execution) -> {
                                    baseInterceptorRan.set(true);
                                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                                });
        TimeoutProperties timeout = new TimeoutProperties();

        RestClient client =
                ResilientClientRestSupport.buildRestClient(
                        base, "http://example.test", timeout, Map.of());
        client.get().uri("/probe").retrieve().toBodilessEntity();

        assertThat(baseInterceptorRan)
                .as("base 빌더 인터셉터가 보존되어 호출됨(트레이스 전파 가능)")
                .isTrue();
    }

    @Test
    @DisplayName("requestSender는 RestClient exchange 결과를 RawResponse로 변환한다")
    void requestSender() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://example.test/ping"))
                .andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));

        RequestSender sender = ResilientClientRestSupport.requestSender(builder.build());

        RawResponse response =
                sender.send(new ExternalRequest("/ping", HttpMethod.GET, Map.of(), null));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).isEqualTo("hello");
        server.verify();
    }
}
