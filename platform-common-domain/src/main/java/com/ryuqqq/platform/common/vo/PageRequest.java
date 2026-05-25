package com.ryuqqq.platform.common.vo;

/**
 * offset 기반 페이지 요청.
 *
 * @param page 0-based page index
 * @param size page size
 */
public record PageRequest(int page, int size) {

    private static final int DEFAULT_SIZE = 20;

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
}
