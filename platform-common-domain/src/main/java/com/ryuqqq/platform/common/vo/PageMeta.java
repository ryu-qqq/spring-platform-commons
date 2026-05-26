package com.ryuqqq.platform.common.vo;

/**
 * offset 페이징 응답 메타.
 *
 * @param page 0-based page index
 * @param size page size
 * @param totalCount 전체 건수
 */
public record PageMeta(int page, int size, long totalCount) {

    public static PageMeta of(int page, int size, long totalCount) {
        return new PageMeta(page, size, totalCount);
    }

    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) ((totalCount + size - 1) / size);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
