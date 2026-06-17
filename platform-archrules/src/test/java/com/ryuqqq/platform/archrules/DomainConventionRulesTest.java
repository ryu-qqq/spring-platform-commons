package com.ryuqqq.platform.archrules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("도메인 컨벤션 룰 + 건강 리포트")
class DomainConventionRulesTest {

    private static final String BASE = "com.ryuqqq.platform.archrules.fixture.domainconv.";

    private static JavaClasses compliant;
    private static JavaClasses violation;

    @BeforeAll
    static void load() {
        // fixture가 test 소스셋에 있으므로 DO_NOT_INCLUDE_TESTS를 쓰면 안 된다(전부 제외됨).
        ClassFileImporter importer = new ClassFileImporter();
        compliant = importer.importPackages(BASE + "compliant");
        violation = importer.importPackages(BASE + "violation");
    }

    @Test
    @DisplayName("compliant 도메인은 모든 룰을 통과한다 (GREEN)")
    void allRulesPassOnCompliant() {
        for (DomainRule dr : DomainConventionRules.all()) {
            assertThat(dr.rule().evaluate(compliant).hasViolation())
                    .as("compliant이 %s를 통과해야 한다", dr.id())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("violation 도메인은 모든 룰에 걸린다 (RED)")
    void allRulesFailOnViolation() {
        for (DomainRule dr : DomainConventionRules.all()) {
            assertThat(dr.rule().evaluate(violation).hasViolation())
                    .as("violation이 %s에 걸려야 한다", dr.id())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("compliant 건강 점수는 100 (findings 0)")
    void healthyScore() {
        HealthReport report = DomainHealthReporter.report(compliant, DomainConventionRules.all());

        assertThat(report.score()).isEqualTo(100);
        assertThat(report.isHealthy()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    @DisplayName("violation 점수는 실패 차원 가중합으로 결정적: 100 − (3×10+5×5+3×2) = 39")
    void unhealthyScore() {
        HealthReport report = DomainHealthReporter.report(violation, DomainConventionRules.all());

        assertThat(report.score()).isEqualTo(39);
        assertThat(report.findings()).isNotEmpty();

        List<String> failingIds =
                report.findings().stream().map(Finding::ruleId).distinct().toList();
        assertThat(failingIds)
                .containsExactlyInAnyOrder(
                        DomainConventionRules.all().stream().map(DomainRule::id).toArray(String[]::new));
    }

    @Test
    @DisplayName("toJson은 score·findings를 담는다")
    void jsonReport() {
        HealthReport report = DomainHealthReporter.report(violation, DomainConventionRules.all());

        String json = report.toJson();

        assertThat(json).contains("\"score\":39").contains("\"NO_TIME_IN_DOMAIN\"");
    }
}
