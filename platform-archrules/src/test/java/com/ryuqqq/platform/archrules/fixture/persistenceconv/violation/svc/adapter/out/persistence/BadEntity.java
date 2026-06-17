package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 위반 — @Entity인데 platform BaseAuditEntity 계열을 상속하지 않는다. */
@Entity
public class BadEntity {
    @Id private Long id;

    public Long getId() {
        return id;
    }
}
