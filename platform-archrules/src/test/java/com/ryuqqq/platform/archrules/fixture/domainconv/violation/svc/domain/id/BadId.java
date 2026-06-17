package com.ryuqqq.platform.archrules.fixture.domainconv.violation.svc.domain.id;

/** Violation Identifier — record가 아닌 class(R9). */
public class BadId {
    private final long value;

    public BadId(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }
}
