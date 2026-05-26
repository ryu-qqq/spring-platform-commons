package com.ryuqqq.platform.common.vo;

/**
 * 정렬 + offset 페이징 묶음. {@code SearchCriteria}가 조합해 사용한다.
 *
 * @param sortKey 정렬 키
 * @param sortDirection 정렬 방향
 * @param pageRequest 페이징
 * @param includeDeleted soft-deleted 행 포함 여부
 */
public record QueryContext<T extends SortKey>(
        T sortKey, SortDirection sortDirection, PageRequest pageRequest, boolean includeDeleted) {

    public static <T extends SortKey> QueryContext<T> of(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest) {
        return of(sortKey, sortDirection, pageRequest, false);
    }

    public static <T extends SortKey> QueryContext<T> of(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest, boolean includeDeleted) {
        return new QueryContext<>(sortKey, sortDirection, pageRequest, includeDeleted);
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
