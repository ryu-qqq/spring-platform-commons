package com.ryuqqq.platform.common.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * Soft delete 상태. Aggregate의 {@code delete(now)} / {@code restore()}와 persistence 필터가 공유한다. 삭제 여부는
 * {@code deletedAt != null}로 파생한다(ADR-0007: boolean 필드 제거).
 *
 * @param deletedAt 삭제 시각 (active이면 null)
 */
public record DeletionStatus(Instant deletedAt) {

    public static DeletionStatus active() {
        return new DeletionStatus(null);
    }

    public static DeletionStatus deletedAt(Instant deletedAt) {
        return new DeletionStatus(Objects.requireNonNull(deletedAt, "deletedAt must not be null"));
    }

    public DeletionStatus markDeleted(Instant deletedAt) {
        return deletedAt(deletedAt);
    }

    public DeletionStatus restore() {
        return active();
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
