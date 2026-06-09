# platform-persistence-jpa

JPA 기반 **adapter-out 영속성 SDK**. 헥사고날 아키텍처의 아웃바운드 어댑터가 공통으로 쓰는
JPA 베이스 엔티티(audit · soft delete · `@Version`)와 QueryDSL `JPAQueryFactory` 자동설정을 제공한다.

`DataSource` · 엔티티 스캔 · 리포지토리는 **소비측이 제공한다** (consumer provides). 이 모듈은
영속성 어댑터가 반복 작성하던 공통 매핑·와이어링만 담당하며, 접속 정보나 스키마 전략을 강제하지 않는다.

```groovy
implementation project(':platform-persistence-jpa')
// 소비측이 DataSource · @EntityScan · @EnableJpaRepositories 를 직접 제공
```

## 역할

| 책임 | 비책임 |
|------|--------|
| audit · soft delete · `@Version` `@MappedSuperclass` 베이스 제공 | `DataSource` 생성·접속 설정 |
| `JPAQueryFactory` 빈 자동 등록 (QueryDSL 동적쿼리) | 엔티티 스캔(`@EntityScan`)·리포지토리 활성화 |
| `@EnableJpaAuditing` 로 생성·수정 시각 자동 채움 | DDL 전략·스키마 마이그레이션 강제 |
| 소비측 커스텀 `JPAQueryFactory` 빈에 양보 (`@ConditionalOnMissingBean`) | 도메인별 리포지토리 인터페이스 정의 |

## 확장점

### 1. 베이스 엔티티 (`@MappedSuperclass`)

도메인 JPA 엔티티가 필요에 따라 골라 상속한다. 모두 audit 컬럼(`created_at` · `updated_at`)을 기본으로 갖는다.

| 베이스 | 추가 필드 | 용도 |
|--------|-----------|------|
| `BaseAuditEntity` | `createdAt` · `updatedAt` | 생성·수정 시각만 필요한 단순 엔티티 |
| `BaseSoftDeleteEntity` | `deletedAt` | 소프트 삭제 — 삭제 여부는 `deletedAt != null`로 파생 (ADR-0003) |
| `BaseVersionedEntity` | `version` (`@Version`) | 낙관적 락이 필요한 엔티티 |
| `BaseVersionedSoftDeleteEntity` | `deletedAt` + `version` | audit + soft delete + 낙관적 락 — 전형적 애그리거트 루트 베이스 |

- soft delete는 **별도 `deleted` boolean 컬럼을 두지 않는다**. `deletedAt`의 null 여부로 파생하며,
  `isDeleted()` · `markDeleted(Instant)` · `restoreFromSoftDelete()` 로 상태를 다룬다.
- `@Version`은 도메인 `Versioned`와 정렬되어, 매퍼가 `getVersion()`을 도메인 `version()`으로 옮긴다.

```java
@Entity
@Table(name = "orders")
public class OrderJpaEntity extends BaseVersionedSoftDeleteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... 도메인 컬럼
}
```

### 2. QueryDSL `JPAQueryFactory`

동적 쿼리용 `JPAQueryFactory` 단일 빈을 주입받아 타입 안전 쿼리를 작성한다.
`SharedEntityManagerCreator`로 만든 공유 `EntityManager` 위에서 동작하므로 트랜잭션 경계를 따른다.

```java
@Repository
class OrderQueryRepository {
    private final JPAQueryFactory queryFactory;

    List<OrderJpaEntity> findActive(long customerId) {
        QOrderJpaEntity o = QOrderJpaEntity.orderJpaEntity;
        return queryFactory.selectFrom(o)
                .where(o.customerId.eq(customerId).and(o.deletedAt.isNull()))
                .fetch();
    }
}
```

QueryDSL Q-타입은 `annotationProcessor`(`querydsl-apt`, jakarta classifier)가 컴파일 시 생성한다.

## 자동설정

`PlatformJpaAutoConfiguration` (`@AutoConfiguration`, `AutoConfiguration.imports`로 등록,
`HibernateJpaAutoConfiguration` 이후 적용).

| 조건 | 동작 |
|------|------|
| 클래스패스에 `EntityManagerFactory`(JPA) 없음 | `@ConditionalOnClass`로 자동설정 **비활성** |
| 클래스패스에 `JPAQueryFactory`(QueryDSL) 없음 | `@ConditionalOnClass`로 자동설정 **비활성** |
| `EntityManagerFactory` 빈 없음 | `@ConditionalOnBean`으로 `JPAQueryFactory` **미등록** (backs-off) |
| `EntityManagerFactory` 빈 있음 | `JPAQueryFactory` 빈 **자동 등록** + `@EnableJpaAuditing` 활성 |
| 소비측이 `JPAQueryFactory` 빈 정의 | `@ConditionalOnMissingBean`으로 **양보** |

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnBean(EntityManagerFactory.class)
public JPAQueryFactory jpaQueryFactory(EntityManagerFactory entityManagerFactory) {
    return new JPAQueryFactory(
            SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
}
```

## 테스트

자동설정 등록 / backs-off / 양보는 `ApplicationContextRunner`로 검증한다
(`PlatformJpaAutoConfigurationTest` — `platform-security` · `platform-outbox`와 동일 스타일).
베이스 엔티티 매핑·auditing·soft delete·낙관적 락은 H2 위 통합 테스트로 검증한다
(`BaseSoftDeleteEntityIntegrationTest` · `BaseVersionedEntityIntegrationTest`).

```bash
./gradlew :platform-persistence-jpa:test
./gradlew :platform-persistence-jpa:build
```

## 의존

- `api libs.spring.boot.starter.data.jpa` — JPA · Hibernate · auditing (전이 노출)
- `api libs.querydsl.jpa` (jakarta classifier) — QueryDSL `JPAQueryFactory` API (전이 노출)
- `implementation libs.spring.boot.autoconfigure` — `@AutoConfiguration` 조건부 와이어링
- `annotationProcessor libs.querydsl.apt` (jakarta) — Q-타입 컴파일 시 생성
