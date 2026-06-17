package com.ryuqqq.platform.archrules.fixture.domainconv.compliant.svc.domain.exception;

import com.ryuqqq.platform.common.exception.ErrorCode;

/** Compliant ErrorCode — enum implements ErrorCode. */
public enum GoodErrorCode implements ErrorCode {
    SAMPLE;

    @Override
    public String getCode() {
        return "SVC-001";
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getMessage() {
        return "sample";
    }
}
