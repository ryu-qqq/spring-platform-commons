package com.ryuqqq.platform.archrules;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 규칙 라이브러리 self-test — 규약 준수 픽스처는 통과, 위반 픽스처는 잡히는지(positive control) 검증한다.
 */
class HexagonalArchRulesTest {

    private static JavaClasses compliant;
    private static JavaClasses violating;

    @BeforeAll
    static void load() {
        compliant =
                new ClassFileImporter()
                        .importPackages("com.ryuqqq.platform.archrules.fixture.compliant");
        violating =
                new ClassFileImporter()
                        .importPackages("com.ryuqqq.platform.archrules.fixture.violation");
    }

    @Test
    @DisplayName("규약을 지키는 코드는 모든 규칙을 통과한다")
    void compliantPassesAllRules() {
        HexagonalArchRules.DOMAIN_FRAMEWORK_FREE.check(compliant);
        HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE.check(compliant);
        HexagonalArchRules.HEXAGONAL_LAYERS.check(compliant);
    }

    @Test
    @DisplayName("도메인이 Spring에 의존하면 DOMAIN_FRAMEWORK_FREE가 잡는다")
    void frameworkFreeCatchesSpringInDomain() {
        assertThatThrownBy(() -> HexagonalArchRules.DOMAIN_FRAMEWORK_FREE.check(violating))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("도메인이 application(바깥쪽)에 의존하면 HEXAGONAL_LAYERS가 잡는다")
    void layersCatchInwardViolation() {
        assertThatThrownBy(() -> HexagonalArchRules.HEXAGONAL_LAYERS.check(violating))
                .isInstanceOf(AssertionError.class);
    }
}
