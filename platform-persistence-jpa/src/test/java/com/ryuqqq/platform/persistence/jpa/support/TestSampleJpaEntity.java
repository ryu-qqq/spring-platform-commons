package com.ryuqqq.platform.persistence.jpa.support;

import com.ryuqqq.platform.persistence.jpa.entity.BaseVersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Test-only sample entity extending {@link BaseVersionedEntity}. */
@Entity
@Table(name = "test_samples")
public class TestSampleJpaEntity extends BaseVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected TestSampleJpaEntity() {
        super();
    }

    private TestSampleJpaEntity(Long id, String name, Instant createdAt, Instant updatedAt, Long version) {
        super(createdAt, updatedAt, version);
        this.id = id;
        this.name = name;
    }

    public static TestSampleJpaEntity create(String name) {
        return new TestSampleJpaEntity(null, name, null, null, null);
    }

    public static TestSampleJpaEntity reconstitute(
            Long id, String name, Instant createdAt, Instant updatedAt, Long version) {
        return new TestSampleJpaEntity(id, name, createdAt, updatedAt, version);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
