# platform-archrules Enforce 토대 (Freezing ratchet + 한 줄 Apply) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `platform-archrules`를 binary 게이트에서 레거시 동결 + 신규 위반만 차단하는 ratchet 게이트로 끌어올리고, 소비측 입양을 한 줄로 줄인다.

**Architecture:** 기존 3종 raw 규칙은 유지하되 `@ArchTest`를 부여해 `ArchRules.in()` 한 줄 strict 입양을 가능케 한다. 별도로 `FreezingArchRule`로 감싼 frozen 번들(`HexagonalArchRulesFrozen`)을 추가해 브라운필드 입양 경로를 연다. violation-store는 소비측 소유(라이브러리는 래퍼·README 템플릿만 제공). self-test는 in-memory `ViolationStore` 주입으로 ratchet 동작을 파일 오염 없이 검증한다.

**Tech Stack:** Java, ArchUnit 1.2.1 (`archunit-junit5`), JUnit5, AssertJ, Gradle.

**선행 문서:** `docs/superpowers/specs/2026-06-15-archrules-enforce-freezing-design.md`

---

## 파일 구조

| 파일 | 책임 | 신규/변경 |
|------|------|-----------|
| `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRules.java` | raw 규칙 3종 + `@ArchTest` 부여(strict 한 줄 표면) | 변경 |
| `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRulesFrozen.java` | frozen 번들(ratchet 입양 표면) | 신규 |
| `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/support/InMemoryViolationStore.java` | self-test용 in-memory 스토어 | 신규 |
| `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FreezingBehaviorTest.java` | freezing ratchet 메커니즘 검증 | 신규 |
| `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/StrictSuiteArchTest.java` | strict 한 줄 표면 end-to-end | 신규 |
| `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FrozenSuiteArchTest.java` | frozen 한 줄 표면 end-to-end | 신규 |
| `platform-archrules/src/test/resources/archunit.properties` | 모듈 self-test용 store를 `build/`로 격리 | 신규 |
| `platform-archrules/README.md` | strict/frozen 표면·`archunit.properties` 템플릿·ratchet 설명 | 변경 |

---

## Task 1: strict 한 줄 표면 — 기존 상수에 `@ArchTest` 부여

기존 raw 상수 3종에 `@ArchTest`를 달아 `ArchRules.in(HexagonalArchRules.class)`로 strict 한 줄 입양이 되게 한다. 직접 참조(`HexagonalArchRules.HEXAGONAL_LAYERS`)는 그대로 동작 → 완전 하위호환.

**Files:**
- Modify: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRules.java`
- Test: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/StrictSuiteArchTest.java`

- [ ] **Step 1: strict 한 줄 표면 self-test 작성 (실패하는 테스트)**

`StrictSuiteArchTest.java` 생성. `@AnalyzeClasses`로 compliant 픽스처만 임포트하고 `ArchRules.in(HexagonalArchRules.class)`로 묶음을 적용한다. compliant는 위반이 없으므로 통과해야 한다(= 묶음 표면이 동작함을 증명).

```java
package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchRules;

/**
 * strict 한 줄 표면 self-test — ArchRules.in(HexagonalArchRules.class)가
 * 소비측에서 동작함을 compliant 픽스처로 증명한다(위반 없음 → 전 규칙 통과).
 */
@AnalyzeClasses(packages = "com.ryuqqq.platform.archrules.fixture.compliant")
class StrictSuiteArchTest {

    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRules.class);
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*StrictSuiteArchTest'`
Expected: FAIL — `ArchRules.in(HexagonalArchRules.class)`가 `@ArchTest` 필드를 못 찾아 "no @ArchTest" 류 오류, 또는 컴파일 실패(아직 `@ArchTest` 미부여).

- [ ] **Step 3: 기존 3개 상수에 `@ArchTest` 부여**

`HexagonalArchRules.java`에 import 추가:

```java
import com.tngtech.archunit.junit.ArchTest;
```

세 상수 각각의 선언 바로 위에 `@ArchTest`를 단다. 예:

```java
    /** 도메인은 프레임워크 비의존(순수 자바)이어야 한다. */
    @ArchTest
    public static final ArchRule DOMAIN_FRAMEWORK_FREE =
            noClasses()
                    // ... 기존 본문 그대로 ...
                    .allowEmptyShould(true);
```

