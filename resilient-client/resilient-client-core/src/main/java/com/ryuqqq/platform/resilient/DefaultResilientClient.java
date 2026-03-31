package com.ryuqqq.platform.resilient;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.resilient.config.CircuitBreakerConfig;
import com.ryuqqq.platform.resilient.config.RetryConfig;
import com.ryuqqq.platform.resilient.exception.CircuitOpenException;
import com.ryuqqq.platform.resilient.exception.ExternalCallException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ResilientClient}의 기본 구현체.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>메트릭 Timer 시작</li>
 *   <li>CircuitBreaker 체크 (OPEN → 즉시 실패)</li>
 *   <li>Retry 래핑 (exponential backoff)</li>
 *   <li>RequestSender.send() 호출</li>
 *   <li>ResponseClassifier로 응답 분류</li>
 *   <li>CB에 결과 기록</li>
 *   <li>메트릭 기록</li>
 *   <li>결과 반환 또는 예외 전파</li>
 * </ol>
 */
class DefaultResilientClient implements ResilientClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultResilientClient.class);

    private final String name;
    private final RequestSender sender;
    private final ResponseClassifier classifier;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final MetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;

    DefaultResilientClient(String name,
                           RequestSender sender,
                           CircuitBreakerConfig cbConfig,
                           RetryConfig retryConfig,
                           ResponseClassifier classifier,
                           MetricsRecorder metricsRecorder) {
        this.name = name;
        this.sender = sender;
        this.classifier = classifier;
        this.circuitBreaker = CircuitBreaker.of(name, cbConfig.toResilience4j());
        this.retry = Retry.of(name, retryConfig.toResilience4j());
        this.metricsRecorder = metricsRecorder;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.retry.getEventPublisher()
            .onRetry(event -> log.warn("[{}] Retry attempt #{}: {}",
                name, event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));

        metricsRecorder.bindCircuitBreaker(name, this.circuitBreaker);
    }

    @Override
    public <T> T execute(ExternalRequest request, Class<T> responseType) {
        RawResponse raw = doExecute(request);
        return deserialize(raw, responseType);
    }

    @Override
    public void executeVoid(ExternalRequest request) {
        doExecute(request);
    }

    private RawResponse doExecute(ExternalRequest request) {
        log.debug("[{}] {} {}", name, request.method(), request.url());

        long startNanos = System.nanoTime();
        AtomicInteger attemptCount = new AtomicInteger(0);

        try {
            RawResponse result = Retry.decorateCheckedSupplier(retry,
                CircuitBreaker.decorateCheckedSupplier(circuitBreaker,
                    () -> {
                        attemptCount.incrementAndGet();
                        return sendAndClassify(request);
                    })
            ).get();

            long durationNanos = System.nanoTime() - startNanos;
            boolean retried = attemptCount.get() > 1;
            metricsRecorder.recordSuccess(name, request.method(), durationNanos, retried);

            return result;
        } catch (CallNotPermittedException e) {
            long durationNanos = System.nanoTime() - startNanos;
            CircuitOpenException ex = new CircuitOpenException(
                "[" + name + "] CircuitBreaker is OPEN: " + e.getMessage(), e);
            metricsRecorder.recordFailure(name, request.method(), durationNanos, ex, false);
            throw ex;
        } catch (ExternalCallException e) {
            long durationNanos = System.nanoTime() - startNanos;
            boolean retried = attemptCount.get() > 1;
            metricsRecorder.recordFailure(name, request.method(), durationNanos, e, retried);
            throw e;
        } catch (Throwable e) {
            long durationNanos = System.nanoTime() - startNanos;
            ExternalCallException classified = classifier.classifyException(
                e instanceof Exception ex ? ex : new RuntimeException(e));
            boolean retried = attemptCount.get() > 1;
            metricsRecorder.recordFailure(name, request.method(), durationNanos, classified, retried);
            throw classified;
        }
    }

    private RawResponse sendAndClassify(ExternalRequest request) throws Exception {
        RawResponse response;
        try {
            response = sender.send(request);
        } catch (Exception e) {
            throw classifier.classifyException(e);
        }

        Optional<ExternalCallException> error = classifier.classify(response);
        if (error.isPresent()) {
            log.warn("[{}] {} {} → {} ({})", name, request.method(), request.url(),
                response.statusCode(), error.get().getClass().getSimpleName());
            throw error.get();
        }

        log.debug("[{}] {} {} → {}", name, request.method(), request.url(), response.statusCode());
        return response;
    }

    private <T> T deserialize(RawResponse raw, Class<T> responseType) {
        if (responseType == RawResponse.class) {
            @SuppressWarnings("unchecked")
            T result = (T) raw;
            return result;
        }
        if (responseType == byte[].class) {
            @SuppressWarnings("unchecked")
            T result = (T) raw.body();
            return result;
        }
        if (responseType == String.class) {
            @SuppressWarnings("unchecked")
            T result = (T) new String(raw.body(), java.nio.charset.StandardCharsets.UTF_8);
            return result;
        }
        if (raw.body() == null || raw.body().length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(raw.body(), responseType);
        } catch (Exception e) {
            throw new IllegalStateException(
                "[" + name + "] 응답 역직렬화 실패: " + e.getMessage(), e);
        }
    }
}
