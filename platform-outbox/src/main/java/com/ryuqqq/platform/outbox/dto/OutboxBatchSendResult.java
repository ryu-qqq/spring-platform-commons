package com.ryuqqq.platform.outbox.dto;

import java.util.List;

/** 아웃박스 배치 발행 결과 — 건별 성공/실패를 추적한다. */
public record OutboxBatchSendResult(List<String> successIds, List<FailedEntry> failedEntries) {

    /** 발행 실패 항목 — business id 와 에러 메시지. */
    public record FailedEntry(String id, String errorMessage) {}

    public static OutboxBatchSendResult allSuccess(List<String> ids) {
        return new OutboxBatchSendResult(ids, List.of());
    }

    public static OutboxBatchSendResult of(
            List<String> successIds, List<FailedEntry> failedEntries) {
        return new OutboxBatchSendResult(successIds, failedEntries);
    }

    public boolean hasFailures() {
        return !failedEntries.isEmpty();
    }
}
