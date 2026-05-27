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
- per-domain `ConditionBuilder`·Query adapter는 **서비스 adapter-out**에 둔다 (wiki `persistence-mysql` 참고)

## 낙관적 락 → HTTP 409

`@Version` 충돌 시 Spring이 `OptimisticLockingFailureException`을 던진다.  
`platform-web` `GlobalExceptionHandler`가 **409** + `OPTIMISTIC_LOCK_CONFLICT` ProblemDetail로 매핑한다.

사용자-facing `detail`: *정보가 변경되었습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.*  
기술 원인(`ex.getMessage()`)은 서버 로그에만 남긴다.

## 테스트

```bash
./gradlew :platform-persistence-jpa:test
```
