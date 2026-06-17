package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import java.util.List;

/** 컨벤션 준수 — 직접 선언 메서드는 save/saveAll만. 조회는 QueryDSL(별도)로 한다. */
public interface GoodRepository {
    GoodEntity save(GoodEntity entity);

    List<GoodEntity> saveAll(List<GoodEntity> entities);
}
