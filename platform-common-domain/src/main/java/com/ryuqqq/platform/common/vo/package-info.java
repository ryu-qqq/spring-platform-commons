/**
 * Platform common query/paging value objects and field markers.
 *
 * <p>쿼리·페이징·범위·soft delete·키 VO와 정렬·검색·날짜 필드 마커를 모은다. 마커
 * 인터페이스({@code SortKey}·{@code SearchField}·{@code DateField}·{@code CacheKey}·{@code LockKey})는
 * 도메인별 {@code enum}/{@code record}가 구현하고, 페이징·범위 VO는 {@code record}로 직접 쓴다.
 *
 * <p>Wiki: {@code wiki/conventions/java-springboot-hexagonal/layers/domain.md} § common.vo
 */
package com.ryuqqq.platform.common.vo;
