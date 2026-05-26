package com.ryuqqq.platform.common.exception;

import java.util.Map;

/**
 * 비즈니스 규칙 위반 예외 베이스. 도메인별 예외는 {@code extends DomainException}.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> args;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), Map.of());
    }

    public DomainException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainException(ErrorCode errorCode, String message, Map<String, Object> args) {
        super(message);
        this.errorCode = errorCode;
        this.args = Map.copyOf(args);
    }

    public DomainException(ErrorCode errorCode, String message, Map<String, Object> args, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = Map.copyOf(args);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** {@link ErrorCode#getCode()} 위임. */
    public String code() {
        return errorCode.getCode();
    }

    /** {@link ErrorCode#getHttpStatus()} 위임. */
    public int httpStatus() {
        return errorCode.getHttpStatus();
    }

    public Map<String, Object> args() {
        return args;
    }
}
