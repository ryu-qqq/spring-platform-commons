package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * frozen 한 줄 표면 self-test — ArchTests.in(HexagonalArchRulesFrozen.class)가
 * 소비측에서 동작함을 compliant 픽스처로 증명한다(위반 없음 → 빈 baseline, 통과).
 * store는 archunit.properties로 build/에 격리된다.
 */
@AnalyzeClasses(packages = "com.ryuqqq.platform.archrules.fixture.compliant")
class FrozenSuiteArchTest {

    @ArchTest static final ArchTests platform = ArchTests.in(HexagonalArchRulesFrozen.class);
}
