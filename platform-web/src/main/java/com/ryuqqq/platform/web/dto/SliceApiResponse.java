package com.ryuqqq.platform.web.dto;

import java.util.List;

import com.ryuqqq.platform.common.vo.SliceMeta;

/**
 * 커서 페이징 REST 응답 envelope.
 *
 * @param <T> 콘텐츠 타입
 * @param <C> next cursor 타입
 */
public record SliceApiResponse<T, C>(
        List<T> content, int size, boolean hasNext, C nextCursor) {

    public SliceApiResponse {
        content = content != null ? List.copyOf(content) : List.of();
    }

    public static <T, C> SliceApiResponse<T, C> of(List<T> content, SliceMeta<C> meta) {
        return new SliceApiResponse<>(content, meta.size(), meta.hasNext(), meta.nextCursor());
    }

    public static <T, C> SliceApiResponse<T, C> of(
            List<T> content, int size, boolean hasNext, C nextCursor) {
        return new SliceApiResponse<>(content, size, hasNext, nextCursor);
    }
}
