package com.ryuqqq.platform.web.dto;

import com.ryuqqq.platform.common.vo.PageMeta;
import java.util.List;

/**
 * offset 페이징 REST 응답 envelope.
 *
 * @param <T> 콘텐츠 타입
 */
public record PageApiResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PageApiResponse {
        content = List.copyOf(content);
    }

    public static <T> PageApiResponse<T> of(List<T> content, PageMeta meta) {
        return of(content, meta.page(), meta.size(), meta.totalElements());
    }

    public static <T> PageApiResponse<T> of(
            List<T> content, int page, int size, long totalElements) {
        PageMeta meta = PageMeta.of(page, size, totalElements);
        int totalPages = meta.totalPages();
        boolean first = page == 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageApiResponse<>(content, page, size, totalElements, totalPages, first, last);
    }
}
