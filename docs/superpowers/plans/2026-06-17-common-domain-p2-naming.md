# common-domain P2 네이밍 수렴 Implementation Plan

> **스냅샷:** 2026-06-17

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ADR-0007 결정대로 `PageMeta.totalCount`→`totalElements`, `DeletionStatus`를 `deletedAt` 단일필드(boolean 제거)로 변경한다 (errorCode는 변경 없음).

**Architecture:** breaking 변경이나 platform-common-domain 페이징/삭제 VO의 외부 소비처가 0(crawling은 자체 VO 사용)이라 내부 `CommonVoTest`만 동반 변경. ADR-0004 정책대로 루트 CHANGELOG 갱신.

**Tech Stack:** Java record, JUnit5, AssertJ.

## Global Constraints

- ADR-0007 결정 범위만: **PageMeta `totalElements`**, **DeletionStatus `deletedAt`+boolean 제거**. `DomainException.errorCode()`는 **변경 금지**(이미 정합). `ErrorCode` 인터페이스 get→no-get은 범위 밖.
- `totalPages`는 record 필드가 아니라 기존 계산 메서드 `totalPages()` 유지.
- 순수 자바(프레임워크 import 금지). record/enum 컨벤션 유지.
- breaking → 루트 `CHANGELOG.md` Unreleased에 Changed/Removed 기록(ADR-0004).
- 모듈 테스트: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`.

---

## File Structure

- **Modify** `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java` — `totalCount`→`totalElements`.
- **Modify** `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/DeletionStatus.java` — boolean 제거, `deletedAt` 팩토리.
- **Modify** `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java` — 두 변경의 호출부 업데이트.
- **Modify** `CHANGELOG.md` (루트) — Unreleased Changed/Removed.

---

## Task 1: PageMeta totalCount → totalElements

**Files:**
- Modify: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java`
- Modify: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java`

**Interfaces:**
- Consumes: (없음)
- Produces: `PageMeta(int page, int size, long totalElements)` record. `of(int,int,long)`·`totalElements()`·`totalPages()`·`hasNext()`·`hasPrevious()`·`empty()`·`empty(int)` 유지(이름만 totalElements).

- [ ] **Step 1: 테스트 호출부를 totalElements로 변경 (RED 유도)**

`CommonVoTest.java`에서 `totalCount` → `totalElements` 치환 (4곳):
- line 196 부근: `assertThat(page.meta().totalCount())` → `assertThat(page.meta().totalElements())`
- line 438·447 부근: `assertThat(meta.totalCount()).isZero()` → `assertThat(meta.totalElements()).isZero()`
- line 371 부근 `@DisplayName("PageMeta는 음수 page·0 이하 size·음수 totalCount를 거부한다")` → `...음수 totalElements를 거부한다`

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: 컴파일 실패 — `PageMeta`에 `totalElements()` 없음(아직 `totalCount`).

- [ ] **Step 3: PageMeta 구현 변경**

`PageMeta.java`를 아래로 교체(`totalCount`→`totalElements`, Javadoc 포함):
```java
package com.ryuqqq.platform.common.vo;

/**
 * offset 페이징 응답 메타.
 *
 * @param page 0-based page index
 * @param size page size
 * @param totalElements 전체 건수
 */
public record PageMeta(int page, int size, long totalElements) {

    private static final int DEFAULT_SIZE = 20;

