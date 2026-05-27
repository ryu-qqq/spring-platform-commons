package com.ryuqqq.platform.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * Soft-delete columns ({@code deleted}, {@code deletedAt}) for JPA entities.
 *
 * <p>Maps to domain {@code DeletionStatus}. Per-domain status-enum soft delete remains valid (wiki
 * persistence-mysql § ConditionBuilder).
 */
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseAuditEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BaseSoftDeleteEntity() {
        super();
    }

    protected BaseSoftDeleteEntity(Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
        super(createdAt, updatedAt);
        this.deleted = deleted;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    protected void markDeleted(Instant deletedAt) {
        this.deleted = true;
        this.deletedAt = deletedAt;
    }

    protected void restoreFromSoftDelete() {
        this.deleted = false;
        this.deletedAt = null;
    }
}
