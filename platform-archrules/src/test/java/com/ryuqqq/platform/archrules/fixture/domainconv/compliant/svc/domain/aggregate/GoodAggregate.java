package com.ryuqqq.platform.archrules.fixture.domainconv.compliant.svc.domain.aggregate;

import java.time.Instant;

/** Compliant aggregate — class, private 생성자, setter 없음, raw 컬렉션 없음, 시간 주입. */
public final class GoodAggregate {

    private final long id;
    private final Instant createdAt;

    private GoodAggregate(long id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public static GoodAggregate forNew(long id, Instant now) {
        return new GoodAggregate(id, now);
    }

    public long id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