`APPLICATION_NO_WEB_OR_PERSISTENCE`, `HEXAGONAL_LAYERS`에도 동일하게 `@ArchTest`만 추가한다(본문·로직 무변경).

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*StrictSuiteArchTest'`
Expected: PASS

- [ ] **Step 5: 기존 self-test 회귀 확인**

Run: `./gradlew :platform-archrules:test --tests '*HexagonalArchRulesTest'`
Expected: PASS — 직접 참조 경로가 깨지지 않았음을 확인.

- [ ] **Step 6: 커밋**

```bash
git add platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRules.java \
        platform-archrules/src/test/java/com/ryuqqq/platform/archrules/StrictSuiteArchTest.java
git commit -m "feat(archrules): strict 한 줄 표면 — 상수에 @ArchTest 부여(ArchRules.in 지원)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: freezing ratchet 메커니즘 self-test (in-memory store)

freezing의 핵심 동작 3가지(레거시 동결·신규 위반 차단·prune 후 회귀 차단)를 파일 오염 없이 in-memory 스토어로 검증한다. 메커니즘 증명은 여기서 끝낸다 → 이후 번들은 smoke만.

**Files:**
- Create: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/support/InMemoryViolationStore.java`
- Test: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FreezingBehaviorTest.java`

- [ ] **Step 1: in-memory ViolationStore 작성**

ArchUnit `ViolationStore` 인터페이스를 메모리 Map으로 구현. 규칙 식별은 `rule.getDescription()`.

```java
package com.ryuqqq.platform.archrules.support;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.ViolationStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** self-test 전용 in-memory 위반 스토어 — 파일을 건드리지 않고 freezing baseline을 보관한다. */
public final class InMemoryViolationStore implements ViolationStore {

    private final Map<String, List<String>> store = new HashMap<>();

    @Override
    public void initialize(Properties properties) {
        // 메모리 스토어 — 초기화 불필요
    }

    @Override
    public boolean contains(ArchRule rule) {
        return store.containsKey(rule.getDescription());
    }

    @Override
    public void save(ArchRule rule, List<String> violations) {
        store.put(rule.getDescription(), new ArrayList<>(violations));
    }

    @Override
    public List<String> getViolations(ArchRule rule) {
        return new ArrayList<>(store.get(rule.getDescription()));
    }
}
```

- [ ] **Step 2: freezing 동작 테스트 작성 (실패하는 테스트)**

`FreezingBehaviorTest.java` 생성. `DOMAIN_FRAMEWORK_FREE`(가장 단순)로 3 시나리오를 건다. 같은 frozen 인스턴스·같은 store를 재사용해야 baseline이 누적됨에 유의.

```java
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
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*FreezingBehaviorTest'`
Expected: FAIL — `InMemoryViolationStore` 미존재 시 컴파일 실패였다가, Step 1 적용 후엔 동작. (Step 1·2를 한 번에 작성했다면 첫 실행에서 통과/실패가 갈림 — 통과하면 메커니즘 가정이 맞은 것.)

> 참고: 이 Task는 신규 프로덕션 코드가 없어 "실패 후 통과" TDD 사이클보다 "동작 명세 고정" 성격이다. 만약 어떤 시나리오가 예상과 다르게 동작하면(예: 첫 `check`가 통과 아닌 실패) 그것이 ArchUnit 1.2.1 freezing 의미와 우리 가정의 차이를 드러내는 신호 → 테스트가 아니라 가정을 재검토한다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*FreezingBehaviorTest'`
Expected: PASS (3 테스트 모두)

- [ ] **Step 5: 커밋**

