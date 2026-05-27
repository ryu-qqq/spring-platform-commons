# Spring Platform Commons

헥사고날 5레이어 **템플릿 골격** + **publishable Platform SDK** monorepo.  
샘플 비즈니스 도메인 없이 wiki 패턴대로 `{Domain}` 코드만 채우면 된다.

**컨벤션 SSOT:** Obsidian wiki `wiki/conventions/java-springboot-hexagonal/_overview.md`

## 모듈 지도

```
spring-platform-commons/
├── platform-common-domain/          # HT2 — Query/paging VOs, Versioned, DomainException
├── platform-common-application/     # HT7 — CommonVoFactory
├── platform-web/                    # HT3 — ApiResponse, GEH, RequestContextFilter
├── platform-bootstrap/              # HT4 — logback JSON, actuator, bootstrap yml
├── platform-persistence-jpa/          # P2-1 — BaseAuditEntity, soft delete, @Version, QueryDSL
├── resilient-client/                # v0.2 — CB + Retry + Timeout + metrics + YAML beans
│   ├── resilient-client-core/
│   ├── resilient-client-metrics/
│   └── resilient-client-spring-boot-starter/
├── architecture-tests/              # HT5 — ArchUnit (template + SDK)
│
├── domain/                          # HT1 template — empty aggregate slot
├── application/
├── adapter-in/rest-api/
├── adapter-out/client/example-client/   # HT6 — resilient-client YAML wiring 예시
├── adapter-out/persistence-mysql/example-persistence/  # P2-3 — platform-persistence-jpa wiring 예시
└── bootstrap/bootstrap-web-api/
```

| Phase 0 Task | 상태 |
|--------------|------|
| HT1 Gradle skeleton | ✅ |
| HT2 platform-common-domain | ✅ |
| HT3 platform-web | ✅ |
| HT4 platform-bootstrap | ✅ |
| HT5 architecture-tests | ✅ |
| HT6 example-client | ✅ |
| HT7 platform-common-application | ✅ |
| HT8 docs (이 README · backlog · SDK 가이드) | ✅ |

| Phase 2 Task | 상태 |
|--------------|------|
| P2-1 platform-persistence-jpa v0.1 | ✅ |
| P2-2 OptimisticLock → 409 (platform-web) | ✅ |
| P2-3 example-persistence template wiring | ✅ |
| P2-4 SDK docs · backlog | ✅ |

## 빠른 시작

```bash
./gradlew build
./gradlew :bootstrap:bootstrap-web-api:bootRun   # template API (profile에 따라)
```

## Platform SDK

### resilient-client (v0.2)

외부 HTTP에 **Circuit Breaker + Retry + Timeout + 통일 메트릭**을 강제한다.

**YAML declarative 빈 (권장):**

```yaml
resilient:
  client:
    auto-register-beans: true
    clients:
      example:
        enabled: true
        base-url: https://upstream.example.com
        timeout:
          connect: 3s
          read: 10s
        circuit-breaker:
          sliding-window-type: COUNT_BASED
        retry:
          max-attempts: 2
```

→ `{clientKey}ResilientClient` 빈 + `ResilientClientRegistry` 자동 등록.

**adapter-out 패턴:** `adapter-out/client/example-client` 참고 (`@DependsOn("resilientClientRegistry")`).

| 문서 | 경로 |
|------|------|
| 레포 가이드 | [`docs/sdk/resilient-client.md`](docs/sdk/resilient-client.md) |
| Wiki SSOT | `wiki/projects/spring-platform-commons/resilient-client.md` |

**JitPack** (태그 후):

```groovy
implementation 'com.github.ryu-qqq.spring-platform-commons:resilient-client-spring-boot-starter:v0.2.0'
```

### platform-persistence-jpa (v0.1)

JPA adapter-out 공통 기반 — audit, soft delete, `@Version`, `JPAQueryFactory`.

```groovy
implementation project(':platform-persistence-jpa')
```

**adapter-out 패턴:** `adapter-out/persistence-mysql/example-persistence` 참고 (`platform.example.persistence.enabled=true`).

| 문서 | 경로 |
|------|------|
| 레포 가이드 | [`docs/sdk/platform-persistence-jpa.md`](docs/sdk/platform-persistence-jpa.md) |
| Wiki SSOT | `wiki/projects/spring-platform-commons/platform-persistence-jpa.md` |
| 백로그·로드맵 | [`docs/platform-backlog.md`](docs/platform-backlog.md) |

**JitPack** (태그 후, P0-2 잔여):

```groovy
implementation 'com.github.ryu-qqq.spring-platform-commons:platform-persistence-jpa:v0.1.0'
```

### Resilience 2레이어

| 레이어 | 범위 | SDK |
|--------|------|-----|
| CB + Retry + Timeout | Pod 로컬 | `resilient-client` |
| Rate limit (quota/429) | 클러스터 | Phase 4 `platform-persistence-redis` (예정) |

## Harness · Engineering OS · OpsPilot

| 문서 | 용도 |
|------|------|
| [`docs/engineering-os-runbook.md`](docs/engineering-os-runbook.md) | Notion Task 루프 (eo-start → eo-done) |
| [`docs/platform-backlog.md`](docs/platform-backlog.md) | Phase 0~5 로드맵 |
| [`docs/opspilot-feedback-loop.md`](docs/opspilot-feedback-loop.md) | ingest · work-evaluator · proposal |

**OpsPilot ingest:** Phase Task commit 1회 = ingest 1회. push 후 `docs/opspilot-feedback-loop.md` 순서대로 실행.

## 기술 스택

- Java 21 · Gradle · Spring Boot 3.5
- Resilience4j 2.2 (CB/Retry) · Micrometer
- ArchUnit (레이어 의존 검증)
