package com.ryuqqq.platform.archrules.fixture.compliant.adapter.out;

import com.ryuqqq.platform.archrules.fixture.compliant.application.OrderService;

/** adapter-out은 application(포트)을 구현/의존. */
public class OrderPersistenceAdapter {

    private final OrderService service;

    public OrderPersistenceAdapter(OrderService service) {
        this.service = service;
    }
}
