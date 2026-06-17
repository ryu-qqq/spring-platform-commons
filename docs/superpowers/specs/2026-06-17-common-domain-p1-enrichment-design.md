# platform-common-domain P1 편의 메서드 흡수 — 설계

> **스냅샷:** 2026-06-17

> crawling 실전 피드백 중 두 게이트(abstraction-critic seam + decision-researcher commonality)를
> 통과한 P1 편의 메서드만 흡수한다. 작성: 2026-06-17 · 상태: 승인됨(설계) → 구현 계획 단계

## 배경·동기

`connectly-services/crawling-domain`이 `platform-common-domain` 채택을 진행하며 흡수 후보 편의 메서드를
식별했다(근거: `docs/common-domain-enrichment-from-crawling.md`). 단일 소비처 피드백이라 두 단계로 걸렀다:

1. **abstraction-critic (seam)**: 표현계층 책임(`startElement`/`endElement`/`displayName`)·요청/응답 책임
   혼선(`totalPages(long)` on PageRequest)을 NO-GO로 컷.
2. **decision-researcher (commonality)**: fleet 8개 서비스 실측 — *복붙 선언*이 아닌 **실프로덕션 호출**
   기준으로, 호출 0인 죽은 메서드(`next`/`previous`/`PageMeta.isFirst·isLast·isEmpty·offset`)와 값
   불일치 정책값(`MAX_SIZE`)을 추가 제거.

두 게이트를 모두 통과한 **8개 메서드**만 본 설계 대상이다.

## 범위 — 흡수 대상 (전부 비파괴 추가)

| 타입 | 메서드 | 동작 |
|------|--------|------|
| `PageRequest` (record `page,size`) | `defaultPage()` | `of(0, DEFAULT_SIZE)` (DEFAULT_SIZE=20) |
| | `isFirst()` | `page == 0` |
| `PageMeta` (record `page,size,totalCount`) | `empty(int size)` | `of(0, size, 0)` |
| | `empty()` | `empty(DEFAULT_SIZE)` (DEFAULT_SIZE=20) |
| `SortDirection` (enum ASC/DESC) | `isAscending()` | `this == ASC` |
| | `reverse()` | ASC↔DESC 반전 |
| | `defaultDirection()` (static) | `DESC` |
| | `fromString(String)` | null/blank → `defaultDirection()`; 아니면 `valueOf(s.trim().toUpperCase(Locale.ROOT))` 시도, `IllegalArgumentException` 시 `defaultDirection()` |

전부 추가만(record 컴포넌트·enum 상수 불변) → 바이너리/소스 호환성 유지. **v0.2.x 마이너**. 현행 코어엔
8개 모두 부재(중복 없음).

## 핵심 설계 결정

- **`SortDirection.fromString`은 `Locale.ROOT` 강제**. fleet 일부 구현이 `toUpperCase()`(로케일 미지정 —
  터키어 dotless-i 등 잠재 버그)를 쓰는데, 코어가 `Locale.ROOT`로 통일해 그 버그를 흡수·개선한다.
  파싱은 **엄격** — `"asc"`/`"DESC"` 등 정확한 enum명(대소문자·공백 무시)만 허용, 관용표기(`"ascending"`,
  `"1"`, `"오름차순"` 등)는 불허하고 기본값으로 폴백한다(관용표기 허용은 어댑터-in 책임).
- **`isDescending()` 제외**. 실프로덕션 호출이 MarketPlace 1곳(중복 레포)뿐. 필요 시 `!isAscending()`으로
  대체. API 비대칭은 의도된 선택(YAGNI > 대칭성).
- **`PageMeta.empty()` 기본 size = 20**. `PageRequest.DEFAULT_SIZE`와 같은 값을 `PageMeta` 자체
  `private static final int DEFAULT_SIZE = 20`으로 둔다(두 record 독립, 공유 상수 클래스는 YAGNI).
- **`defaultDirection()` = DESC**. crawling 및 fleet 공통 관행("최신순 기본").

## 비목표 (명시적 제외 — 데이터 근거)

- **죽은 메서드**: `PageRequest.next()`/`previous()`, `PageMeta.isFirst()`/`isLast()`/`isEmpty()`/`offset()`
  — fleet 7곳 복붙 선언이나 프로덕션 호출 0. 흡수하지 않는다.
- **`MAX_SIZE`**: 값이 서비스마다 100/2000/10000으로 갈리고 한 서비스가 그 값에 프로덕션 의존 → 정책값,
  코어 단일 상수 부적합. 제외.
- **`DomainException` null-args (후보 D)**: 추상화가 아닌 방어 버그 → **별도 코드리뷰 트랙**(본 설계 범위
  밖). 단 근거 문서 `common-domain-enrichment-from-crawling.md:63`의 NPE 오진단("`args()` 호출 시 NPE"
  → 실제는 현재 코드가 이미 `Map.copyOf(args)`라 null 전달 시 **생성자**에서 NPE)은 본 작업에서 **문서만**
  정정한다.
- **P2 (네이밍 breaking)**: `PageMeta.totalCount`↔`totalElements`, `DeletionStatus.deleted`↔`deletedAt`,
  `DomainException.errorCode()`↔`getErrorCode()` — 호환성 깨짐 → 별도 ADR.
- **P3 (커서/QueryContext 불변변형·Sort 다중정렬 정합)**: 후속.

## 영향 범위

- 변경: `platform-common-domain` 3개 VO 파일(`PageRequest`·`PageMeta`·`SortDirection`)에 메서드 추가 +
  각 테스트. 근거 문서 1곳 오진단 정정.
- 다른 모듈·기존 소비처 무영향(추가만). connectly 재승격 시 v0.2.x로 반영(후속, 본 범위 밖).

## 테스트 (common-domain은 순수 자바 — 단위 테스트만)

- `SortDirectionTest`: `fromString`(null→DESC, `""`→DESC, `"  "`→DESC, `"asc"`→ASC, `"DESC"`→DESC,
  `"ASCENDING"`→DESC(엄격), 유효하지않은값→DESC), `reverse`(ASC→DESC→ASC 왕복), `isAscending`(ASC=true,
  DESC=false), `defaultDirection`==DESC
- `PageRequestTest`: `defaultPage`==(0,20), `isFirst`(page0=true, page1=false)
- `PageMetaTest`: `empty()`==(0,20,0)·`empty(50)`==(0,50,0), 기존 `hasNext`/`hasPrevious`/`totalPages`와
  정합(empty는 hasNext=false)

---

*최종 갱신: 2026-06-17 — 초판(설계 승인). 두 게이트 통과 8개 메서드 흡수, 죽은 메서드·MAX_SIZE·P2·P3 제외.*
