# 0003. 드리프트 표준 수렴 — 시간 타입 · ApiResponse · soft-delete

- 상태: Accepted
- 날짜: 2026-06-07

## 맥락 (Context)

ADR-0002가 채택한 "0단계 무후회 경로"의 후속 결정이다. 5개 서비스 코드스캔(근거: `ryu-qqq-wiki/wiki/projects/spring-platform-commons/adoption-gap.md`)에서 platform-commons와 서버들이 같은 횡단 자산을 조금씩 다르게 들고 있는 드리프트가 식별됐다. 입양을 가능하게 하고 재드리프트를 막으려면 표준을 못박아야 한다.

**이 ADR은 표준(target)을 결정한다 — 실제 코드 마이그레이션 실행은 별도(0단계 실행)다.**

스캔 현황:

- **시간 타입**: `Instant` 4/5(platform · MarketPlace · AuthHub · FileFlow), CrawlingHub만 `LocalDateTime`(동결 상태).
- **ApiResponse**: `success` 필드 없음이 다수(platform · MarketPlace · FileFlow · Gateway), AuthHub만 `success` 보유.
- **soft-delete**: 서버 다수(AuthHub · FileFlow · CrawlingHub)는 `deletedAt`만, platform-commons만 `deleted(boolean) + deletedAt`.

## 결정 동인 (Decision Drivers)

- 입양 가능성 — 즉시 입양 후보(platform-common-domain 등)의 정합성 확보.
- 재드리프트 차단 — 표준을 단일 target으로 못박아 추후 흔들림 방지.
- "더 나은 쪽으로" 수렴 — commons가 무조건 옳다는 전제가 아니라, outlier가 어느 쪽이든 더 깔끔한 모델로 모은다.

## 검토한 옵션 (Considered Options)

각 자산별로 "outlier를 다수 표준에 맞춘다" vs "현상 유지(드리프트 방치)"를 검토했고, 후자는 입양·재드리프트 차단 모두를 막으므로 기각.

- 시간 타입: `Instant`(UTC) 수렴 vs `LocalDateTime` 혼재 유지.
- ApiResponse: `success` 필드 제거(다수 표준) vs `success` 추가(AuthHub 모델 채택).
- soft-delete: `deletedAt`만(서버 다수 모델) vs `deleted(boolean) + deletedAt`(commons 현행 유지).

## 결정 (Decision Outcome — 3개 표준 확정)

1. **시간 타입 = `java.time.Instant`(UTC)**. CrawlingHub의 `LocalDateTime`은 동결 예외 — 부활 시 수렴.
2. **ApiResponse에 `success` 필드 없음** — `(data, timestamp, requestId)`. 에러는 RFC 7807 ProblemDetail이 신호하므로 `success`는 중복. AuthHub의 `success` 필드가 제거 대상.
3. **soft-delete = `deletedAt`(nullable `Instant`)만**, `deleted` boolean 없음(`isDeleted() = deletedAt != null`). **platform-commons의 `BaseSoftDeleteEntity`가 outlier** — `deleted` boolean을 제거하는 방향(서버 다수가 더 깔끔).

핵심 원칙: 표준 수렴은 "commons가 무조건 옳음"이 아니라 **더 나은 쪽으로** 모은다. (시간 · ApiResponse는 outlier 서버를 commons 표준으로, soft-delete는 commons가 outlier라 서버 모델로 commons를 고침.)

## 결과 (Consequences)

긍정:

- 표준 명확화 → 입양 가능성 · 재드리프트 차단 기반.
- platform-common-domain 등 즉시 입양 후보의 정합성 확보.

비용/후속(= 마이그레이션, 별도 실행):

- **AuthHub**: ApiResponse `success` 필드 제거(응답 계약 변경 — 소비측 영향 점검 필요).
- **platform-commons**: `BaseSoftDeleteEntity`에서 `deleted` 컬럼 제거(스키마 마이그레이션 동반).
- **CrawlingHub**: 동결이라 예외, 부활 시 `Instant` 수렴.
- 이 표준을 **ArchUnit 규칙으로 강제**(재드리프트 차단)하는 것은 Enforce 단계의 후속.

비목표: 지금 마이그레이션을 실행하지 않는다(표준 *결정*만).

## 관련

- ADR-0002(repo 토폴로지 · 0단계 경로)
- `adoption-gap.md`(드리프트 근거)
