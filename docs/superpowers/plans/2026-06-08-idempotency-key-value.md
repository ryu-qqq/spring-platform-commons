# IdempotencyKeyValue VO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 멱등 키의 검증된 불투명 값을 감싸는 단일 필드 VO `IdempotencyKeyValue` 를 `platform-common-domain` 에 추가한다.

**Architecture:** 순수 도메인(프레임워크-프리) record. 단일 `value` 필드, compact 생성자에서 blank/null 가드(`IllegalArgumentException`), `toString()` 은 raw value 반환. namespace 결합·파생 팩토리는 비목표(소비측/Javadoc 규약). 형제 VO `CacheKey`/`LockKey`/`DateRange` 와 같은 `vo` 패키지.

**Tech Stack:** Java record, JUnit 5, AssertJ. 빌드: Gradle.

**Spec:** `docs/superpowers/specs/2026-06-08-idempotency-key-value-design.md`

---

### Task 1: IdempotencyKeyValue VO (TDD)

**Files:**
- Create: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValue.java`
- Test: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValueTest.java`

- [ ] **Step 1: Write the failing test**

`platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValueTest.java`:

```java
package com.ryuqqq.platform.common.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyValueTest {

    @Test
    @DisplayName("정상 값이면 value() 가 원문을 그대로 반환한다")
    void acceptsNonBlankValue() {
        IdempotencyKeyValue key = new IdempotencyKeyValue("abc-123");
        assertThat(key.value()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("null value 는 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> new IdempotencyKeyValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("빈 문자열 value 는 거부한다")
    void rejectsEmpty() {
        assertThatThrownBy(() -> new IdempotencyKeyValue(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공백 only value 는 거부한다")
    void rejectsBlank() {
        assertThatThrownBy(() -> new IdempotencyKeyValue("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 value 두 인스턴스는 구조적으로 동등하다")
    void structuralEquality() {
        assertThat(new IdempotencyKeyValue("k"))
                .isEqualTo(new IdempotencyKeyValue("k"))
                .hasSameHashCodeAs(new IdempotencyKeyValue("k"));
        assertThat(new IdempotencyKeyValue("k"))
                .isNotEqualTo(new IdempotencyKeyValue("other"));
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 하지 않고 보존한다 — 정규화 없음")
    void doesNotNormalize() {
        assertThat(new IdempotencyKeyValue(" abc ").value()).isEqualTo(" abc ");
        assertThat(new IdempotencyKeyValue(" abc "))
                .isNotEqualTo(new IdempotencyKeyValue("abc"));
    }

    @Test
    @DisplayName("toString 은 record 기본형이 아닌 raw value 를 반환한다")
    void toStringReturnsRawValue() {
        assertThat(new IdempotencyKeyValue("abc-123")).hasToString("abc-123");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform-common-domain:test --tests 'com.ryuqqq.platform.common.vo.IdempotencyKeyValueTest'`
Expected: 컴파일 실패 — `IdempotencyKeyValue` 심볼 없음.

- [ ] **Step 3: Write minimal implementation**

`platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValue.java`:

```java
package com.ryuqqq.platform.common.vo;

/**
 * 멱등 키의 검증된 불투명 값.
 *
 * <p>외부(클라이언트 헤더 등)에서 받은 불투명 키 문자열을 타입으로 감싸 blank/null 만 막는다.
 * SDK는 값을 정규화(trim/대소문자/charset)하지 않으며, 동등성은 원문 기준이다.
 *
 * <p><b>네임스페이스 규약:</b> 같은 키가 컨텍스트 간 충돌하지 않도록 소비측이
 * {@code PREFIX:value}(예: {@code "payment:abc-123"}) 형태로 결합해 보관할 것을 권장한다.
 * 결합 규칙은 도메인 정책이므로 이 타입이 강제하지 않는다.
 *
 * <p><b>비목표:</b> 키 파생/생성 팩토리(forNew·SHA-256 derive 등), namespace 코어 필드,
 * charset/length 제약, trim 정규화, 영속화 매핑 — 모두 호출자 책임.
 *
 * <pre>{@code
 * var key = new IdempotencyKeyValue(request.getHeader("Idempotency-Key"));
 * String stored = "payment:" + key; // 규약에 따른 namespacing은 소비측
 * }</pre>
 */
public record IdempotencyKeyValue(String value) {

    public IdempotencyKeyValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :platform-common-domain:test --tests 'com.ryuqqq.platform.common.vo.IdempotencyKeyValueTest'`
Expected: PASS (7 테스트).

- [ ] **Step 5: Full module build (archrules 가드 포함)**

Run: `./gradlew :platform-common-domain:build`
Expected: BUILD SUCCESSFUL. `platform-archrules` 의 `DOMAIN_FRAMEWORK_FREE` 규칙 통과(순수 record, 프레임워크 의존 없음).

- [ ] **Step 6: Commit**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValue.java \
        platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/IdempotencyKeyValueTest.java
git commit -m "feat(idempotency): IdempotencyKeyValue 단일값 VO (P2-4)

value-only record + blank/null 가드 + toString(raw). namespace/팩토리는 비목표
(Javadoc 규약·소비측). 감사 보고서 line 104 최소 seam에 정렬.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- value-only record + blank/null 가드 → Task 1 Step 3 ✅
- toString raw value → Step 3 + 테스트 `toStringReturnsRawValue` ✅
- 정규화 없음(동등성 원문 기준) → 테스트 `doesNotNormalize` ✅
- 팩토리 없음 → 구현에 `of()` 부재 ✅
- 비목표 Javadoc 명문화 → Step 3 Javadoc ✅
- 프레임워크-프리 → Step 5 archrules 빌드 가드 ✅
- 입양 리팩터(MP·FF 위임)는 deferred → 계획 범위 외(의도) ✅

**Placeholder scan:** 없음 — 모든 step에 실제 코드·명령·기대값 포함.

**Type consistency:** `IdempotencyKeyValue`, `value()`, `IllegalArgumentException`, 메시지 `"...must not be blank"` 가 spec·test·impl 전반 일치.
