package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * frozen 헥사고날 ArchUnit 규칙 번들 — 레거시 위반이 있는 기존 레포의 점진 입양용.
 *
 * <p>각 규칙을 {@link FreezingArchRule}로 감싸, 첫 실행 시 현재 위반을 violation-store에
 * baseline으로 동결하고 이후 <b>신규 위반만</b> 실패시킨다(ratchet). violation-store는
 * 소비측 레포가 소유하며 {@code src/test/resources/archunit.properties}로 경로·생성 정책을 제어한다.
 *
 * <p>소비 예 (브라운필드):
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.ryuqq.marketplace")
 * class HexagonalArchitectureTest {
 *     @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRulesFrozen.class);
 * }
 * }</pre>
 *
 * <p>레거시가 없는 그린필드는 {@link HexagonalArchRules}(strict)를 직접 쓴다.
 */
public final class HexagonalArchRulesFrozen {

    private HexagonalArchRulesFrozen() {}

    @ArchTest
    static final ArchRule domainFrameworkFree =
            FreezingArchRule.freeze(HexagonalArchRules.DOMAIN_FRAMEWORK_FREE);

    @ArchTest
    static final ArchRule applicationNoWebOrPersistence =
            FreezingArchRule.freeze(HexagonalArchRules.APPLICATION_NO_WEB_OR_PERSISTENCE);

    @ArchTest
    static final ArchRule hexagonalLayers =
            FreezingArchRule.freeze(HexagonalArchRules.HEXAGONAL_LAYERS);
}
