package com.ryuqqq.platform.persistence.jpa.support;

import com.ryuqqq.platform.persistence.jpa.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Test-only sample entity extending {@link BaseSoftDeleteEntity}. */
@Entity
@Table(name = "test_soft_delete_samples")
public class TestSoftDeleteJpaEntity extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected TestSoftDeleteJpaEntity() {
        super();
    }

    private TestSoftDeleteJpaEntity(
            Long id, String name, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        super(createdAt, updatedAt, deletedAt);
        this.id = id;
        this.name = name;
    }

    public static TestSoftDeleteJpaEntity create(String name) {
        return new TestSoftDeleteJpaEntity(null, name, null, null, null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void softDelete(Instant deletedAt) {
        markDeleted(deletedAt);
    }

    public void undelete() {
        restoreFromSoftDelete();
    }
}
