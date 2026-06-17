package com.ryuqqq.platform.common.vo;

import java.util.List;

/**
 * 정렬 명세 — 하나 이상의 {@link SortOrder}를 우선순위 순서대로 보존한다.
 *
 * <p>복합 정렬({@code ORDER BY a DESC, b ASC})을 표현한다. {@code orders}는 불변이며 비어 있을 수 없다.
 *
 * @param orders 정렬 항목 (선언 순서 = 정렬 우선순위)
 */
public record Sort<T extends SortKey>(List<SortOrder<T>> orders) {

    public Sort {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("sort orders must not be empty");
        }
        orders = List.copyOf(orders);
    }

    /** 단일 정렬 편의 생성. */
    public static <T extends SortKey> Sort<T> by(T key, SortDirection direction) {
        return new Sort<>(List.of(new SortOrder<>(key, direction)));
    }

    @SafeVarargs
    public static <T extends SortKey> Sort<T> of(SortOrder<T>... orders) {
        return new Sort<>(List.of(orders));
    }

    public static <T extends SortKey> Sort<T> of(List<SortOrder<T>> orders) {
        return new Sort<>(orders);
    }
}
