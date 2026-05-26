package com.ryuqqq.platform.resilient;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.ryuqqq.platform.resilient.exception.BadRequestException;
import com.ryuqqq.platform.resilient.exception.CircuitOpenException;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;
import com.ryuqqq.platform.resilient.exception.NetworkException;
import com.ryuqqq.platform.resilient.exception.ServerException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

    @Nested
    @DisplayName("CircuitBreaker 이벤트 로그")
    class CircuitBreakerLogging {

        private Logger sdkLogger;
        private ListAppender<ILoggingEvent> appender;

        @BeforeEach
        void attachAppender() {
            sdkLogger = (Logger) LoggerFactory.getLogger(DefaultResilientClient.class);
            appender = new ListAppender<>();
            appender.start();
            sdkLogger.addAppender(appender);
        }

        @AfterEach
        void detachAppender() {
            sdkLogger.detachAppender(appender);
            appender.stop();
        }

        @Test
        @DisplayName("CB가 OPEN으로 전이되면 state transition INFO 로그를 남긴다")
        void logsStateTransitionToOpen() {
            ResilientClient client = ResilientClient.builder()
                .name("cb-log")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .circuitBreaker(cb -> cb
                    .failureRateThreshold(50)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofSeconds(60)))
                .retry(r -> r.maxAttempts(1))
                .build();

            for (int i = 0; i < 4; i++) {
                try {
                    client.executeVoid(ExternalRequest.get("http://example.com"));
                } catch (ServerException ignored) {
                }
            }

            assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.matches("\\[cb-log\\] CB CLOSED -> OPEN"));
        }

        @Test
        @DisplayName("CB가 OPEN 상태에서 호출되면 call-not-permitted WARN 로그를 남긴다")
        void logsCallNotPermittedAfterOpen() {
            ResilientClient client = ResilientClient.builder()
                .name("cb-log")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .circuitBreaker(cb -> cb
                    .failureRateThreshold(50)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofSeconds(60)))
                .retry(r -> r.maxAttempts(1))
                .build();

            for (int i = 0; i < 4; i++) {
                try {
                    client.executeVoid(ExternalRequest.get("http://example.com"));
                } catch (ServerException ignored) {
                }
            }
            try {
                client.executeVoid(ExternalRequest.get("http://example.com"));
            } catch (CircuitOpenException ignored) {
            }

            assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("[cb-log] CB call not permitted"));
        }
    }

    @Nested
    @DisplayName("Fallback 람다 오버로드")
    class FallbackOverload {

        @Test
        @DisplayName("ServerException 발생 시 fallback이 호출되어 대체 값을 반환한다")
        void fallbackHandlesServerException() {
            ResilientClient client = ResilientClient.builder()
                .name("fallback")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .retry(r -> r.maxAttempts(1))
                .build();

            TestDto result = client.execute(
                ExternalRequest.get("http://example.com"),
                TestDto.class,
                ex -> new TestDto("fallback", -1));

            assertThat(result.name()).isEqualTo("fallback");
            assertThat(result.value()).isEqualTo(-1);
        }

        @Test
        @DisplayName("fallback 람다에서 예외 타입별 분기가 가능하다")
        void fallbackBranchesByExceptionType() {
            ResilientClient client = ResilientClient.builder()
                .name("fallback-branch")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .circuitBreaker(cb -> cb
                    .failureRateThreshold(50)
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(2)
                    .waitDurationInOpenState(Duration.ofSeconds(60)))
                .retry(r -> r.maxAttempts(1))
                .build();

            Function<ExternalCallException, TestDto> fallback = ex -> {
                if (ex instanceof CircuitOpenException) {
                    return new TestDto("circuit-open", 0);
                }
                if (ex instanceof ServerException) {
                    return new TestDto("server-error", 0);
                }
                return new TestDto("other", 0);
            };

            List<TestDto> results = List.of(
                client.execute(ExternalRequest.get("http://example.com"), TestDto.class, fallback),
                client.execute(ExternalRequest.get("http://example.com"), TestDto.class, fallback),
                client.execute(ExternalRequest.get("http://example.com"), TestDto.class, fallback));

            assertThat(results.get(0).name()).isEqualTo("server-error");
            assertThat(results.get(1).name()).isEqualTo("server-error");
            assertThat(results.get(2).name()).isEqualTo("circuit-open");
        }

        @Test
        @DisplayName("fallback이 예외를 다시 던지면 호출자에게 그대로 전파된다")
        void fallbackRethrowsPropagatesToCaller() {
            ResilientClient client = ResilientClient.builder()
                .name("fallback-rethrow")
                .sender(req -> new RawResponse(500, Map.of(), "error".getBytes()))
                .retry(r -> r.maxAttempts(1))
                .build();

            assertThatThrownBy(() -> client.execute(
                ExternalRequest.get("http://example.com"),
                TestDto.class,
                ex -> { throw ex; }))
                .isInstanceOf(ServerException.class);
        }

        @Test
        @DisplayName("정상 응답 시 fallback은 호출되지 않는다")
        void fallbackNotInvokedOnSuccess() {
            AtomicInteger fallbackCount = new AtomicInteger();

            ResilientClient client = ResilientClient.builder()
                .name("fallback-skip")
                .sender(req -> new RawResponse(200, Map.of(), JSON_BODY))
                .build();

            TestDto result = client.execute(
                ExternalRequest.get("http://example.com"),
                TestDto.class,
                ex -> {
                    fallbackCount.incrementAndGet();
                    return new TestDto("never", -1);
                });

            assertThat(result.name()).isEqualTo("test");
            assertThat(fallbackCount.get()).isZero();
        }
    }

    record TestDto(String name, int value) {
    }
}
