package com.ryuqqq.platform.common.vo;

/**
 * 정렬 키 마커. 도메인별 {@code enum}이 구현한다.
 *
 * <p>DB 컬럼명 매핑은 adapter-out 레이어 책임.
 */
public interface SortKey {

    String fieldName();
}
