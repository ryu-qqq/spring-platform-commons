package com.ryuqqq.platform.common.vo;

import java.time.Instant;

/**
 * 날짜 범위 필터.
 *
 * @param fromInclusive 시작 시각 (null이면 하한 없음)
 * @param toExclusive 종료 시각 (null이면 상한 없음)
 * @param dateField 필터 대상 날짜 필드 (null 가능)
 */
public record DateRange(Instant fromInclusive, Instant toExclusive, DateField dateField) {

    public static DateRange of(Instant fromInclusive, Instant toExclusive) {
        return new DateRange(fromInclusive, toExclusive, null);
    }

    public static DateRange of(Instant fromInclusive, Instant toExclusive, DateField dateField) {
        return new DateRange(fromInclusive, toExclusive, dateField);
    }

    public boolean isEmpty() {
        return fromInclusive == null && toExclusive == null;
    }
}
