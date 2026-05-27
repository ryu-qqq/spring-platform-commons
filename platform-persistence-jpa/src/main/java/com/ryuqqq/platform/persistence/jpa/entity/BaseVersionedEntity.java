package com.ryuqqq.platform.persistence.jpa.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * JPA base with optimistic locking ({@code @Version}).
 *
 * <p>Aligns with domain {@code Versioned} — mapper maps {@link #getVersion()} to {@code version()}.
 * Use for competing row updates (Outbox, inventory) without soft delete. HTTP 409 mapping is
 * {@code platform-web} (Phase 2 follow-up).
 */
@MappedSuperclass
public abstract class BaseVersionedEntity extends BaseAuditEntity {

    @Version
    private Long version;

    protected BaseVersionedEntity() {
        super();
    }

    protected BaseVersionedEntity(Instant createdAt, Instant updatedAt, Long version) {
        super(createdAt, updatedAt);
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
}
