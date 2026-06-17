package com.ryuqqq.platform.common.vo;

/**
 * 정렬 + offset 페이징 묶음. {@code SearchCriteria}가 조합해 사용한다.
 *
 * @param sort 정렬 명세(복합 정렬 지원)
 * @param pageRequest 페이징
 * @param includeDeleted soft-deleted 행 포함 여부
 */
public record QueryContext<T extends SortKey>(
        Sort<T> sort, PageRequest pageRequest, boolean includeDeleted) {

    public static <T extends SortKey> QueryContext<T> of(Sort<T> sort, PageRequest pageRequest) {
        return of(sort, pageRequest, false);
    }

    public static <T extends SortKey> QueryContext<T> of(
            Sort<T> sort, PageRequest pageRequest, boolean includeDeleted) {
        return new QueryContext<>(sort, pageRequest, includeDeleted);
    }

    /** 단일 정렬 편의 생성. */
    public static <T extends SortKey> QueryContext<T> of(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest) {
        return of(Sort.by(sortKey, sortDirection), pageRequest, false);
    }

    /** 단일 정렬 편의 생성. */
    public static <T extends SortKey> QueryContext<T> of(
            T sortKey, SortDirection sortDirection, PageRequest pageRequest, boolean includeDeleted) {
        return of(Sort.by(sortKey, sortDirection), pageRequest, includeDeleted);
    }

    public static <T extends SortKey> QueryContext<T> defaultOf(T sortKey) {
        return of(Sort.by(sortKey, SortDirection.DESC), PageRequest.firstPage());
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
