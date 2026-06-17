package com.ryuqqq.platform.archrules;

/**
 * 건강 리포트의 위반 1건 — 어느 룰이, 어느 심각도로, 무엇을(메시지에 클래스·위치 포함) 위반했는지.
 *
 * @param ruleId 위반한 룰 식별자
 * @param severity 심각도
 * @param message ArchUnit 위반 상세(위반 클래스·위치 포함)
 */
public record Finding(String ruleId, Severity severity, String message) {

    public Finding {
        if (ruleId == null || severity == null || message == null) {
            throw new IllegalArgumentException("finding fields must not be null");
        }
    }
}
