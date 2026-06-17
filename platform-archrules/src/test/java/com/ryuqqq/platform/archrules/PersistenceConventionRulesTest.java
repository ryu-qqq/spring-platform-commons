package com.ryuqqq.platform.archrules;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("영속 컨벤션 룰 + 건강 리포트")
class PersistenceConventionRulesTest {

    private static final String BASE = "com.ryuqqq.platform.archrules.fixture.persistenceconv.";

    private static JavaClasses compliant;
    private static JavaClasses violation;

    @BeforeAll
    static void load() {
        ClassFileImporter importer = new ClassFileImporter();
        compliant = importer.importPackages(BASE + "compliant");
        violation = importer.importPackages(BASE + "violation");
    }

    @Test
    @DisplayName("게이트: compliant은 QueryDSL 누수가 없다 (GREEN)")
    void gatePassesOnCompliant() {
        assertThat(
                        PersistenceConventionRules.NO_QUERYDSL_OUTSIDE_ADAPTER_OUT
                                .evaluate(compliant)
                                .hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("게이트: violation은 application의 QueryDSL 사용에 걸린다 (RED)")
    void gateFailsOnViolation() {
        assertThat(
                        PersistenceConventionRules.NO_QUERYDSL_OUTSIDE_ADAPTER_OUT
                                .evaluate(violation)
                                .hasViolation())
                .isTrue();
    }

    @Test
    @DisplayName("REPOSITORY_COMMAND_ONLY: compliant Repository는 통과 (GREEN)")
    void repositoryCommandOnlyPassesOnCompliant() {
        assertThat(PersistenceConventionRules.REPOSITORY_COMMAND_ONLY.evaluate(compliant).hasViolation())
                .isFalse();
    }

    @Test
    @DisplayName("REPOSITORY_COMMAND_ONLY: 파생 쿼리 선언 Repository는 걸린다 (RED)")
    void repositoryCommandOnlyFailsOnViolation() {
        assertThat(PersistenceConventionRules.REPOSITORY_COMMAND_ONLY.evaluate(violation).hasViolation())
                .isTrue();
    }
}
