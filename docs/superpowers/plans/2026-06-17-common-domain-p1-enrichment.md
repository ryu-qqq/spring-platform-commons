# platform-common-domain P1 편의 메서드 흡수 Implementation Plan

> **스냅샷:** 2026-06-17

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 두 게이트(seam + commonality)를 통과한 8개 편의 메서드를 `PageRequest`·`PageMeta`·`SortDirection`에 비파괴 추가한다.

**Architecture:** 기존 record VO·enum에 메서드만 추가(컴포넌트·상수 불변 → 바이너리/소스 호환). 테스트는 기존 `CommonVoTest`의 `@Nested` 타입별 그룹 패턴을 따른다.

**Tech Stack:** Java (record/enum), JUnit5, AssertJ. common-domain은 프레임워크 비의존 순수 자바라 단위 테스트만.

## Global Constraints

- **전부 비파괴 추가만** — record 컴포넌트·enum 상수를 바꾸지 않는다. 메서드/정적 팩토리/상수 추가만.
- **`SortDirection.fromString`은 `Locale.ROOT` 강제** — `value.trim().toUpperCase(Locale.ROOT)`. 엄격 파싱(정확한 enum명만, 관용표기 불허), null/blank/실패는 `defaultDirection()` 폴백.
- **`defaultDirection()` = DESC**, **기본 size = 20**(`PageRequest.DEFAULT_SIZE`·`PageMeta.DEFAULT_SIZE` 동일값).
- **`isDescending()` 흡수 안 함** (실호출 1곳 — 범위 밖).
- **순수 도메인** — Spring/JPA/Jackson/Lombok import 금지(`DomainConventionRules.DOMAIN_FRAMEWORK_FREE`). `java.util.Locale`만 추가 허용.
- 테스트는 기존 `CommonVoTest` 단일 파일에 `@Nested`로 추가(새 테스트 파일 만들지 않음 — 기존 패턴).
- 모듈 테스트: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`.

---

## File Structure

- **Modify** `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/SortDirection.java` — enum에 4개 메서드 + `java.util.Locale` import.
- **Modify** `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageRequest.java` — record에 2개 메서드.
- **Modify** `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java` — record에 `DEFAULT_SIZE` 상수 + 2개 팩토리.
- **Modify** `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java` — `@Nested SortDirectionTest`·`@Nested PageMetaTest` 추가, 기존 `@Nested PageRequestTest`에 `@Test` 2개 추가.
- **Modify** `docs/common-domain-enrichment-from-crawling.md` — 후보 D NPE 오진단 1줄 정정(docs only).

---

## Task 1: SortDirection 편의 메서드 4종

**Files:**
- Modify: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/SortDirection.java`
- Modify: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java`

**Interfaces:**
- Consumes: (없음)
- Produces: `SortDirection.defaultDirection() -> SortDirection` (static), `SortDirection.isAscending() -> boolean`, `SortDirection.reverse() -> SortDirection`, `SortDirection.fromString(String) -> SortDirection` (static).

- [ ] **Step 1: 실패하는 테스트 작성 — CommonVoTest 클래스 끝(마지막 `}` 직전)에 `@Nested` 추가**

```java
    @Nested
    @DisplayName("SortDirection")
    class SortDirectionTest {

        @Test
        @DisplayName("defaultDirection은 DESC")
        void defaultDirection() {
            assertThat(SortDirection.defaultDirection()).isEqualTo(SortDirection.DESC);
        }

        @Test
        @DisplayName("isAscending은 ASC에서만 true")
        void isAscending() {
            assertThat(SortDirection.ASC.isAscending()).isTrue();
            assertThat(SortDirection.DESC.isAscending()).isFalse();
        }

        @Test
        @DisplayName("reverse는 ASC↔DESC 반전")
        void reverse() {
            assertThat(SortDirection.ASC.reverse()).isEqualTo(SortDirection.DESC);
            assertThat(SortDirection.DESC.reverse()).isEqualTo(SortDirection.ASC);
        }

        @Test
        @DisplayName("fromString은 정확한 enum명만 허용(대소문자·공백 무시), 그 외 DESC 폴백")
        void fromString() {
            assertThat(SortDirection.fromString("asc")).isEqualTo(SortDirection.ASC);
            assertThat(SortDirection.fromString("  DESC ")).isEqualTo(SortDirection.DESC);
            assertThat(SortDirection.fromString(null)).isEqualTo(SortDirection.DESC);
            assertThat(SortDirection.fromString("")).isEqualTo(SortDirection.DESC);
            assertThat(SortDirection.fromString("   ")).isEqualTo(SortDirection.DESC);
            assertThat(SortDirection.fromString("ASCENDING")).isEqualTo(SortDirection.DESC);
        }
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: 컴파일 실패 (`defaultDirection`/`isAscending`/`reverse`/`fromString` 메서드 없음).

- [ ] **Step 3: SortDirection 구현 — 파일 전체를 아래로 교체**

```java
package com.ryuqqq.platform.common.vo;

import java.util.Locale;

/** 정렬 방향. */
public enum SortDirection {
    ASC,
    DESC;

    /** 기본 정렬 방향(최신순 관행). */
    public static SortDirection defaultDirection() {
        return DESC;
    }

    /** 오름차순이면 true. */
    public boolean isAscending() {
        return this == ASC;
    }

    /** 방향 반전(ASC↔DESC). */
    public SortDirection reverse() {
        return this == ASC ? DESC : ASC;
    }

    /**
     * 문자열을 방향으로 파싱한다. null/blank나 유효하지 않은 값은 {@link #defaultDirection()}으로 폴백.
     * 정확한 enum명(대소문자·앞뒤 공백 무시)만 허용하며, 관용표기는 받지 않는다(어댑터 책임).
     */
    public static SortDirection fromString(String value) {
        if (value == null || value.isBlank()) {
            return defaultDirection();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return defaultDirection();
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/SortDirection.java platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java
git commit -m "feat(common-domain): SortDirection 편의 메서드(defaultDirection·isAscending·reverse·fromString)"
```

---

## Task 2: PageRequest 편의 메서드 2종

**Files:**
- Modify: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageRequest.java`
- Modify: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java`

**Interfaces:**
- Consumes: 기존 `PageRequest.DEFAULT_SIZE`(private, =20), `PageRequest.of(int,int)`.
- Produces: `PageRequest.defaultPage() -> PageRequest` (static), `PageRequest.isFirst() -> boolean`.

- [ ] **Step 1: 실패하는 테스트 작성 — 기존 `@Nested class PageRequestTest`(CommonVoTest 내부, `@DisplayName("firstPage 기본 size는 20")` 테스트 다음)에 `@Test` 2개 추가**

```java
        @Test
        @DisplayName("defaultPage는 page0·size20")
        void defaultPage() {
            assertThat(PageRequest.defaultPage().page()).isZero();
            assertThat(PageRequest.defaultPage().size()).isEqualTo(20);
        }

        @Test
        @DisplayName("isFirst는 page0에서만 true")
        void isFirst() {
            assertThat(PageRequest.of(0, 10).isFirst()).isTrue();
            assertThat(PageRequest.of(1, 10).isFirst()).isFalse();
        }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: 컴파일 실패 (`defaultPage`/`isFirst` 없음).

- [ ] **Step 3: PageRequest 구현 — 기존 `offset()` 메서드 다음(클래스 닫는 `}` 직전)에 추가**

```java
    public static PageRequest defaultPage() {
        return of(0, DEFAULT_SIZE);
    }

    public boolean isFirst() {
        return page == 0;
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageRequest.java platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java
git commit -m "feat(common-domain): PageRequest.defaultPage·isFirst 편의 메서드"
```

---

## Task 3: PageMeta empty 팩토리 2종

**Files:**
- Modify: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java`
- Modify: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java`

**Interfaces:**
- Consumes: 기존 `PageMeta.of(int,int,long)`, `PageMeta.hasNext()`.
- Produces: `PageMeta.empty(int) -> PageMeta` (static), `PageMeta.empty() -> PageMeta` (static). 신규 `private static final int DEFAULT_SIZE = 20`.

- [ ] **Step 1: 실패하는 테스트 작성 — CommonVoTest 클래스 끝(마지막 `}` 직전, Task 1의 SortDirectionTest 다음)에 `@Nested` 추가**

```java
    @Nested
    @DisplayName("PageMeta")
    class PageMetaTest {

        @Test
        @DisplayName("empty()는 page0·size20·total0")
        void emptyDefault() {
            PageMeta meta = PageMeta.empty();
            assertThat(meta.page()).isZero();
            assertThat(meta.size()).isEqualTo(20);
            assertThat(meta.totalCount()).isZero();
        }

        @Test
        @DisplayName("empty(size)는 주어진 size·page0·total0")
        void emptyWithSize() {
            PageMeta meta = PageMeta.empty(50);
            assertThat(meta.page()).isZero();
            assertThat(meta.size()).isEqualTo(50);
            assertThat(meta.totalCount()).isZero();
        }

        @Test
        @DisplayName("empty는 hasNext false")
        void emptyHasNoNext() {
            assertThat(PageMeta.empty().hasNext()).isFalse();
        }
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: 컴파일 실패 (`empty` 없음).

- [ ] **Step 3: PageMeta 구현 — record 본문 맨 위(compact constructor `public PageMeta {` 직전)에 상수 추가**

```java
    private static final int DEFAULT_SIZE = 20;
```

그리고 기존 `hasPrevious()` 메서드 다음(클래스 닫는 `}` 직전)에 팩토리 추가:

```java
    public static PageMeta empty(int size) {
        return of(0, size, 0);
    }

    public static PageMeta empty() {
        return empty(DEFAULT_SIZE);
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java
git commit -m "feat(common-domain): PageMeta.empty·empty(int) 팩토리"
```

---

## Task 4: 근거 문서 NPE 오진단 정정 (docs only)

**Files:**
- Modify: `docs/common-domain-enrichment-from-crawling.md:63`

**Interfaces:**
- Consumes: (없음)
- Produces: (없음 — 문서)

- [ ] **Step 1: 63행의 오진단 문구 정정**

현재 63행(`| **null args 처리** | ...`)의 platform 열 서술이 "null Map을 그대로 보존 → `args()` 호출 시 NPE"인데, 실제 코드(`DomainException.java:24,30`)는 이미 `Map.copyOf(args)`를 호출하므로 null 전달 시 **생성자**에서 NPE가 난다. 해당 셀의 "platform" 열 텍스트를 다음으로 교체:

```
`(EC,msg,Map)` 에 null Map 전달 시 생성자의 `Map.copyOf(null)` 에서 NPE
```

(즉 "`args()` 호출 시 NPE" → "생성자 `Map.copyOf(null)`에서 NPE"로 발생 지점 정정. 제안 열의 "null args → 빈 Map 정규화"는 그대로 유지하되, 이는 별도 코드리뷰 트랙 — 본 작업 범위 밖임을 같은 셀에 한 구절로 명시.)

- [ ] **Step 2: 변경 확인 (빌드 무관 — docs)**

Run: `grep -n "Map.copyOf(null)" docs/common-domain-enrichment-from-crawling.md`
Expected: 63행 부근에 정정된 문구가 보임.

- [ ] **Step 3: 커밋**

```bash
git add docs/common-domain-enrichment-from-crawling.md
git commit -m "docs: common-domain 보강 근거 문서 NPE 발생지점 오진단 정정"
```

---

## Task 5: 모듈 전체 빌드 — 회귀·게이트 확인

**Files:** (없음 — 검증만)

- [ ] **Step 1: 모듈 전체 빌드**

Run: `./gradlew :platform-common-domain:build`
Expected: BUILD SUCCESSFUL — 기존 테스트 전부 통과 + Spotless 포맷 게이트 + SpotBugs 통과. (각 Task가 모듈 테스트를 통과시켰으므로 여기서는 회귀·정적분석 게이트만 확인. Spotless 위반이 남아 있으면 `./gradlew :platform-common-domain:spotlessApply` 적용 후 그 포맷 변경만 별도 커밋 `style: spotless 포맷`으로 남기고 재빌드.)

- [ ] **Step 2: archrules 자기점검 회귀 없음 확인 (선택)**

Run: `./gradlew :platform-archrules:test`
Expected: BUILD SUCCESSFUL — common-domain 변경이 archrules dogfood(`DomainHealthReporter` 등)에 회귀를 일으키지 않음.

---

## Self-Review 결과

**Spec coverage:**
- SortDirection 4개(`isAscending`/`reverse`/`defaultDirection`/`fromString`) — Task 1 ✓
- PageRequest 2개(`defaultPage`/`isFirst`) — Task 2 ✓
- PageMeta 2개(`empty`/`empty(int)`) — Task 3 ✓
- `fromString` Locale.ROOT·엄격 파싱·폴백 — Task 1 Step 3 ✓
- 기본값(DESC·size 20) — Task 1·2·3 ✓
- 후보 D 문서 정정 — Task 4 ✓
- 비목표(죽은 메서드·MAX_SIZE·isDescending·P2·P3) — 계획에 포함 안 함 ✓
- 비파괴·순수 도메인 제약 — Global Constraints + 각 Task ✓

**Placeholder scan:** 모든 코드 스텝에 완전한 코드 있음. "TBD"/"적절히" 없음.

**Type consistency:** `DEFAULT_SIZE`=20 (PageRequest 기존 private / PageMeta 신규 private, 동일값). `empty()`→`empty(int)`→`of(0,size,0)` 체인 일관. `fromString`/`defaultDirection` 시그니처 Task 1 정의와 테스트 호출 일치. `empty().hasNext()`==false는 `of(0,size,0)`의 `totalPages()=0` → `hasNext()`=`0+1<0`=false로 정합.
