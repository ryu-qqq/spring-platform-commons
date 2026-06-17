package com.ryuqqq.platform.common.vo;

/**
 * 단일 정렬 항목 — 정렬 키 + 방향. {@link Sort}가 순서대로 모은다.
 *
 * @param key 정렬 키 (도메인별 {@link SortKey} enum)
 * @param direction 정렬 방향
 */
public record SortOrder<T extends SortKey>(T key, SortDirection direction) {

    public SortOrder {
        if (key == null) {
            throw new IllegalArgumentException("sort key must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("sort direction must not be null");
        }
    }
}
