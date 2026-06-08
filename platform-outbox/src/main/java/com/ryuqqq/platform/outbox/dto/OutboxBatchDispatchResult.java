package com.ryuqqq.platform.outbox.dto;

import java.util.List;

/** 배치 dispatch 결과 — 건별 성공/실패를 추적한다. */
public record OutboxBatchDispatchResult(List<String> successIds, List<FailedEntry> failedEntries) {

    /** null 입력은 빈 리스트로 정규화하고 방어적 복사로 불변성을 보장한다. */
    public OutboxBatchDispatchResult {
        successIds = (successIds == null) ? List.of() : List.copyOf(successIds);
        failedEntries = (failedEntries == null) ? List.of() : List.copyOf(failedEntries);
    }

    /** 발행 실패 항목 — business id 와 에러 메시지. */
    public record FailedEntry(String id, String errorMessage) {}

    public static OutboxBatchDispatchResult allSuccess(List<String> ids) {
        return new OutboxBatchDispatchResult(ids, List.of());
    }

    public static OutboxBatchDispatchResult of(
            List<String> successIds, List<FailedEntry> failedEntries) {
        return new OutboxBatchDispatchResult(successIds, failedEntries);
    }

    public boolean hasFailures() {
        return !failedEntries.isEmpty();
    }
}
