package com.ryuqqq.platform.common.exception;

/**
 * 도메인 에러 코드 계약. Bounded context별 {@code enum}이 구현한다.
 *
 * <p>형식: {@code {CONTEXT}-{NUMBER}} (예: {@code PRD-001}). HTTP status는 int — Spring 의존 없음.
 */
public interface ErrorCode {

    String getCode();

    int getHttpStatus();

    String getMessage();
}
