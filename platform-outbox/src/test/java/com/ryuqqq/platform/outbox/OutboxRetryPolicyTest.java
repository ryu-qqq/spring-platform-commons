package com.ryuqqq.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    @DisplayName("defaults: 5회·6시간")
    void defaults() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();

        assertThat(policy.maxRetries()).isEqualTo(5);
        assertThat(policy.maxDeferDuration()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    @DisplayName("of: 소비자 정책 override")
    void custom() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.of(3, Duration.ofMinutes(30));

        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.maxDeferDuration()).isEqualTo(Duration.ofMinutes(30));
    }
}
