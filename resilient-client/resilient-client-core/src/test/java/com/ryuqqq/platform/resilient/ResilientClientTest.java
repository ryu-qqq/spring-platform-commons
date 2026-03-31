package com.ryuqqq.platform.resilient;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ryuqqq.platform.resilient.exception.BadRequestException;
import com.ryuqqq.platform.resilient.exception.CircuitOpenException;
import com.ryuqqq.platform.resilient.exception.NetworkException;
import com.ryuqqq.platform.resilient.exception.ServerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientClientTest {

    private static final byte[] EMPTY = new byte[0];
    private static final byte[] JSON_BODY = """
        {"name":"test","value":42}""".getBytes();

    @Nested
    @DisplayName("정상 요청")
    class SuccessPath {

        @Test
        @DisplayName("2xx 응답 → 정상 반환")
        void executeVoidSuccess() {
            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> new RawResponse(200, Map.of(), EMPTY))
                .build();

            client.executeVoid(ExternalRequest.post("http://example.com", "body"));
        }

        @Test
        @DisplayName("JSON 응답을 지정 타입으로 역직렬화")
        void executeWithDeserialization() {
            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> new RawResponse(200, Map.of(), JSON_BODY))
                .build();

            TestDto result = client.execute(
                ExternalRequest.get("http://example.com"), TestDto.class);

            assertThat(result.name()).isEqualTo("test");
            assertThat(result.value()).isEqualTo(42);
        }

        @Test
        @DisplayName("RawResponse 타입으로 반환 시 역직렬화 없이 반환")
        void executeReturnsRawResponse() {
            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> new RawResponse(200, Map.of(), JSON_BODY))
                .build();

            RawResponse result = client.execute(
                ExternalRequest.get("http://example.com"), RawResponse.class);

            assertThat(result.statusCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("예외 분류")
    class ExceptionClassification {

        @Test
        @DisplayName("5xx → ServerException")
        void serverError() {
            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .retry(r -> r.maxAttempts(1))
                .build();

            assertThatThrownBy(() -> client.executeVoid(ExternalRequest.get("http://example.com")))
                .isInstanceOf(ServerException.class);
        }

        @Test
        @DisplayName("400 → BadRequestException (Retry 없이 즉시 실패)")
        void badRequest() {
            AtomicInteger callCount = new AtomicInteger();

            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> {
                    callCount.incrementAndGet();
                    return new RawResponse(400, Map.of(), "bad".getBytes());
                })
                .retry(r -> r.maxAttempts(3))
                .build();

            assertThatThrownBy(() -> client.executeVoid(ExternalRequest.get("http://example.com")))
                .isInstanceOf(BadRequestException.class);

            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("네트워크 오류 → NetworkException")
        void networkError() {
            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> { throw new ConnectException("Connection refused"); })
                .retry(r -> r.maxAttempts(1))
                .build();

            assertThatThrownBy(() -> client.executeVoid(ExternalRequest.get("http://example.com")))
                .isInstanceOf(NetworkException.class);
        }
    }

    @Nested
    @DisplayName("Retry")
    class RetryBehavior {

        @Test
        @DisplayName("5xx 실패 후 Retry 성공")
        void retryOnServerError() {
            AtomicInteger callCount = new AtomicInteger();

            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> {
                    if (callCount.incrementAndGet() <= 2) {
                        return new RawResponse(500, Map.of(), "error".getBytes());
                    }
                    return new RawResponse(200, Map.of(), EMPTY);
                })
                .retry(r -> r
                    .maxAttempts(3)
                    .initialBackoff(Duration.ofMillis(10)))
                .build();

            client.executeVoid(ExternalRequest.get("http://example.com"));
            assertThat(callCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("maxAttempts 초과 시 최종 실패")
        void retryExhausted() {
            AtomicInteger callCount = new AtomicInteger();

            ResilientClient client = ResilientClient.builder()
                .name("test")
                .sender(req -> {
                    callCount.incrementAndGet();
                    return new RawResponse(500, Map.of(), "error".getBytes());
                })
                .retry(r -> r
                    .maxAttempts(3)
                    .initialBackoff(Duration.ofMillis(10)))
                .build();

            assertThatThrownBy(() -> client.executeVoid(ExternalRequest.get("http://example.com")))
                .isInstanceOf(ServerException.class);

            assertThat(callCount.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("CircuitBreaker")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("실패율 초과 시 CB OPEN → CircuitOpenException")
        void circuitBreakerOpens() {
            AtomicInteger callCount = new AtomicInteger();

            ResilientClient client = ResilientClient.builder()
                .name("cb-test")
                .sender(req -> {
                    callCount.incrementAndGet();
                    return new RawResponse(500, Map.of(), "error".getBytes());
                })
                .circuitBreaker(cb -> cb
                    .failureRateThreshold(50)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofSeconds(60)))
                .retry(r -> r.maxAttempts(1))
                .build();

            // slidingWindowSize(4) + minimumNumberOfCalls(4): 4번 호출 후 실패율 계산
            for (int i = 0; i < 4; i++) {
                try {
                    client.executeVoid(ExternalRequest.get("http://example.com"));
                } catch (ServerException ignored) {
                }
            }

            // CB가 OPEN 상태이므로 CircuitOpenException 발생
            assertThatThrownBy(() -> client.executeVoid(ExternalRequest.get("http://example.com")))
                .isInstanceOf(CircuitOpenException.class);
        }
    }

    @Nested
    @DisplayName("빌더 검증")
    class BuilderValidation {

        @Test
        @DisplayName("name 없이 build → NullPointerException")
        void missingName() {
            assertThatThrownBy(() -> ResilientClient.builder()
                .sender(req -> new RawResponse(200, Map.of(), EMPTY))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
        }

        @Test
        @DisplayName("sender 없이 build → NullPointerException")
        void missingSender() {
            assertThatThrownBy(() -> ResilientClient.builder()
                .name("test")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sender");
        }
    }

    record TestDto(String name, int value) {
    }
}
