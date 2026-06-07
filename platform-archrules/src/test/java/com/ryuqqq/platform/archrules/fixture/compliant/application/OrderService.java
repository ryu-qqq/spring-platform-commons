package com.ryuqqq.platform.archrules.fixture.compliant.application;

import com.ryuqqq.platform.archrules.fixture.compliant.domain.OrderId;

/** 애플리케이션은 도메인에만 의존(안쪽 방향). */
public class OrderService {

    public OrderId create(long value) {
        return new OrderId(value);
    }
}
