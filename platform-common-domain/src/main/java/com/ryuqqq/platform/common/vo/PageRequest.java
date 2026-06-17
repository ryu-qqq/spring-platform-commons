package com.ryuqqq.platform.common.vo;

/**
 * offset 기반 페이지 요청.
 *
 * @param page 0-based page index
 * @param size page size
 */
public record PageRequest(int page, int size) {

    private static final int DEFAULT_SIZE = 20;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest firstPage() {
        return of(0, DEFAULT_SIZE);
    }

    public static PageRequest firstPage(int size) {
        return of(0, size);
    }

    public long offset() {
        return (long) page * size;
    }

    public static PageRequest defaultPage() {
        return of(0, DEFAULT_SIZE);
    }

    public boolean isFirst() {
        return page == 0;
    }
}
