package com.ryuqqq.platform.archrules.fixture.domainconv.compliant.svc.domain.exception;

import com.ryuqqq.platform.common.exception.DomainException;

/** Compliant 도메인 예외 — DomainException 상속. */
public class GoodException extends DomainException {

    public GoodException() {
        super(GoodErrorCode.SAMPLE);
    }
}
