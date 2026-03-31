package com.ryuqqq.platform.resilient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Optional;

import com.ryuqqq.platform.resilient.exception.BadRequestException;
import com.ryuqqq.platform.resilient.exception.ClientException;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;
import com.ryuqqq.platform.resilient.exception.NetworkException;
import com.ryuqqq.platform.resilient.exception.ServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResponseClassifierTest {

    private DefaultResponseClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DefaultResponseClassifier();
    }

    @Nested
    @DisplayName("classify - HTTP 응답 분류")
    class ClassifyResponse {

        @Test
        @DisplayName("200 → 정상 (empty)")
        void success200() {
            RawResponse response = new RawResponse(200, Map.of(), new byte[0]);
            assertThat(classifier.classify(response)).isEmpty();
        }

        @Test
        @DisplayName("201 → 정상 (empty)")
        void success201() {
            RawResponse response = new RawResponse(201, Map.of(), new byte[0]);
            assertThat(classifier.classify(response)).isEmpty();
        }

        @Test
        @DisplayName("400 → BadRequestException (retriable=false, recordFailure=false)")
        void badRequest400() {
            RawResponse response = new RawResponse(400, Map.of(), "invalid".getBytes());
            Optional<ExternalCallException> result = classifier.classify(response);

            assertThat(result).isPresent();
            assertThat(result.get()).isInstanceOf(BadRequestException.class);
            assertThat(result.get().isRetriable()).isFalse();
            assertThat(result.get().shouldRecordFailure()).isFalse();
        }

        @Test
        @DisplayName("404 → ClientException (retriable=false, recordFailure=false)")
        void clientError404() {
            RawResponse response = new RawResponse(404, Map.of(), "not found".getBytes());
            Optional<ExternalCallException> result = classifier.classify(response);

            assertThat(result).isPresent();
            assertThat(result.get()).isInstanceOf(ClientException.class);
            assertThat(result.get().isRetriable()).isFalse();
            assertThat(result.get().shouldRecordFailure()).isFalse();
        }

        @Test
        @DisplayName("500 → ServerException (retriable=true, recordFailure=true)")
        void serverError500() {
            RawResponse response = new RawResponse(500, Map.of(), "error".getBytes());
            Optional<ExternalCallException> result = classifier.classify(response);

            assertThat(result).isPresent();
            assertThat(result.get()).isInstanceOf(ServerException.class);
            assertThat(result.get().isRetriable()).isTrue();
            assertThat(result.get().shouldRecordFailure()).isTrue();
        }

        @Test
        @DisplayName("503 → ServerException")
        void serverError503() {
            RawResponse response = new RawResponse(503, Map.of(), "unavailable".getBytes());
            Optional<ExternalCallException> result = classifier.classify(response);

            assertThat(result).isPresent();
            assertThat(result.get()).isInstanceOf(ServerException.class);
        }
    }

    @Nested
    @DisplayName("classifyException - 전송 예외 분류")
    class ClassifyException {

        @Test
        @DisplayName("SocketTimeoutException → NetworkException")
        void socketTimeout() {
            ExternalCallException result = classifier.classifyException(
                new SocketTimeoutException("Read timed out"));

            assertThat(result).isInstanceOf(NetworkException.class);
            assertThat(result.isRetriable()).isTrue();
            assertThat(result.shouldRecordFailure()).isTrue();
        }

        @Test
        @DisplayName("ConnectException → NetworkException")
        void connectException() {
            ExternalCallException result = classifier.classifyException(
                new ConnectException("Connection refused"));

            assertThat(result).isInstanceOf(NetworkException.class);
        }

        @Test
        @DisplayName("래핑된 SocketTimeoutException → NetworkException (root cause 추적)")
        void wrappedSocketTimeout() {
            Exception wrapped = new RuntimeException("wrapper",
                new SocketTimeoutException("Read timed out"));

            ExternalCallException result = classifier.classifyException(wrapped);
            assertThat(result).isInstanceOf(NetworkException.class);
        }

        @Test
        @DisplayName("이미 ExternalCallException이면 그대로 반환")
        void alreadyClassified() {
            ServerException original = new ServerException(500, "already classified");
            ExternalCallException result = classifier.classifyException(original);
            assertThat(result).isSameAs(original);
        }
    }
}
