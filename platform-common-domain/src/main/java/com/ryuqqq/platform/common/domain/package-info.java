/**
 * Platform common domain aggregate contracts.
 *
 * <p>{@code Versioned} — 낙관적 락용 version 계약({@code long version()} + {@code refreshVersion(long)}).
 * Outbox·Aggregate가 conform 한다.
 *
 * <p>Wiki: {@code wiki/conventions/java-springboot-hexagonal/layers/domain.md} § Versioned
 */
package com.ryuqqq.platform.common.domain;
