# 0007. common-domain P2 네이밍 수렴 — totalElements · deletedAt · errorCode

- 상태: Accepted
- 날짜: 2026-06-17

## 맥락 (Context)

`platform-common-domain` v0.1.0이 connectly(`com.connectly.platform`)로 발행된 상태에서, **crawling-domain 입양 피드백**으로 3개 타입의 네이밍 충돌이 드러났다.

1. **페이징 전체 건수** — `PageMeta.totalCount` 필드명.
2. **삭제 상태** — `DeletionStatus`의 팩토리 `deleted(Instant)` + `boolean deleted` 필드.
3. **예외 접근자** — `DomainException.errorCode()` vs `getErrorCode()` 진영.

세 변경 모두 **breaking**이라 방향을 ADR로 확정한다. pre-1.0(ADR-0004)이라 minor에서 breaking이 허용되며, 소비자가 거의 없는 **지금이 변경 적기**다. 드리프트는 "더 나은 쪽으로 수렴"(ADR-0003)한다.

## 결정 동인 (Decision Drivers)

1. **실제 입양처 정합** — fleet 단순 다수결이 아니라, 실제로 이 타입을 import해 쓰는 소비처와의 정합을 우선한다.
2. **breaking 비용** — pre-1.0이라 허용되나, 그래도 소비처 마이그레이션 비용이 낮은 쪽을 고른다.
3. **외부 표준 정합** — Spring Data 등 사실상 표준 네이밍과 일치하면 학습 비용이 낮다.
4. **선행 ADR 정합** — ADR-0003(soft-delete는 deletedAt만, boolean 제거)과 이미 어긋난 부분을 바로잡는다.
5. **platform record no-get 컨벤션** — record 접근자는 `get` 접두사 없이.

## 검토한 옵션 (Considered Options)

### ① 페이징 전체 건수 (PageMeta)

- **A) `totalCount` → `totalElements`(채택)** — Spring Data `Page.getTotalElements()` 사실상 표준, fleet 9:0 우세, 현재 페이징 VO 소비처 0건이라 마이그레이션 비용 사실상 0.
- B) `totalCount` 유지 — churn 0이나 외부 표준과 어긋남 잔존. 기각.

### ② 삭제 상태 (DeletionStatus)

- **A) `deleted(Instant)` → `deletedAt(Instant)` + `boolean deleted` 필드 제거(채택)** — record를 `DeletionStatus(Instant deletedAt)` 단일 필드로 정리하고 `isDeleted()`는 `deletedAt != null`로 파생. ADR-0003이 "soft-delete는 deletedAt만, boolean 제거"를 못박았는데 현 VO가 boolean을 들고 있어 ADR-0003과 어긋난 상태였다 → 이번에 VO에도 적용해 정합. 인자 의미(`Instant`)와 이름(`deletedAt`)이 일치하고 crawling과 정합.
- B) 현행 boolean + `deleted(Instant)` 유지 — ADR-0003 위반 잔존. 기각.

### ③ 예외 접근자 (DomainException)

- **A) `errorCode()` 유지(채택)** — `getErrorCode` 진영이 fleet 다수(10:3)이나 그 진영은 **비입양 진영**이고, 실제 유일 입양처 crawling-domain은 이미 `errorCode()`를 쓴다 → 유지가 breaking 0. platform record no-get 컨벤션과도 정합.
- B) `getErrorCode()`로 변경(fleet 다수 추종) — 실제 입양처를 깨뜨림 + no-get 컨벤션 위반. 기각.

## 결정 (Decision Outcome)

사람이 결정함. 세 건 모두 확정.

1. **`PageMeta.totalCount` → `totalElements`로 변경.** Spring Data 사실상 표준 정합, fleet 9:0, 소비처 0건이라 마이그레이션 비용 사실상 0. (`totalPages`는 record 필드가 아니라 기존대로 계산 메서드 `totalPages()` 유지 — 이번 범위.)

2. **`DeletionStatus`: 팩토리 `deleted(Instant)` → `deletedAt(Instant)`로 변경 + `boolean deleted` 필드 제거.** record를 `DeletionStatus(Instant deletedAt)` 단일 필드로 정리하고 `isDeleted()`는 `deletedAt != null`로 파생. ADR-0003과 어긋나 있던 VO를 정합시킨다. (`markDeleted(Instant)` 인스턴스 메서드도 함께 정리.)

3. **`DomainException.errorCode()`: 변경 없이 유지.** fleet 다수가 아니라 실제 입양처(crawling-domain) 정합을 우선한다 — 이 ADR에서 "fleet 단순 다수결이 아니라 실제 입양처 정합을 우선"하는 원칙을 확립. (단 `ErrorCode` 인터페이스의 `getCode()`/`getHttpStatus()`/`getMessage()` no-get 통일은 추가 breaking이라 이번 범위 밖 — 미해결로 명시.)

## 결과 (Consequences)

**긍정:**

- 페이징 전체 건수가 Spring Data 사실상 표준(`totalElements`)과 정합한다.
- `DeletionStatus`가 ADR-0003(deletedAt만, boolean 제거)과 비로소 정합한다.
- 예외 접근자가 실제 입양처와 0-breaking으로 유지된다.

**비용/후속:**

- ①·②는 breaking → `platform-common-domain` **minor 버전업(다음 버전)** + CHANGELOG(Changed/Removed) + `com.ryuqqq`(작업) · `com.connectly`(발행본) **양쪽 동기화 후 재승격** 필요(ADR-0004 정책).
- ③은 변경 0 — 입양처가 이미 정합.
- ABI 게이트는 1.0까지 defer(ADR-0004)라 이 breaking이 게이트에 막히지 않는다.
- 후속 구현: P2 코드 변경(네이밍 적용)은 별도 작업, 그 후 connectly 재승격.

**미해결:**

- `ErrorCode` 인터페이스 get→no-get 통일(`getCode`/`getHttpStatus`/`getMessage`) — 추가 breaking, **별도 ADR 후보**.
- `totalPages` 필드화 여부 — 현행 계산 메서드(`totalPages()`) 유지로 일단 닫음.

## 관련

- ADR-0003(드리프트 표준 수렴) — ①·②가 이 원칙의 직접 적용 · ADR-0004(버저닝/pre-1.0 breaking 허용) · ADR-0006(도메인 커널, pre-1.0 변경 창)
- 코드: `PageMeta.java` · `DeletionStatus.java` · `DomainException.java` · `ErrorCode.java`
