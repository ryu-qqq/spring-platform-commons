package com.ryuqqq.platform.common.vo;

/**
 * offset 페이징 응답 메타.
 *
 * @param page 0-based page index
 * @param size page size
 * @param totalCount 전체 건수
 */
public record PageMeta(int page, int size, long totalCount) {

    private static final int DEFAULT_SIZE = 20;

    public PageMeta {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative: " + totalCount);
        }
    }

    public static PageMeta of(int page, int size, long totalCount) {
        return new PageMeta(page, size, totalCount);
    }

    public int totalPages() {
        long pages = totalCount / size;
        if (totalCount % size != 0) {
            pages++;
        }
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public static PageMeta empty(int size) {
        return of(0, size, 0);
    }

    public static PageMeta empty() {
        return empty(DEFAULT_SIZE);
    }
}