    public PageMeta {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative: " + totalElements);
        }
    }

    public static PageMeta of(int page, int size, long totalElements) {
        return new PageMeta(page, size, totalElements);
    }

    public static PageMeta empty(int size) {
        return of(0, size, 0);
    }

    public static PageMeta empty() {
        return empty(DEFAULT_SIZE);
    }

    public int totalPages() {
        long pages = totalElements / size;
        if (totalElements % size != 0) {
            pages++;
        }
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/PageMeta.java platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java
git commit -m "feat(common-domain)!: PageMeta.totalCount → totalElements (ADR-0007, breaking)"
```

---

## Task 2: DeletionStatus boolean 제거 + deletedAt 팩토리

**Files:**
- Modify: `platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/DeletionStatus.java`
- Modify: `platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java`

**Interfaces:**
- Consumes: (없음)
- Produces: `DeletionStatus(Instant deletedAt)` record(단일 필드). `active()`·`deletedAt(Instant)`(정적 팩토리)·`markDeleted(Instant)`·`restore()`·`isActive()`·`isDeleted()`. `deleted(Instant)` 팩토리와 `boolean deleted()` 접근자는 제거됨.

- [ ] **Step 1: 테스트(DeletionStatusTest) 변경 (RED 유도)**

`CommonVoTest.java`의 `@Nested DeletionStatusTest`(line 328~)를 아래로 교체:
```java
    @Nested
    @DisplayName("DeletionStatus")
    class DeletionStatusTest {

        @Test
        @DisplayName("active → markDeleted → restore")
        void lifecycle() {
            DeletionStatus active = DeletionStatus.active();
            assertThat(active.isActive()).isTrue();
            assertThat(active.isDeleted()).isFalse();

            Instant now = Instant.parse("2026-06-17T00:00:00Z");
            DeletionStatus deleted = active.markDeleted(now);
            assertThat(deleted.isDeleted()).isTrue();
            assertThat(deleted.deletedAt()).isEqualTo(now);

            assertThat(deleted.restore().isActive()).isTrue();
        }

        @Test
        @DisplayName("deletedAt 팩토리는 null을 거부한다")
        void deletedAtRejectsNull() {
            assertThatNullPointerException().isThrownBy(() -> DeletionStatus.deletedAt(null));
        }
    }
```
(기존 `lifecycle` 테스트의 메서드명과 검증 줄을 위로 맞추고, 기존 검증 테스트 2개 — `new DeletionStatus(true, null)`/`new DeletionStatus(false, now)` 거부 — 는 boolean 제거로 무의미해지므로 삭제. 그 자리를 `deletedAtRejectsNull`이 대체.)

상단 import에 없으면 추가:
```java
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
```
(이미 이번 트랙에서 다른 모듈이 썼으나 common-domain CommonVoTest엔 없을 수 있음 — 없으면 추가, 있으면 생략.)

> 참고: `new DeletionStatus(true, null)` 류 검증 테스트가 `@Nested PageMetaTest`나 다른 곳(line 379·385)에 있으면 그것도 함께 삭제 — boolean 생성자가 사라져 컴파일 불가.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: 컴파일 실패 — `DeletionStatus`에 `isDeleted()`·`deletedAt(Instant)` 팩토리 없음, `deleted(boolean,Instant)` 생성자 참조 불가.

- [ ] **Step 3: DeletionStatus 구현 변경**

`DeletionStatus.java`를 아래로 교체:
```java
package com.ryuqqq.platform.common.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * Soft delete 상태. Aggregate의 {@code delete(now)} / {@code restore()}와 persistence 필터가 공유한다.
 * 삭제 여부는 {@code deletedAt != null}로 파생한다(ADR-0007: boolean 필드 제거).
 *
 * @param deletedAt 삭제 시각 (active이면 null)
 */
public record DeletionStatus(Instant deletedAt) {

    public static DeletionStatus active() {
        return new DeletionStatus(null);
    }

    public static DeletionStatus deletedAt(Instant deletedAt) {
        return new DeletionStatus(Objects.requireNonNull(deletedAt, "deletedAt must not be null"));
    }

    public DeletionStatus markDeleted(Instant deletedAt) {
        return deletedAt(deletedAt);
    }

    public DeletionStatus restore() {
        return active();
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew :platform-common-domain:test --tests '*CommonVoTest*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add platform-common-domain/src/main/java/com/ryuqqq/platform/common/vo/DeletionStatus.java platform-common-domain/src/test/java/com/ryuqqq/platform/common/vo/CommonVoTest.java
git commit -m "feat(common-domain)!: DeletionStatus deletedAt 팩토리 + boolean 제거 (ADR-0007, breaking)"
```

---

## Task 3: CHANGELOG 갱신 (ADR-0004 정책)

**Files:**
- Modify: `CHANGELOG.md` (레포 루트)

**Interfaces:**
- Consumes: (없음)
- Produces: (없음 — 문서)

- [ ] **Step 1: CHANGELOG 확인 후 Unreleased에 항목 추가**

`CHANGELOG.md`를 Read해 Keep a Changelog 형식(Unreleased 섹션)을 확인하고, Unreleased 아래 `### Changed`·`### Removed`에 추가(섹션 없으면 생성):
```markdown
### Changed
- `platform-common-domain`: `PageMeta.totalCount` → `totalElements` (Spring Data 표준 정합, ADR-0007). **breaking**.
- `platform-common-domain`: `DeletionStatus`를 `deletedAt` 단일 필드 record로 정리, 정적 팩토리 `deleted(Instant)` → `deletedAt(Instant)` (ADR-0007). **breaking**.

### Removed
- `platform-common-domain`: `DeletionStatus`의 `boolean deleted` 컴포넌트·`deleted()` 접근자 제거 (`isDeleted()` = `deletedAt != null`로 파생, ADR-0003/0007).
```
(기존 Unreleased 항목이 있으면 그 아래 append. 정확한 들여쓰기·헤더 레벨은 기존 CHANGELOG와 일치시킬 것.)

- [ ] **Step 2: 커밋**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): common-domain P2 네이밍 breaking 변경 기록 (ADR-0007)"
```

---

## Task 4: 모듈 전체 빌드 — 회귀·게이트 확인

**Files:** (없음 — 검증만)

- [ ] **Step 1: 모듈 전체 빌드**

Run: `./gradlew :platform-common-domain:build`
Expected: BUILD SUCCESSFUL — 전체 CommonVoTest + Spotless + SpotBugs 통과. (Spotless 위반 시 `spotlessApply` 후 그 포맷 변경만 `style: spotless 포맷` 별도 커밋하고 재빌드.)

- [ ] **Step 2: archrules 회귀 확인 (common-domain을 fixture로 쓰므로)**

Run: `./gradlew :platform-archrules:test`
Expected: BUILD SUCCESSFUL — common-domain 변경이 archrules dogfood/fixture에 회귀 없음.

---

## Self-Review 결과

**Spec coverage (ADR-0007):**
- 결정1 totalCount→totalElements — Task 1 ✓
- 결정2 deletedAt+boolean제거 — Task 2 ✓
- 결정3 errorCode 유지 — 변경 안 함(범위 명시) ✓
- totalPages 메서드 유지 — Task 1 Step 3(필드 아님) ✓
- breaking → CHANGELOG — Task 3 ✓
- ErrorCode 인터페이스 범위 밖 — 계획에 포함 안 함 ✓

**Placeholder scan:** 모든 코드 스텝에 완전한 코드. CHANGELOG는 기존 형식 확인 후 append(정확 들여쓰기 위임 — 단 추가 항목 텍스트는 명시).

**Type consistency:** PageMeta `totalElements`가 record 컴포넌트·of·totalPages·검증·테스트 호출 전부 일관. DeletionStatus `deletedAt(Instant)` 팩토리·`isDeleted()`·`isActive()`가 테스트 호출과 일치. `empty()`/`empty(int)`(이번 트랙 #47 추가분)는 `of(0,size,0)` 유지 — totalElements=0. boolean 생성자 제거로 기존 검증 테스트 2개 삭제 명시(컴파일 불가 회피).
