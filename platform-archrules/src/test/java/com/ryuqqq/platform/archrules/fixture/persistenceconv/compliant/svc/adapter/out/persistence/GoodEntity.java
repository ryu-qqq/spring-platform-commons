package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import com.ryuqqq.platform.persistence.jpa.entity.BaseAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 컨벤션 준수 — @Entity는 platform BaseAuditEntity 계열을 상속한다. */
@Entity
public class GoodEntity extends BaseAuditEntity {
    @Id private Long id;

    public Long getId() {
        return id;
    }
}
