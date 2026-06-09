# platform-redis

Redisson 기반 **adapter-out Redis SDK**. `platform-common-application`이 정의한 아웃바운드 포트
(`CachePort` · `DistributedLockPort`)의 Redis 구현과 Spring Boot 자동설정을 제공한다.

연결 설정(`RedissonClient`)은 **소비측이 제공한다** (consumer provides — `DataSource`와 동일 원칙).
이 모듈은 어댑터와 와이어링만 담당하며, Redis 접속 정보·codec·클러스터 토폴로지를 강제하지 않는다.

```groovy
implementation project(':platform-redis')
// 소비측이 RedissonClient 빈을 직접 제공 (예: redisson-spring-boot-starter)
```

## 역할

| 책임 | 비책임 |
|------|--------|
| `CachePort` / `DistributedLockPort` 의 Redisson 구현 | `RedissonClient` 생성·접속 설정 |
| `RedissonClient` 빈이 있을 때 어댑터 자동 등록 | 캐시 키 포맷 강제 (도메인이 marker로 결정) |
| 소비측 커스텀 포트 빈에 양보 (`@ConditionalOnMissingBean`) | 리액티브/논블로킹 락 (현재 블로킹/servlet) |

## 확장점

### 1. 아웃바운드 포트 (구현 대상은 이 모듈, 소비는 application)

포트·VO marker의 SSOT는 `platform-common-application`(`com.ryuqqq.platform.common.port` /
`...common.vo`)에 있다. 이 모듈은 그 구현을 제공한다.

| 포트 | Redisson 구현 | 비고 |
|------|---------------|------|
| `CachePort` | `RedissonCacheAdapter` (`RBucket` 기반) | 직렬화는 `RedissonClient`의 codec을 따름 |
| `DistributedLockPort` | `RedissonDistributedLockAdapter` (`RLock` 기반) | 블로킹/servlet, `unlock`은 현재 스레드 보유 시에만 |

`CachePort`는 메서드 단위 제네릭(`<T>`)이라 **단일 빈을 어디서나 주입**받을 수 있다.

### 2. 키 marker (도메인이 구현 — 키 포맷 강제)

키는 타입 안전한 marker 인터페이스로 받는다. 각 bounded context가 `record`로 구현해 포맷을 강제한다.

```java
// 캐시 키 — 권장 형식 cache:{domain}:{entity}:{id}
public record ProductCacheKey(long productId) implements CacheKey {
    public String value() { return "cache:product:" + productId; }
}

// 분산락 키 — 권장 형식 lock:{domain}:{entity}:{id}
public record OrderLockKey(long orderId) implements LockKey {
    public String value() { return "lock:order:" + orderId; }
}
```

`CacheKey` / `LockKey`는 프레임워크 비의존 순수 인터페이스다(`platform-common-application` 소유).

## 자동설정

`PlatformRedisAutoConfiguration` (`@AutoConfiguration`, `AutoConfiguration.imports`로 등록).

| 조건 | 동작 |
|------|------|
| 클래스패스에 `RedissonClient` 없음 | `@ConditionalOnClass`로 자동설정 **비활성** |
| `RedissonClient` 빈 없음 | `@ConditionalOnBean`으로 어댑터 **미등록** (backs-off) |
| `RedissonClient` 빈 있음 | `DistributedLockPort` · `CachePort` 빈 **자동 등록** |
| 소비측이 동일 타입 포트 빈 정의 | `@ConditionalOnMissingBean`으로 **양보** |

```java
@Bean
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnMissingBean
public CachePort cachePort(RedissonClient redissonClient) { ... }
```

## 사용 예

```java
// 분산락 — tryLock 성공 시 try ... finally 로 반드시 unlock
if (lockPort.tryLock(new OrderLockKey(orderId), 3, 10, TimeUnit.SECONDS)) {
    try {
        // critical section
    } finally {
        lockPort.unlock(new OrderLockKey(orderId));
    }
}

// 캐시 — TTL 지정 저장 / 타입 안전 조회
cachePort.set(new ProductCacheKey(id), product, Duration.ofMinutes(10));
Optional<Product> cached = cachePort.get(new ProductCacheKey(id), Product.class);
```

## 테스트

자동설정 backs-off / 양보 / 등록은 `ApplicationContextRunner`로 검증한다
(`PlatformRedisAutoConfigurationTest` — `platform-security` · `platform-outbox`와 동일 스타일).
어댑터 동작은 mock `RedissonClient`로 검증한다.

```bash
./gradlew :platform-redis:test
./gradlew :platform-redis:build
```

## 의존

- `api project(':platform-common-application')` — 포트·VO marker (전이 노출)
- `implementation libs.redisson` — Redisson 클라이언트 API
- `implementation libs.spring.boot.autoconfigure` — `@AutoConfiguration` 조건부 와이어링
