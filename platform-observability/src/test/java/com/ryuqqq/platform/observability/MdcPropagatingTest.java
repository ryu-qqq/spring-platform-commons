package com.ryuqqq.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcPropagatingTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("wrap(Runnable): 제출 스레드 MDC가 워커 스레드에 전파된다")
    void runnablePropagates() throws Exception {
        MDC.put("traceId", "T-123");
        AtomicReference<String> seen = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(MdcPropagating.wrap(() -> seen.set(MDC.get("traceId")))).get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(seen.get()).isEqualTo("T-123");
    }

    @Test
    @DisplayName("wrap(Runnable): 워커의 기존 MDC가 작업 후 원복된다(누수 없음)")
    void runnableRestoresWorkerContext() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> MDC.put("traceId", "WORKER-OWN")).get();

            MDC.put("traceId", "SUBMITTER");
            pool.submit(MdcPropagating.wrap(() -> {})).get();

            AtomicReference<String> after = new AtomicReference<>();
            pool.submit(() -> after.set(MDC.get("traceId"))).get();
            assertThat(after.get()).isEqualTo("WORKER-OWN");
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("wrap(Runnable): 빈 MDC(캡처 null)도 예외 없이 전파")
    void runnableEmptyContext() throws Exception {
        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("INIT");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(MdcPropagating.wrap(() -> seen.set(MDC.get("traceId")))).get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("wrap(Runnable): 작업이 예외를 던져도 제출 스레드 MDC는 보존")
    void runnableRestoresOnException() {
        MDC.put("traceId", "T-1");
        Runnable wrapped = MdcPropagating.wrap(() -> {
            throw new RuntimeException("boom");
        });
        try {
            wrapped.run();
        } catch (RuntimeException ignored) {
            // expected
        }
        assertThat(MDC.get("traceId")).isEqualTo("T-1");
    }
}
