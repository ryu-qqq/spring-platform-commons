package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import java.util.List;

/** 위반 — 조회/파생 쿼리 메서드를 Repository 인터페이스에 직접 선언한다. */
public interface BadRepository {
    Object save(Object entity);

    List<Object> findByName(String name);
}
