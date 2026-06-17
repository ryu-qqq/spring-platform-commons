package com.ryuqqq.platform.common.vo;

/**
 * 정렬 + 커서 페이징 묶음. {@code SearchCriteria}가 조합해 사용한다.
 *
 * @param sort 정렬 명세(복합 정렬 지원)
 * @param pageRequest 커서 페이징
 * @param includeDeleted soft-deleted 행 포함 여부
 */
public record CursorQueryContext<T extends SortKey, C>(
        Sort<T> sort, CursorPageRequest<C> pageRequest, boolean includeDeleted) {

    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            Sort<T> sort, CursorPageRequest<C> pageRequest) {
        return of(sort, pageRequest, false);
    }

    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            Sort<T> sort, CursorPageRequest<C> pageRequest, boolean includeDeleted) {
        return new CursorQueryContext<>(sort, pageRequest, includeDeleted);
    }

    /** 단일 정렬 편의 생성. */
    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            T sortKey, SortDirection sortDirection, CursorPageRequest<C> pageRequest) {
        return of(Sort.by(sortKey, sortDirection), pageRequest, false);
    }

    /** 단일 정렬 편의 생성. */
    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            T sortKey,
            SortDirection sortDirection,
            CursorPageRequest<C> pageRequest,
            boolean includeDeleted) {
        return of(Sort.by(sortKey, sortDirection), pageRequest, includeDeleted);
    }

    public static <T extends SortKey, C> CursorQueryContext<T, C> defaultOf(T sortKey) {
        return of(Sort.by(sortKey, SortDirection.DESC), CursorPageRequest.firstPage());
    }

    public int size() {
        return pageRequest.size();
    }

    public C cursor() {
        return pageRequest.cursor();
    }

    public boolean isFirstPage() {
        return pageRequest.isFirstPage();
    }
}
