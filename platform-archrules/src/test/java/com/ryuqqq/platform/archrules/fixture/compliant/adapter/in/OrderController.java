package com.ryuqqq.platform.archrules.fixture.compliant.adapter.in;

import com.ryuqqq.platform.archrules.fixture.compliant.application.OrderService;

/** adapter-in은 application(포트)에 의존. */
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public long create(long value) {
        return service.create(value).value();
    }
}
