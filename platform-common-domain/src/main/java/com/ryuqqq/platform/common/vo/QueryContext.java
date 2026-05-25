package com.ryuqqq.platform.common.vo;

/**
 * 정렬 + offset 페이징 묶음. {@code SearchCriteria}가 조합해 사용한다.
 *
 * @param sortKey 정렬 키
 * @param sortDirection 정렬 방향
 * @param pageRequest 페이징
 */
public record QueryContext<T extends SortKey>(
        T sortKey, SortDirection sortDirection, PageRequest pageRequest) {

    public static <T extends SortKey> QueryContext<T> of(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest) {
        return new QueryContext<>(sortKey, sortDirection, pageRequest);
    }

    public static <T extends SortKey> QueryContext<T> defaultOf(T sortKey) {
        return of(sortKey, SortDirection.DESC, PageRequest.firstPage());
    }

    public int size() {
        return pageRequest.size();
    }

    public long offset() {
        return pageRequest.offset();
    }

    public int page() {
        return pageRequest.page();
    }
}
