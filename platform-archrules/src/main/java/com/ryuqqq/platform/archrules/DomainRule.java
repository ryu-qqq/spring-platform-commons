package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 도메인 컨벤션 룰 1건 — 식별자 + ArchUnit 규칙 + 심각도.
 *
 * <p>{@link DomainHealthReporter}가 {@code rule}을 평가해 위반을 수집하고 {@code severity}로 감점한다.
 *
 * @param id 룰 식별자 (예: {@code NO_TIME_IN_DOMAIN})
 * @param rule ArchUnit 규칙
 * @param severity 심각도
 */
public record DomainRule(String id, ArchRule rule, Severity severity) {

    public DomainRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
    }
}
