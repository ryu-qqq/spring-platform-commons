package com.ryuqqq.platform.persistence.jpa.entity.meta;

import com.ryuqqq.platform.persistence.jpa.entity.BaseVersionedSoftDeleteEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * QueryDSL Q-type generation anchor for {@link BaseVersionedSoftDeleteEntity} hierarchy.
 *
 * <p>Not intended for runtime persistence — services define their own {@code @Entity} types.
 */
@Entity
@Table(name = "platform_persistence_query_meta")
class PersistenceQueryMetaEntity extends BaseVersionedSoftDeleteEntity {

    @Id private Long id;

    protected PersistenceQueryMetaEntity() {}
}
