package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.application;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 위반 — application 레이어가 QueryDSL을 직접 사용한다. */
public class LeakyAppService {
    public BooleanExpression alwaysTrue() {
        return Expressions.asBoolean(true).isTrue();
    }
}
