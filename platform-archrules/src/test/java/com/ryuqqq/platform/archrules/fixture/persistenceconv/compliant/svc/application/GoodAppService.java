package com.ryuqqq.platform.archrules.fixture.persistenceconv.compliant.svc.application;

/** application 레이어 — 영속/QueryDSL 스택에 의존하지 않는다(컨벤션 준수). */
public class GoodAppService {
    public String describe() {
        return "no querydsl here";
    }
}
