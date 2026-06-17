package com.ryuqqq.platform.archrules;

/**
 * 도메인 컨벤션 룰의 심각도. 건강 점수 가중치와 게이트 여부를 가른다.
 *
 * <p>{@code CRITICAL}은 빌드 게이트(실패) 대상이며 점수 계산에는 포함하지 않는다(기존 헥사고날
 * 게이트가 담당). {@code HIGH}/{@code MEDIUM}/{@code LOW}는 건강 리포트로 진단·감점한다.
 */
public enum Severity {
    CRITICAL(25),
    HIGH(10),
    MEDIUM(5),
    LOW(2);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    /** 점수 감점 가중치(룰당 1회). */
    public int weight() {
        return weight;
    }
}
