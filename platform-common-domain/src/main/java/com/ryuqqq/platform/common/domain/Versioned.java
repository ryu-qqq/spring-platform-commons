package com.ryuqqq.platform.common.domain;

/**
 * 낙관적 락용 version 계약. Outbox·Aggregate가 conform한다.
 *
 * <p>wiki: {@code long version} + {@code refreshVersion(long)} 필수 (outbox-family).
 */
public interface Versioned {

    long version();

    void refreshVersion(long version);
}
