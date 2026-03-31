package com.ryuqqq.platform.resilient.metrics;

import com.ryuqqq.platform.resilient.HttpMethod;
import com.ryuqqq.platform.resilient.exception.ServerException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientClientMetricsBinderTest {

    private SimpleMeterRegistry registry;
    private ResilientClientMetricsBinder binder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        binder = new ResilientClientMetricsBinder(registry);
    }

    @Test
    @DisplayName("성공 시 duration, total, retry_total 메트릭 기록")
    void recordSuccess() {
        binder.recordSuccess("callback", HttpMethod.POST, 1_000_000L, false);

        Timer timer = registry.find("resilient_client_duration_seconds")
            .tag("name", "callback")
            .tag("outcome", "success")
            .tag("method", "POST")
            .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter total = registry.find("resilient_client_total")
            .tag("outcome", "success")
            .counter();
        assertThat(total).isNotNull();
        assertThat(total.count()).isEqualTo(1);

        Counter retry = registry.find("resilient_client_retry_total")
            .tag("result", "successful_without_retry")
            .counter();
        assertThat(retry).isNotNull();
        assertThat(retry.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Retry 후 성공 시 result=successful_with_retry")
    void recordSuccessWithRetry() {
        binder.recordSuccess("callback", HttpMethod.POST, 2_000_000L, true);

        Counter retry = registry.find("resilient_client_retry_total")
            .tag("result", "successful_with_retry")
            .counter();
        assertThat(retry).isNotNull();
        assertThat(retry.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 시 duration, total, errors_total, retry_total 메트릭 기록")
    void recordFailure() {
        binder.recordFailure("callback", HttpMethod.POST, 3_000_000L,
            new ServerException(500, "error"), true);

        Timer timer = registry.find("resilient_client_duration_seconds")
            .tag("outcome", "error")
            .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter errors = registry.find("resilient_client_errors_total")
            .tag("exception", "ServerException")
            .counter();
        assertThat(errors).isNotNull();
        assertThat(errors.count()).isEqualTo(1);

        Counter retry = registry.find("resilient_client_retry_total")
            .tag("result", "failed")
            .counter();
        assertThat(retry).isNotNull();
        assertThat(retry.count()).isEqualTo(1);
    }
}
