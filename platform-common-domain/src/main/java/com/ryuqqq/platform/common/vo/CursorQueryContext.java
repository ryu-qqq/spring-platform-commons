package com.ryuqqq.platform.common.vo;

/**
 * 정렬 + 커서 페이징 묶음. {@code SearchCriteria}가 조합해 사용한다.
 *
 * @param sortKey 정렬 키
 * @param sortDirection 정렬 방향
 * @param pageRequest 커서 페이징
 * @param includeDeleted soft-deleted 행 포함 여부
 */
public record CursorQueryContext<T extends SortKey, C>(
        T sortKey, SortDirection sortDirection, CursorPageRequest<C> pageRequest, boolean includeDeleted) {

    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            T sortKey, SortDirection sortDirection, CursorPageRequest<C> pageRequest) {
        return of(sortKey, sortDirection, pageRequest, false);
    }

    public static <T extends SortKey, C> CursorQueryContext<T, C> of(
            T sortKey,
            SortDirection sortDirection,
            CursorPageRequest<C> pageRequest,
            boolean includeDeleted) {
        return new CursorQueryContext<>(sortKey, sortDirection, pageRequest, includeDeleted);
    }

    public static <T extends SortKey, C> CursorQueryContext<T, C> defaultOf(T sortKey) {
        return of(sortKey, SortDirection.DESC, CursorPageRequest.firstPage());
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
