package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 도메인 컨벤션 룰을 빌드를 죽이지 않고 평가해 {@link HealthReport}(점수 + 위반)를 산출한다.
 *
 * <p>게이트(빌드 실패)가 아니라 <b>진단</b>이다 — 소비측이 자기 도메인 클래스를 import 해 호출하고,
 * 결과 JSON을 스코어카드/CI 아티팩트로 흘린다.
 *
 * <pre>{@code
 * JavaClasses classes = new ClassFileImporter().importPackages("com.ryuq.myservice");
 * HealthReport report = DomainHealthReporter.report(classes, DomainConventionRules.all());
 * System.out.println(report.toJson());
 * }</pre>
 */
public final class DomainHealthReporter {

    private DomainHealthReporter() {}

    public static HealthReport report(JavaClasses classes, List<DomainRule> rules) {
        List<Finding> findings = new ArrayList<>();
        Set<String> failingRuleIds = new HashSet<>();

        for (DomainRule dr : rules) {
            EvaluationResult result = dr.rule().evaluate(classes);
            if (!result.hasViolation()) {
                continue;
            }
            failingRuleIds.add(dr.id());
            for (String detail : result.getFailureReport().getDetails()) {
                findings.add(new Finding(dr.id(), dr.severity(), detail));
            }
        }

        return new HealthReport(computeScore(rules, failingRuleIds), findings);
    }

    /** 실패한 룰의 severity 가중치 합을 100에서 감점(룰당 1회). CRITICAL은 게이트라 점수 제외. */
    private static int computeScore(List<DomainRule> rules, Set<String> failingRuleIds) {
        int penalty = 0;
        for (DomainRule dr : rules) {
            if (dr.severity() == Severity.CRITICAL) {
                continue;
            }
            if (failingRuleIds.contains(dr.id())) {
                penalty += dr.severity().weight();
            }
        }
        return Math.max(0, 100 - penalty);
    }
}
