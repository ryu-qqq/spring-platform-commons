# platform-common-domain 보강 제안 — connectly-services/crawling 실전 피드백

> 작성 2026-06-17. 출처: `connectly-services` 의 `crawling-domain` 이 `com.connectly.platform:platform-common-domain:0.1.0` 채택을 진행하며 발견한 gap.
> 목적: crawling 이 **자체 공통 VO 를 버리고 platform 으로 단일화**하려면, platform 이 아래 실전 메서드들을 흡수해야 한다. 반대로 platform 이 더 나은 부분(다중 정렬·컨테이너)은 crawling 이 채택할 부분으로 표시.

---

## 0. 요약

crawling 은 platform 과 **이름이 같은 공통 타입 14종**(VO 12 + `ErrorCode`/`DomainException`)을 자체 보유해왔다. 채택을 위해 비교한 결과:

- **`ErrorCode`**: 시그니처 100% 동일 → crawling 이 **이미 platform 으로 교체 완료**(자체 삭제). ✅
- **VO 12종**: 이름은 겹치나 **crawling 쪽이 실전 편의 메서드를 더 많이** 갖고 있어, 지금 platform 으로 내리면 호출부가 기능을 잃는다. → platform 보강 필요.
- **단, 설계 축이 다른 곳**: platform 이 `Sort/SortOrder`(다중 정렬)·`Page/Slice`(content+meta 컨테이너)·제네릭 커서로 더 일반적 → 이쪽은 crawling 이 흡수할 부분.

수렴점: **platform 이 아래 편의 메서드를 흡수**하고, crawling 은 platform 의 `Sort`·`Page/Slice` 모델을 채택하면 양쪽이 단일 커널로 합쳐진다.

---

## 1. 페이징 — `PageRequest`

| 항목 | platform 0.1.0 | crawling 보유 | 제안 |
|---|---|---|---|
| 팩토리 | `of(p,s)`·`firstPage()`·`firstPage(int)` | `of`·`first(int)`·`defaultPage()` | `firstPage`/`first` 네이밍 통일, `defaultPage()` 추가 |
| 네비게이션 | `offset()` | `next()`·`previous()`·`isFirst()` | **추가** — 페이지 이동 편의 |
| 계산 | — | `totalPages(long total)`·`isLast(long total)` | **추가** — total 기준 마지막 판정 |
| 상수 | `DEFAULT_SIZE` | `DEFAULT_SIZE`·`MAX_SIZE` | `MAX_SIZE` 추가(상한 가드) |

## 2. 페이징 메타 — `PageMeta`

| 항목 | platform | crawling | 제안 |
|---|---|---|---|
| 필드 | `page,size,totalCount` | `page,size,totalElements,totalPages` | ⚠️ **네이밍 충돌**: `totalCount` vs `totalElements`. 하나로 확정 필요(채택 시 호환성 영향) |
| 팩토리 | `of(p,s,total)` | `of(3)`·`of(4)`·`empty()`·`empty(int)` | `empty()` 류 추가 |
| 헬퍼 | `totalPages()`·`hasNext()`·`hasPrevious()` | + `isFirst()`·`isLast()`·`isEmpty()`·`startElement()`·`endElement()`·`offset()` | **추가** — 행 번호 계산 등 실전 헬퍼 |

## 3. 정렬 — `SortDirection` / `SortKey` / `Sort`

- **`SortDirection`**: platform 은 enum ASC/DESC 뿐. crawling 은 `reverse()`·`isAscending()`·`isDescending()`·`displayName()`·`fromString(String)`·`defaultDirection()` 보유 → **추가 제안**.
- **`SortKey`/`DateField`/`SearchField`**: platform `{ fieldName() }`, crawling 은 `+ default name()`(enum 구현체와 통합 조회). 호출부가 `key.name()` 으로 문자열 매칭에 씀 → `default name()` 추가 검토(또는 가이드: enum 자체 `name()` 사용).
- **(역방향) `Sort<T>`/`SortOrder<T>`**: platform 은 **다중 정렬**을 일급 모델로 가짐. crawling 은 `sortKey + sortDirection` **단일 정렬**만. → 이건 **crawling 이 platform 을 채택할 부분**. 단 `QueryContext.of(key, direction, ...)` 단일정렬 편의 오버로드는 platform 에도 이미 있어 마이그레이션 부담은 작다.

