package com.ryuqqq.platform.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * Soft-delete column ({@code deletedAt}) for JPA entities. 삭제 여부는 {@code deletedAt != null}로 파생한다 —
 * 별도 {@code deleted} boolean 컬럼은 두지 않는다 (ADR-0003 드리프트 표준 수렴).
 *
 * <p>Maps to domain {@code DeletionStatus}. Per-domain status-enum soft delete remains valid (wiki
 * persistence-mysql § ConditionBuilder).
 */
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseAuditEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BaseSoftDeleteEntity() {
        super();
    }

    protected BaseSoftDeleteEntity(Instant createdAt, Instant updatedAt, Instant deletedAt) {
        super(createdAt, updatedAt);
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    protected void markDeleted(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    protected void restoreFromSoftDelete() {
        this.deletedAt = null;
    }
}