```bash
git add platform-archrules/src/test/java/com/ryuqqq/platform/archrules/support/InMemoryViolationStore.java \
        platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FreezingBehaviorTest.java
git commit -m "test(archrules): freezing ratchet 메커니즘 self-test(in-memory store)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: frozen 번들 + 모듈 store 격리 + end-to-end self-test

브라운필드용 frozen 한 줄 표면(`HexagonalArchRulesFrozen`)을 src/main에 추가하고, 모듈 자체 `@AnalyzeClasses` 테스트로 end-to-end 검증한다. 기본 store(텍스트 파일)는 `archunit.properties`로 `build/`(gitignore)에 격리해 레포 오염을 막는다.

**Files:**
- Create: `platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRulesFrozen.java`
- Create: `platform-archrules/src/test/resources/archunit.properties`
- Test: `platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FrozenSuiteArchTest.java`

- [ ] **Step 1: 모듈 self-test용 store 격리 설정 작성**

`platform-archrules/src/test/resources/archunit.properties` 생성. store를 `build/` 아래로 보내고 최초 생성 허용.

```properties
# self-test 전용: frozen baseline을 build/(gitignore)에 격리한다. 소비측 템플릿이 아님.
freeze.store.default.path=build/archunit_store
freeze.store.default.allowStoreCreation=true
freeze.refreeze=false
```

- [ ] **Step 2: frozen 한 줄 표면 self-test 작성 (실패하는 테스트)**

`FrozenSuiteArchTest.java` 생성. compliant 픽스처(위반 없음)에 frozen 번들을 적용 → baseline은 빈 채로 기록되고 통과해야 한다. frozen 한 줄 표면이 소비측에서 동작함을 증명.

```java
package com.ryuqqq.platform.archrules;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchRules;

/**
 * frozen 한 줄 표면 self-test — ArchRules.in(HexagonalArchRulesFrozen.class)가
 * 소비측에서 동작함을 compliant 픽스처로 증명한다(위반 없음 → 빈 baseline, 통과).
 * store는 archunit.properties로 build/에 격리된다.
 */
@AnalyzeClasses(packages = "com.ryuqqq.platform.archrules.fixture.compliant")
class FrozenSuiteArchTest {

    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRulesFrozen.class);
}
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :platform-archrules:test --tests '*FrozenSuiteArchTest'`
Expected: FAIL — `HexagonalArchRulesFrozen` 미존재로 컴파일 실패.

- [ ] **Step 4: frozen 번들 구현**

`HexagonalArchRulesFrozen.java` 생성. raw 규칙 3종을 `FreezingArchRule.freeze(...)`로 감싸 `@ArchTest` 필드로 노출.

```java
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
 *     @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRulesFrozen.class);
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
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :platform-archrules:test --tests '*FrozenSuiteArchTest'`
Expected: PASS. 실행 후 `platform-archrules/build/archunit_store/`에 baseline 파일이 생기고(빈 위반), `git status`에 추적되지 않아야 한다(build/ gitignore).

- [ ] **Step 6: 레포 오염 없음 확인**

Run: `git status --porcelain platform-archrules/build`
Expected: 출력 없음(빈 결과) — store가 build/에 격리됨.

- [ ] **Step 7: 전체 모듈 테스트 회귀 확인**

Run: `./gradlew :platform-archrules:test`
Expected: PASS — 신규 4개 테스트 + 기존 self-test 모두 통과.

- [ ] **Step 8: 커밋**

```bash
git add platform-archrules/src/main/java/com/ryuqqq/platform/archrules/HexagonalArchRulesFrozen.java \
        platform-archrules/src/test/resources/archunit.properties \
        platform-archrules/src/test/java/com/ryuqqq/platform/archrules/FrozenSuiteArchTest.java
git commit -m "feat(archrules): frozen 번들 — FreezingArchRule ratchet 한 줄 입양 표면

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: README 갱신 — strict/frozen 표면·store 템플릿·ratchet 설명

소비측이 strict/frozen 중 무엇을, 어떻게 한 줄로 당겨쓰는지와 `archunit.properties` 템플릿·CI 운용을 문서화한다.

**Files:**
- Modify: `platform-archrules/README.md`

- [ ] **Step 1: "소비 방법" 섹션 교체**

기존 `## 소비 방법` 섹션(라인 45~62 근처, 규칙마다 `@ArchTest` 수동 선언 예시)을 아래로 교체한다.

````markdown
## 소비 방법 (한 줄 Apply)

규칙 묶음을 `ArchRules.in(...)` 한 줄로 당겨쓴다. 레거시 유무로 strict/frozen을 고른다.

