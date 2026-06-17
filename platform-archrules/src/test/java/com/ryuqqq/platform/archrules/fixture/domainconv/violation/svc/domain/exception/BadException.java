package com.ryuqqq.platform.archrules.fixture.domainconv.violation.svc.domain.exception;

/** Violation 도메인 예외 — DomainException이 아닌 RuntimeException 직접 상속(R3). */
public class BadException extends RuntimeException {

    public BadException(String message) {
        super(message);
    }
}
