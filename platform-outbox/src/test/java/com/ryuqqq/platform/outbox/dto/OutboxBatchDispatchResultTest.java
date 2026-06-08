package com.ryuqqq.platform.outbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxBatchDispatchResultTest {

    @Test
    @DisplayName("allSuccess: 실패 없음, hasFailures=false")
    void allSuccess() {
        OutboxBatchDispatchResult result = OutboxBatchDispatchResult.allSuccess(List.of("a", "b"));

        assertThat(result.successIds()).containsExactly("a", "b");
        assertThat(result.failedEntries()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("of: 성공/실패 혼재, hasFailures=true")
    void ofWithFailures() {
        OutboxBatchDispatchResult result =
                OutboxBatchDispatchResult.of(
                        List.of("a"),
                        List.of(new OutboxBatchDispatchResult.FailedEntry("b", "timeout")));

        assertThat(result.successIds()).containsExactly("a");
        assertThat(result.failedEntries()).hasSize(1);
        assertThat(result.failedEntries().get(0).id()).isEqualTo("b");
        assertThat(result.failedEntries().get(0).errorMessage()).isEqualTo("timeout");
        assertThat(result.hasFailures()).isTrue();
    }

    @Test
    @DisplayName("null 입력은 빈 리스트로 정규화된다")
    void nullInputsNormalizedToEmpty() {
        OutboxBatchDispatchResult result = OutboxBatchDispatchResult.of(null, null);

        assertThat(result.successIds()).isEmpty();
        assertThat(result.failedEntries()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
    }
}
