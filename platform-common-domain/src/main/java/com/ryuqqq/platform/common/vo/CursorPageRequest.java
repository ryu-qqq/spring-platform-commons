package com.ryuqqq.platform.common.vo;

/**
 * 커서 기반 페이지 요청. {@code cursor == null}이면 첫 페이지.
 *
 * @param cursor 이전 응답의 next cursor (첫 페이지는 null)
 * @param size page size
 */
public record CursorPageRequest<C>(C cursor, int size) {

    private static final int DEFAULT_SIZE = 20;

    public static <C> CursorPageRequest<C> of(C cursor, int size) {
        return new CursorPageRequest<>(cursor, size);
    }

    public static <C> CursorPageRequest<C> firstPage() {
        return of(null, DEFAULT_SIZE);
    }

    public static <C> CursorPageRequest<C> firstPage(int size) {
        return of(null, size);
    }

    public boolean isFirstPage() {
        return cursor == null;
    }
}
