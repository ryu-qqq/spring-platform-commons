package com.ryuqqq.platform.common.vo;

import java.util.Locale;

/** 정렬 방향. */
public enum SortDirection {
    ASC,
    DESC;

    /** 기본 정렬 방향(최신순 관행). */
    public static SortDirection defaultDirection() {
        return DESC;
    }

    /** 오름차순이면 true. */
    public boolean isAscending() {
        return this == ASC;
    }

    /** 방향 반전(ASC↔DESC). */
    public SortDirection reverse() {
        return this == ASC ? DESC : ASC;
    }

    /**
     * 문자열을 방향으로 파싱한다. null/blank나 유효하지 않은 값은 {@link #defaultDirection()}으로 폴백. 정확한 enum명(대소문자·앞뒤 공백
     * 무시)만 허용하며, 관용표기는 받지 않는다(어댑터 책임).
     */
    public static SortDirection fromString(String value) {
        if (value == null || value.isBlank()) {
            return defaultDirection();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return defaultDirection();
        }
    }
}
