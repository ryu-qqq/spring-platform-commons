/**
 * Platform common domain aggregate contracts.
 *
 * <p>{@code Versioned} — 낙관적 락용 version 계약({@code long version()} 읽기 전용).
 * Aggregate가 자기 version을 노출하고, 반영은 영속성 매퍼 책임이다(ADR-0006).
 *
 * <p>Wiki: {@code wiki/conventions/java-springboot-hexagonal/layers/domain.md} § Versioned
 */
package com.ryuqqq.platform.common.domain;
