# platform-common-domain

**헥사고날 서비스가 공유하는 순수 도메인 타입 모음 — 프레임워크 비의존.**

쿼리/페이징 VO, soft delete 상태, 멱등·캐시·락 키 마커, 예외 베이스를
한곳에 모은다. Spring·웹·persistence 어떤 인프라에도 의존하지 않으므로 도메인 레이어가 그대로
import 할 수 있다. **순수 도메인 커널** — 횡단 인프라 어휘(MDC 키·헤더 등)는 두지 않는다(ADR-0006,
`platform-observability` 소유).

## 역할

각 bounded context가 반복해서 다시 만드는 **얇은 공통 계약**을 수렴시킨다. 이 모듈은 동작(behavior)을
거의 갖지 않고, 도메인이 구현하거나 소비할 **타입·마커 인터페이스·값 객체**를 제공한다. 구체 정책(키
포맷, 컬럼 매핑, 정규화, 영속화)은 모두 소비측 책임으로 밀어낸다.

- **프레임워크-프리** — `build.gradle` 의 runtime 의존이 없다(테스트 전용 의존만). HTTP status는
  Spring `HttpStatus` 가 아니라 `int` 로 둔다.
- **마커 + record 조합** — 마커 인터페이스(`SortKey`·`SearchField`·`CacheKey`·`LockKey`·`DateField`·
  `ErrorCode`)는 도메인별 `enum`/`record` 가 구현하고, 페이징·범위 VO는 `record` 로 직접 쓴다.
- **확장점은 인터페이스로** — 키/정렬/에러코드는 모듈이 값을 강제하지 않고 계약만 정의한다.

## 패키지 구성

| 패키지 | 내용 |
|--------|------|
| `com.ryuqqq.platform.common.vo` | 쿼리·페이징·범위·soft delete·키 VO와 마커 |
| `com.ryuqqq.platform.common.exception` | `ErrorCode` 계약 + `DomainException` 베이스 |
| `com.ryuqqq.platform.common.domain` | `Versioned` — 낙관적 락 version 계약 |

## 확장점 (소비측이 구현)

### 키 마커 — `CacheKey` · `LockKey`

도메인이 `record` 로 구현해 키 포맷을 강제하는 마커. 각각 단일 `value()` 만 노출하며 순수 인터페이스다
(캐시/락 인프라 비의존). `CachePort`·`DistributedLockPort` 같은 소비측 포트가 이 마커를 받아 처리한다.

```java
public record ProductCacheKey(long productId) implements CacheKey {
    public String value() { return "cache:product:" + productId; }
}
public record OrderLockKey(long orderId) implements LockKey {
    public String value() { return "lock:order:" + orderId; }
}
```

키 형식 권장: `cache:{domain}:{entity}:{id}`, `lock:{domain}:{entity}:{id}`.

### 멱등 키 — `IdempotencyKeyValue`

외부(클라이언트 헤더 등)에서 받은 불투명 키를 타입으로 감싸 **blank/null 만** 막는 `record`. 정규화
(trim·대소문자·charset)는 하지 않고 동등성은 원문 기준이다. 컨텍스트 간 충돌 방지를 위한 `PREFIX:value`
네임스페이싱과 키 파생/생성 팩토리는 **소비측 책임**(비목표).

```java
var key = new IdempotencyKeyValue(request.getHeader("Idempotency-Key"));
String stored = "payment:" + key; // namespacing은 소비측 정책
```

### 정렬·검색·날짜 필드 마커 — `SortKey` · `SearchField` · `DateField`

도메인별 `enum` 이 구현하는 `fieldName()` 마커. **DB 컬럼명 매핑은 adapter-out 레이어 책임**이다.

### 페이징 VO

| 타입 | 용도 |
|------|------|
| `PageRequest(page, size)` | offset 기반 요청. `offset()` 계산 제공 |
| `CursorPageRequest<C>(cursor, size)` | 커서 기반 요청. `cursor == null` 이면 첫 페이지 |
| `PageMeta(page, size, totalCount)` | offset 응답 메타. `totalPages`·`hasNext`·`hasPrevious` |
| `SliceMeta<C>(size, hasNext, nextCursor)` | 커서 응답 메타 |
| `Page<T>(content, meta)` | offset 결과 — 콘텐츠 + `PageMeta` 묶음. `map()` 제공 |
| `Slice<T, C>(content, meta)` | 커서 결과 — 콘텐츠 + `SliceMeta` 묶음. `map()` 제공 |
| `SortOrder<T extends SortKey>(key, direction)` | 단일 정렬 항목 |
| `Sort<T extends SortKey>(orders)` | 정렬 명세 — **복합 정렬**(`ORDER BY a DESC, b ASC`). `by(k,d)`·`of(...)` |
| `QueryContext<T extends SortKey>` | `Sort` + offset 페이징 + `includeDeleted` 묶음 |
| `CursorQueryContext<T extends SortKey, C>` | `Sort` + 커서 페이징 묶음 |
| `DateRange(fromInclusive, toExclusive, dateField)` | `[from, to)` 날짜 범위 필터 |

각 페이징 VO는 `of(...)` / `firstPage(...)` / `defaultOf(...)` 팩토리를 제공한다. `QueryContext`·
`CursorQueryContext`는 `Sort`를 직접 받거나 단일 정렬 편의 팩토리(`of(sortKey, direction, pageRequest)`)를
함께 제공한다.

### Soft delete — `DeletionStatus`

`(deleted, deletedAt)` `record`. Aggregate의 `delete(now)`/`restore()` 와 persistence 필터가 공유한다.
`active()`·`deleted(now)`·`markDeleted(now)`·`restore()`·`isActive()` 제공.

### 예외 — `ErrorCode` · `DomainException`

- `ErrorCode` — `getCode()`·`getHttpStatus()`(int, Spring 비의존)·`getMessage()` 계약. bounded
  context별 `enum` 이 구현하며 코드 형식은 `{CONTEXT}-{NUMBER}`(예: `PRD-001`).
- `DomainException` — 비즈니스 규칙 위반 예외 베이스. `errorCode`·`args(Map)` 를 담고 `code()`·
  `httpStatus()` 위임 접근자를 제공한다. 도메인별 예외는 `extends DomainException`.

```java
public enum ProductErrorCode implements ErrorCode {
    NOT_FOUND("PRD-001", 404, "product not found");
    // ... getCode/getHttpStatus/getMessage
}
throw new DomainException(ProductErrorCode.NOT_FOUND);
```

### 낙관적 락 — `Versioned`

`long version()` **읽기 전용** 계약. Aggregate가 자기 낙관적 락 version을 노출하고, version 반영은
영속성 매퍼가 재구성 시 주입한다(ADR-0006). `platform-persistence-jpa`의 `BaseVersionedEntity`가
`@Version`을 매핑한다.

## 의존성

런타임 의존 없음. JUnit/AssertJ 등 테스트 의존만 갖는다.

```groovy
testImplementation platform(libs.junit.bom)
testImplementation libs.bundles.testing
```

## 비목표

- 키 파생/생성 팩토리, charset/length 제약, trim 정규화, namespace 코어 필드 — 소비측 책임.
- DB 컬럼명 매핑, 영속화, 캐시/락/추적 인프라 — adapter 레이어 책임.
- 분산추적 span 생성 — Micrometer Tracing/OTel 계측 소유.
