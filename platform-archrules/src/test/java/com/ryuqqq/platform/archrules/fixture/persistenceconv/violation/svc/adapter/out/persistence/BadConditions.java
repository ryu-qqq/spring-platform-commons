package com.ryuqqq.platform.archrules.fixture.persistenceconv.violation.svc.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 위반 — ConditionBuilder가 아닌 곳에서 QueryDSL 조건을 만들어 반환한다(조건 로직 누수). */
public class BadConditions {
    public BooleanExpression nameEq(String name) {
        return Expressions.asString(name).eq(name);
    }
}
