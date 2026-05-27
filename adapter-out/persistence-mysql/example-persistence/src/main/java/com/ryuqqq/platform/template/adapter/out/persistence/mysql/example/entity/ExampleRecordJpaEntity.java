package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.entity;

import com.ryuqqq.platform.persistence.jpa.entity.BaseVersionedSoftDeleteEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Sample JPA entity extending {@link BaseVersionedSoftDeleteEntity}. */
@Entity
@Table(name = "example_record")
public class ExampleRecordJpaEntity extends BaseVersionedSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    protected ExampleRecordJpaEntity() {}

    public ExampleRecordJpaEntity(String label) {
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
