package com.ryuqqq.platform.archrules.fixture.violation.domain;

import com.ryuqqq.platform.archrules.fixture.violation.application.LeakyApp;

/** 위반: 도메인이 application(바깥쪽)에 의존 → HEXAGONAL_LAYERS가 잡아야 한다. */
public class LeakyDomain {

    private final LeakyApp app = new LeakyApp();

    public LeakyApp app() {
        return app;
    }
}
