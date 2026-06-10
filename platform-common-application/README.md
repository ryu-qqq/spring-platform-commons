# platform-common-application

**헥사고날 서비스가 공유하는 application 레이어 공통 헬퍼·아웃바운드 포트 계약 모음.**

쿼리 VO 조립 팩토리, 캐시·분산락 아웃바운드 포트, 배치 처리 결과 record를 한곳에 모은다.
application 레이어 규약을 지켜 web·persistence·캐시·락 등 **어떤 인프라 어댑터에도 의존하지 않는다.**
구체 구현(Redisson 캐시/락, HTTP 매핑 등)은 모두 소비측 adapter 레이어로 밀어낸다.

## 역할

각 bounded context의 application 레이어가 반복해서 다시 만드는 **얇은 공통 계약**을 수렴시킨다.
이 모듈은 도메인 타입(`platform-common-domain`)만 알고, 포트는 인터페이스로 정의해 구현을 소비측에
위임한다. 빈은 컴포넌트 스캔이 아니라 auto-configuration으로 zero-config 등록한다.

- **레이어 비의존** — `build.gradle` 의 runtime 의존이 `platform-common-domain` + Spring context/
  autoconfigure 뿐이다. web(서블릿/REST)·persistence(JPA/JDBC)·캐시/락 어댑터에 의존하지 않는다.
- **아웃바운드 포트는 인터페이스만** — `CachePort`·`DistributedLockPort` 는 계약만 정의하고
  구현은 `platform-redis`(Redisson) 등 adapter-out 모듈이 담당한다.
- **무상태 헬퍼 + auto-config** — `CommonVoFactory` 는 stateless 라 단일 빈으로 어디서나 주입된다.

## 패키지 구성

| 패키지 | 내용 |
|--------|------|
| `com.ryuqqq.platform.common.factory` | `CommonVoFactory` — 공통 query VO 조립 (APP-IN-001) |
| `com.ryuqqq.platform.common.port` | `CachePort`·`DistributedLockPort` — 아웃바운드 포트 계약 |
| `com.ryuqqq.platform.common.scheduler` | `SchedulerBatchProcessingResult` — 배치 처리 결과 record |
| `com.ryuqqq.platform.common.config` | `PlatformCommonApplicationAutoConfiguration` — zero-config 빈 등록 |

## 확장점

### 아웃바운드 포트 — `CachePort` · `DistributedLockPort`

application 레이어가 주입받아 호출하고, 구현은 adapter-out 모듈이 제공하는 헥사고날 포트.
키는 `platform-common-domain` 의 타입 안전한 마커(`CacheKey`·`LockKey`)로 받아 인프라 비의존을 유지한다.

```java
// 캐시: 메서드 단위 제네릭으로 단일 빈을 어디서나 주입
Optional<Product> cached = cachePort.get(new ProductCacheKey(id), Product.class);
cachePort.set(new ProductCacheKey(id), product, Duration.ofMinutes(10));

// 분산락: try ... finally 로 반드시 unlock
if (lockPort.tryLock(new OrderLockKey(id), 3, 10, TimeUnit.SECONDS)) {
    try { /* critical section */ }
    finally { lockPort.unlock(new OrderLockKey(id)); }
}
```

`CachePort` 는 set/get/evict/evictByPattern/exists/getTtl 을, `DistributedLockPort` 는
tryLock/unlock/isHeldByCurrentThread/isLocked 를 노출한다. 구현체(Redisson 등)는 소비측 adapter 책임.

### 배치 처리 결과 — `SchedulerBatchProcessingResult`

application 레이어가 배치 작업 결과로 반환하는 순수 `record`(total·success·failed).
`platform-scheduler` 의 aspect가 이 결과를 받아 요약 로깅·메트릭에 사용한다 — 이 모듈은 로깅/메트릭
인프라에 의존하지 않는다. `of`·`empty`·`merge`·`hasFailures` 헬퍼를 제공한다.

```java
return SchedulerBatchProcessingResult.of(total, success, failed);
```

### query VO 조립 — `CommonVoFactory`

`SearchCriteria` 를 만드는 도메인별 Query Factory가 위임하는 무상태 헬퍼.
offset(`QueryContext`)·커서(`CursorQueryContext`) 두 페이징 방식을 조립한다.

**입력 전제 (APP-IN-001):** adapter-in mapper가 paging·sort·date 를 이미 검증·기본값 적용한
도메인 VO/enum 만 넘긴다. null 처리·HTTP 문자열 parse·default page/size 는 Factory 책임이 아니다.

```java
QueryContext<ProductSortKey> ctx =
        commonVoFactory.createQueryContext(sortKey, sortDirection, pageRequest);
```

## 자동 설정

`PlatformCommonApplicationAutoConfiguration`(`@AutoConfiguration`)이 `CommonVoFactory` 를 컴포넌트
스캔 의존 없이 등록한다. 소비측이 동일 타입 빈을 정의하면 `@ConditionalOnMissingBean` 으로 양보한다.
등록은 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 를 통한다.

## 의존성

| 범위 | 의존 |
|------|------|
| api | `platform-common-domain` |
| implementation | Spring `context`·`boot-autoconfigure` (빈 등록·조건부 설정) |
| test | JUnit BOM, testing bundle, `spring-boot-starter-test`(`ApplicationContextRunner`) |

## 비목표

- 캐시·분산락 구현(Redisson 등), 직렬화·TTL 정책 — adapter-out 책임.
- HTTP 요청 파싱·검증·기본값 적용, 응답 매핑 — adapter-in(web) 책임.
- 배치 요약 로깅·메트릭 발행 — `platform-scheduler` aspect 책임.
- DB 컬럼 매핑·영속화 — persistence adapter 책임.
