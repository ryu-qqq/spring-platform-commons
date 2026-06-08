package com.ryuqqq.platform.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxStatusTest {

    @Test
    @DisplayName("outbox 수명주기 4상태를 가진다")
    void hasFourStates() {
        assertThat(OutboxStatus.values())
                .containsExactly(
                        OutboxStatus.PENDING,
                        OutboxStatus.PROCESSING,
                        OutboxStatus.SENT,
                        OutboxStatus.FAILED);
    }
}