### 그린필드 (레거시 없음) — strict

```java
@AnalyzeClasses(packages = "com.ryuqq.newservice")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRules.class);
}
```

처음부터 위반 0을 강제한다. 위반이 하나라도 있으면 빌드 실패.

### 브라운필드 (레거시 위반 있음) — frozen ratchet

```java
@AnalyzeClasses(packages = "com.ryuqq.marketplace")
class HexagonalArchitectureTest {
    @ArchTest static final ArchRules platform = ArchRules.in(HexagonalArchRulesFrozen.class);
}
```

첫 실행이 현재 위반을 baseline으로 **동결**하고, 이후엔 **신규 위반만** 실패시킨다. 위반을 고치면
baseline에서 자동 제거되어 되돌아갈 수 없다(ratchet) → 레거시를 통째로 고치지 않고도 입양 가능.

> 개별 규칙을 직접 참조하던 기존 방식(`HexagonalArchRules.HEXAGONAL_LAYERS` 등)도 그대로 동작한다.

### violation-store 설정 (frozen 사용 시 필수)

소비 레포 `src/test/resources/archunit.properties`:

```properties
freeze.store.default.path=archunit_store           # baseline 텍스트 파일 위치(레포에 커밋)
freeze.store.default.allowStoreCreation=true        # 로컬 최초 1회 baseline 생성용
freeze.refreeze=false                                # 신규 위반을 store에 자동 흡수 금지(=실패시킴)
```

운용:

1. 로컬에서 최초 1회 테스트 실행 → `archunit_store/`에 baseline 생성.
2. baseline 파일을 git에 커밋(소비 레포 소유).
3. CI에서는 우발적 baseline 생성을 막기 위해 `freeze.store.default.allowStoreCreation=false`로
   두는 것을 권장(신규 위반이 store에 조용히 흡수되지 않게 함).
````

- [ ] **Step 2: "제공 규칙" 표 아래에 표면 구분 한 줄 추가**

`## 제공 규칙 (확장점)` 섹션 끝에 추가:

```markdown
**입양 표면 2종:** `HexagonalArchRules`(strict, 그린필드) · `HexagonalArchRulesFrozen`(frozen ratchet, 브라운필드). 둘 다 `ArchRules.in(...)` 한 줄로 적용.
```

- [ ] **Step 3: "self-test" 섹션에 freezing 검증 한 줄 추가**

`## self-test` 섹션의 테이블 아래(라인 80 근처)에 추가:

```markdown
freezing 동작은 `FreezingBehaviorTest`가 in-memory store로 검증한다(레거시 동결·신규 위반 차단·prune 후 회귀 차단). frozen/strict 한 줄 표면은 `FrozenSuiteArchTest`·`StrictSuiteArchTest`가 compliant 픽스처로 end-to-end 확인한다.
```

- [ ] **Step 4: 렌더링·링크 확인**

Run: `grep -n "ArchRules.in\|archunit.properties\|HexagonalArchRulesFrozen" platform-archrules/README.md`
Expected: 새 표면·store 설정이 README에 반영됨(여러 줄 출력).

- [ ] **Step 5: 커밋**

```bash
git add platform-archrules/README.md
git commit -m "docs(archrules): 한 줄 Apply·frozen ratchet·store 템플릿 문서화

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 최종 검증

- [ ] **전체 모듈 빌드·테스트**

Run: `./gradlew :platform-archrules:build`
Expected: BUILD SUCCESSFUL — 컴파일 + 신규 4개 테스트(StrictSuiteArchTest, FreezingBehaviorTest 3건, FrozenSuiteArchTest) + 기존 self-test 통과.

- [ ] **레포 오염 없음 최종 확인**

Run: `git status --porcelain`
Expected: 추적되지 않은 `build/` 산출물 없음(archunit_store가 build/에 격리됨).

---

## 비목표 (이 계획 범위 밖 — Spec 2)

- 기존 3종 매처 정밀화(정확도/표현력).
- 신규 규칙 카탈로그(ADR-0003 드리프트 표준 강제·네이밍·순환의존·포트/어댑터).
- severity/우선순위 다이얼.
- 발행 Gradle convention plugin(ADR-0002 토폴로지 defer).
