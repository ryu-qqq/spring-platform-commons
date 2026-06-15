package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * strict 한 줄 표면 self-test — ArchTests.in(HexagonalArchRules.class)가
 * 소비측에서 동작함을 compliant 픽스처로 증명한다(위반 없음 → 전 규칙 통과).
 *
 * <p>주: ArchUnit 1.x에서 규칙 집약 관용구의 타입명은 {@code ArchTests}이며,
 * {@code @ArchTest}가 부여된 {@code ArchRule} 상수들을 모아 한 줄로 입양한다.
 */
@AnalyzeClasses(packages = "com.ryuqqq.platform.archrules.fixture.compliant")
class StrictSuiteArchTest {

    @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRules.class);
}
