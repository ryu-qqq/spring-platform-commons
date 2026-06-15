package com.ryuqqq.platform.archrules;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ryuqqq.platform.archrules.support.InMemoryViolationStore;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** freezing ratchet 메커니즘 self-test — in-memory 스토어로 동결·차단·회귀차단을 검증한다. */
class FreezingBehaviorTest {

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
    @DisplayName("레거시 위반은 baseline에 동결되어 통과한다")
    void freezesLegacyViolations() {
        ArchRule frozen =
                FreezingArchRule.freeze(HexagonalArchRules.DOMAIN_FRAMEWORK_FREE)
                        .persistIn(new InMemoryViolationStore());

        frozen.check(violating); // 첫 실행: baseline 기록 + 통과
        assertThatCode(() -> frozen.check(violating)).doesNotThrowAnyException(); // 동결 재확인
    }

    @Test
    @DisplayName("baseline에 없는 신규 위반은 실패시킨다")
    void newViolationFails() {
        ArchRule frozen =
                FreezingArchRule.freeze(HexagonalArchRules.DOMAIN_FRAMEWORK_FREE)
                        .persistIn(new InMemoryViolationStore());

        frozen.check(compliant); // baseline = 비어 있음(위반 없음)
        assertThatThrownBy(() -> frozen.check(violating)) // 신규 위반 → 실패
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("고쳐진 위반은 prune되고, 재발하면 다시 실패한다(ratchet)")
    void prunesFixedAndBlocksRegression() {
        ArchRule frozen =
                FreezingArchRule.freeze(HexagonalArchRules.DOMAIN_FRAMEWORK_FREE)
                        .persistIn(new InMemoryViolationStore());

        frozen.check(violating); // baseline = 위반들
        frozen.check(compliant); // 위반 사라짐 → store prune, 통과
        assertThatThrownBy(() -> frozen.check(violating)) // 재발 → ratchet으로 실패
                .isInstanceOf(AssertionError.class);
    }
}
