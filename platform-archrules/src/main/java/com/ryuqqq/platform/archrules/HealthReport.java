package com.ryuqqq.platform.archrules;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 도메인 건강 리포트 — 점수 + 위반 목록. {@link DomainHealthReporter}가 산출한다.
 *
 * <p>점수는 <b>실패한 컨벤션 차원</b> 기준(룰당 1회 감점)이라 한 클래스의 다발 위반으로 폭발하지 않는다.
 * 위반 개수·핫스팟은 {@code findings}에서 본다. 점수=차원 건강, findings=상세.
 *
 * @param score 0~100 건강 점수
 * @param findings 위반 상세 목록
 */
public record HealthReport(int score, List<Finding> findings) {

    public HealthReport {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be 0..100: " + score);
        }
        findings = List.copyOf(findings);
    }

    /** 심각도별 위반 건수. */
    public Map<Severity, Long> countBySeverity() {
        Map<Severity, Long> counts = new TreeMap<>();
        for (Finding f : findings) {
            counts.merge(f.severity(), 1L, Long::sum);
        }
        return counts;
    }

    /** 위반이 하나도 없으면 건강. */
    public boolean isHealthy() {
        return findings.isEmpty();
    }

    /** Jackson 비의존 수동 직렬화. audit-sweep 스코어카드/CI 아티팩트용. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"score\":").append(score).append(",\"findings\":[");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"ruleId\":\"")
                    .append(escape(f.ruleId()))
                    .append("\",\"severity\":\"")
                    .append(f.severity())
                    .append("\",\"message\":\"")
                    .append(escape(f.message()))
                    .append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
