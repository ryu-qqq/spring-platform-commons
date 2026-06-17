package com.ryuqqq.platform.archrules.fixture.domainconv.violation.svc.domain.aggregate;

import java.time.Instant;
import java.util.List;

/**
 * Violation aggregate — 다섯 룰 동시 위반: record(R5)·public 생성자(R6)·raw List 필드(R7)·
 * Instant.now() 직접 호출(R1)·setter(R2).
 */
public record BadAggregate(List<String> items) {

    public void setName(String name) {
        // setter 위반 (R2)
    }

    public Instant when() {
        return Instant.now(); // 시간 직접 호출 위반 (R1)
    }
}
