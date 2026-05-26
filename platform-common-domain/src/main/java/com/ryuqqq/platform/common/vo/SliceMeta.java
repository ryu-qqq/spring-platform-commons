package com.ryuqqq.platform.common.vo;

/**
 * 커서 페이징 응답 메타.
 *
 * @param size 요청 size
 * @param hasNext 다음 슬라이스 존재 여부
 * @param nextCursor 다음 페이지 cursor (없으면 null)
 */
public record SliceMeta<C>(int size, boolean hasNext, C nextCursor) {

    public static <C> SliceMeta<C> of(int size, boolean hasNext, C nextCursor) {
        return new SliceMeta<>(size, hasNext, nextCursor);
    }

    public static <C> SliceMeta<C> empty(int size) {
        return of(size, false, null);
    }
}