## 4. 삭제 상태 — `DeletionStatus`

| 항목 | platform | crawling | 제안 |
|---|---|---|---|
| 팩토리 | `active()`·`deleted(Instant)` | `active()`·`deletedAt(Instant)`·`reconstitute(boolean,Instant)` | ⚠️ 네이밍 `deleted` vs `deletedAt`. `reconstitute(...)`(영속화 복원용) 추가 제안 |
| 헬퍼 | `isActive()`·`markDeleted`·`restore` | `isActive()`·`isDeleted()` | `isDeleted()` 추가(가독성) |

## 5. 커서 페이징 — `CursorPageRequest<C>` / `CursorQueryContext` / `SliceMeta`

- **`CursorPageRequest<C>`**: 양쪽 제네릭. crawling 은 `afterId(Long,int)`·`ofString(String,int)`·`next(C)`·`hasCursor()`·`fetchSize()`(size+1 조회 패턴) 보유 → **추가 제안**.
- **`QueryContext`/`CursorQueryContext`**: crawling 은 `withSortKey`·`withPageSize`·`withIncludeDeleted`·`nextPage`·`previousPage`·`reverseSortDirection` **불변 변형 메서드** 보유 → platform 에 **추가 제안**(빌더 없이 단계적 변형).
- **`SliceMeta`**: platform `SliceMeta<C>`(제네릭 커서, `size/hasNext/nextCursor`) vs crawling 비제네릭(`size/hasNext/cursor/count` + `withCursor` String/Long 오버로드·`cursorAsLong()`·`next()`). → platform 의 제네릭이 더 깔끔(**crawling 채택**), 단 `count`(현재 페이지 건수)·`cursorAsLong()` 편의는 platform 이 **흡수 검토**.

## 6. 예외 — `DomainException`

| 항목 | platform | crawling | 제안 |
|---|---|---|---|
| 생성자 가시성 | `public` | `protected` | crawling 은 **직접 인스턴스화 금지·상속 강제** 의도. platform 도 `protected` 검토(또는 가이드로 명시) |
| 생성자 | `(EC)`·`(EC,msg)`·`(EC,msg,args)`·`(EC,msg,args,cause)` | + `(EC, Throwable cause)` | `(ErrorCode, Throwable)` 오버로드 추가(메시지=ErrorCode 기본) |
| 접근자 | `errorCode()` | `getErrorCode()` | 네이밍 차이 — 통일 결정 필요 |
| **null args 처리** | `(EC,msg,Map)` 에 null Map 전달 시 생성자의 `Map.copyOf(null)` 에서 **NPE**(※ null→빈 Map 정규화는 별도 코드리뷰 트랙) | `args != null ? Map.copyOf(args) : emptyMap()` 정규화 | **null args → 빈 Map 정규화 추가**(방어적). crawling 채택 중 실측 NPE 발생 — 우선순위 높음 |

> crawling 의 30개 하위 예외는 자체 `DomainException` 을 상속 중이다. platform `DomainException` 으로의 전환은 영향이 커서, ErrorCode 통일(완료) 이후 **점진 단계**로 둔다(connectly-services ADR-0002 후속, 런북 B-4).

---

## 7. 우선순위 제안

1. **P1 (저비용·고효용)**: `PageRequest`·`PageMeta`·`SortDirection` 편의 메서드 흡수 — crawling 외 다른 소비처도 바로 득.
2. **P2 (네이밍 확정)**: `PageMeta.totalCount/totalElements`, `DeletionStatus.deleted/deletedAt`, `DomainException.errorCode()/getErrorCode()` — 한쪽으로 확정(호환성 깨짐 주의, 마이너 버전에서).
3. **P3 (설계 정합)**: 커서/QueryContext 의 불변 변형 메서드 흡수 + crawling 의 `Sort`(다중정렬)·`Page/Slice` 컨테이너 채택.

각 항목은 platform 의 다른 소비처에도 영향을 주므로, 채택 전 abstraction-critic/ADR 로 "진짜 공용인지" 한 번 거르길 권장.
