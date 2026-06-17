package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.adapter.out.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/** 컨벤션 준수 — QueryDSL 조건 조립은 *ConditionBuilder 안에 캡슐화한다. */
public class GoodConditionBuilder {
    public BooleanExpression nameEq(String name) {
        return name == null ? null : Expressions.asString(name).eq(name);
    }
}
