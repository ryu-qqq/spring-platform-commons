package com.ryuqqq.platform.common.domain;

/**
 * 낙관적 락용 version 계약. Aggregate가 자기 version을 <b>읽기 전용</b>으로 노출한다.
 *
 * <p>version 반영(증가·동기화)은 영속성 매퍼가 재구성 시 주입하는 인프라 책임이다 — 도메인 계약은
 * 노출만 한다. aggregate 레벨 낙관적 동시성 검사가 이 값을 비교 기준으로 쓴다(ADR-0006).
 */
public interface Versioned {

    long version();
}
