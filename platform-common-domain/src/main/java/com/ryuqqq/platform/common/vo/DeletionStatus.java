package com.ryuqqq.platform.common.vo;

import java.time.Instant;

/**
 * Soft delete 상태. Aggregate의 {@code delete(now)} / {@code restore()}와 persistence 필터가 공유한다.
 *
 * @param deleted 삭제 여부
 * @param deletedAt 삭제 시각 (active이면 null)
 */
public record DeletionStatus(boolean deleted, Instant deletedAt) {

    public DeletionStatus {
        if (deleted && deletedAt == null) {
            throw new IllegalArgumentException("deletedAt must be present when deleted");
        }
        if (!deleted && deletedAt != null) {
            throw new IllegalArgumentException("deletedAt must be null when active");
        }
    }

    public static DeletionStatus active() {
        return new DeletionStatus(false, null);
    }

    public static DeletionStatus deleted(Instant deletedAt) {
        return new DeletionStatus(true, deletedAt);
    }

    public DeletionStatus markDeleted(Instant deletedAt) {
        return deleted(deletedAt);
    }

    public DeletionStatus restore() {
        return active();
    }

    public boolean isActive() {
        return !deleted;
    }
}
