package com.ryuqqq.platform.persistence.jpa.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Audit + soft delete + {@code @Version} — typical domain aggregate JPA base.
 *
 * <p>Combines {@link BaseSoftDeleteEntity} and {@link BaseVersionedEntity} for Product-like roots.
 */
@MappedSuperclass
public abstract class BaseVersionedSoftDeleteEntity extends BaseSoftDeleteEntity {

    @Version
    private Long version;

    protected BaseVersionedSoftDeleteEntity() {
        super();
    }

    protected BaseVersionedSoftDeleteEntity(
            Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt, Long version) {
        super(createdAt, updatedAt, deleted, deletedAt);
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
}
