# platform-persistence-jpa SDK Guide (v0.1)

> **Wiki SSOT:** ryu-qqq/ryu-qqq-wiki @ `wiki/projects/spring-platform-commons/platform-persistence-jpa.md`

레포 내 빠른 참조. 상세·다이어그램은 wiki 페이지를 따른다.

## Gradle

```groovy
// adapter-out persistence module
implementation project(':platform-persistence-jpa')
// JitPack (외부 서비스, 태그 후)
implementation 'com.github.ryu-qqq.spring-platform-commons:platform-persistence-jpa:v0.1.0'
```

## Entity bases

| 클래스 | 용도 |
|--------|------|
| `BaseAuditEntity` | `createdAt` / `updatedAt` (JPA Auditing) |
| `BaseSoftDeleteEntity` | `deleted` / `deletedAt`, `markDeleted()` / `restoreFromSoftDelete()` |
| `BaseVersionedEntity` | `@Version` only |
| `BaseVersionedSoftDeleteEntity` | audit + soft delete + `@Version` (typical aggregate root) |

## Auto-configuration

`PlatformJpaAutoConfiguration` (starter import):

- `@EnableJpaAuditing`
- `JPAQueryFactory` bean (`EntityManagerFactory` 필요)

소비자 모듈에서 `@EntityScan`, `@EnableJpaRepositories`, DataSource는 직접 제공.

## QueryDSL

- SDK가 base entity용 Q-type 생성 (`PersistenceQueryMetaEntity` anchor)
- per-domain `ConditionBuilder`는 서비스 adapter-out에 둔다 (Phase 2 비목표)

## 템플릿 예시

`adapter-out/persistence-mysql/example-persistence` — HT6 패턴:

```yaml
platform:
  example:
    persistence:
      enabled: true
```

→ `ExampleRecordPort` + QueryDSL `ExampleRecordQueryDslRepository`.

## 낙관적 락 → HTTP 409

`@Version` 충돌 시 Spring이 `OptimisticLockingFailureException`을 던진다.  
`platform-web` `GlobalExceptionHandler`가 **409** + `OPTIMISTIC_LOCK_CONFLICT` ProblemDetail로 매핑.

## 테스트

```bash
./gradlew :platform-persistence-jpa:test
./gradlew :adapter-out:persistence-mysql:example-persistence:test
```
