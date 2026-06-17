package com.ryuqqq.platform.architecture;

import com.ryuqqq.platform.archrules.DomainConventionRules;
import com.ryuqqq.platform.archrules.DomainHealthReporter;
import com.ryuqqq.platform.archrules.Finding;
import com.ryuqqq.platform.archrules.HealthReport;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 도메인 컨벤션 건강 리포트 마크다운 생성기 — CI가 PR 코멘트로 게시한다.
 *
 * <p>{@code DomainHealthReporter}를 스캔 패키지에 돌려 점수·findings를 마크다운으로 쓴다. 이 레포는 헥사고날 소비 서비스가 아니라 SDK이므로
 * 자기 도메인성 패키지({@code com.ryuqqq.platform})를 대상으로 <b>자기 점검(dogfood)</b>한다 — 룰 자체는 소비 서비스 도메인용이다.
 *
 * <p>args[0] = 출력 마크다운 경로, args[1] = (선택) 스캔 패키지(기본 {@code com.ryuqqq.platform}).
 */
public final class DomainHealthMain {

    private DomainHealthMain() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/domain-health.md");
        String scanPackage = args.length > 1 ? args[1] : "com.ryuqqq.platform";

        JavaClasses classes = new ClassFileImporter().importPackages(scanPackage);
        HealthReport report = DomainHealthReporter.report(classes, DomainConventionRules.all());

        String md = render(report, scanPackage);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, md);
        System.out.println(
                "domain health: score=" + report.score() + " findings=" + report.findings().size());
    }

    private static String render(HealthReport report, String scanPackage) {
        StringBuilder md = new StringBuilder();
        md.append("## 🩺 도메인 건강 리포트\n\n");
        md.append("**점수: ")
                .append(report.score())
                .append(" / 100** · 대상 `")
                .append(scanPackage)
                .append("`\n\n");
        md.append("> 이 레포는 SDK라 헥사고날 소비 서비스가 아니다 — 소비 서비스 도메인용 룰로 자기 점검(dogfood)한 결과다.\n\n");

        if (report.isHealthy()) {
            md.append("✅ 도메인 컨벤션 위반 없음.\n");
            return md.toString();
        }

        // 룰별 위반 건수 집계(선언 순서 보존).
        Map<String, RuleCount> byRule = new LinkedHashMap<>();
        for (Finding f : report.findings()) {
            byRule.computeIfAbsent(f.ruleId(), k -> new RuleCount(f.severity().name())).count++;
        }

        md.append("| 룰 | 심각도 | 위반 건수 |\n|---|---|---|\n");
        for (Map.Entry<String, RuleCount> e : byRule.entrySet()) {
            md.append("| `")
                    .append(e.getKey())
                    .append("` | ")
                    .append(e.getValue().severity)
                    .append(" | ")
                    .append(e.getValue().count)
                    .append(" |\n");
        }
        md.append("\n핫스팟(상위 ").append(Math.min(10, report.findings().size())).append("):\n\n");
        List<Finding> findings = report.findings();
        for (int i = 0; i < Math.min(10, findings.size()); i++) {
            Finding f = findings.get(i);
            String msg = f.message().replace("\n", " ");
            if (msg.length() > 160) {
                msg = msg.substring(0, 160) + "…";
            }
            md.append("- **").append(f.ruleId()).append("** — ").append(msg).append('\n');
        }
        return md.toString();
    }

    private static final class RuleCount {
        final String severity;
        int count;

        RuleCount(String severity) {
            this.severity = severity;
        }
    }
}
