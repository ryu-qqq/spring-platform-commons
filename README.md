# Spring Platform Commons

헥사고날 서비스용 **공통 플랫폼 SDK** (멀티모듈, JitPack 배포). 4개 활성 서버가 복붙해 쓰던
공통 메커니즘(인증 필터·스케줄러·캐시/락·outbox·관측성 등)을 모듈로 추출해, 서버가 복붙 대신
의존하게 한다.

- **컨벤션 SSOT:** Obsidian wiki `wiki/conventions/java-springboot-hexagonal/_overview.md`
- **변경 이력:** [CHANGELOG.md](CHANGELOG.md) · **버저닝 정책:** [ADR-0004](docs/adr/0004-versioning-release-policy.md)

## 모듈

| 모듈 | 역할 | README |
|------|------|--------|
| **platform-common-domain** | 순수 도메인 타입(VO·페이징·`OutboxStatus`·`MdcKeys`·예외) — 프레임워크 비의존 | [↗](platform-common-domain/README.md) |
| **platform-common-application** | 아웃바운드 포트(`CachePort`·`DistributedLockPort`)·`SchedulerBatchProcessingResult`·`CommonVoFactory` | [↗](platform-common-application/README.md) |
| **platform-web** | adapter-in 웹 코어 — `ApiResponse`·`GlobalExceptionHandler`·`RequestContextFilter` | [↗](platform-web/README.md) |
| **platform-bootstrap** | 조립 레이어 — logback JSON·actuator·bootstrap yml | [↗](platform-bootstrap/README.md) |
| **platform-persistence-jpa** | JPA adapter-out — audit·soft delete·`@Version`·`JPAQueryFactory` | [↗](platform-persistence-jpa/README.md) |
| **platform-redis** | Redisson adapter — `CachePort`·`DistributedLockPort` 구현·자동설정 | [↗](platform-redis/README.md) |
| **platform-scheduler** | `@SchedulerJob` AOP — TraceId/MDC·로깅·메트릭·`SchedulerBatchProcessingResult` | [↗](platform-scheduler/README.md) |
| **platform-security** | Service Token 인증 필터·ProblemDetail 핸들러·자동설정(servlet) | [↗](platform-security/README.md) |
| **platform-outbox** | 트랜스포트 중립 outbox relay — Batch/PerItem 템플릿·`OutboxStore` SPI | [↗](platform-outbox/README.md) |
| **platform-archrules** | 이식 가능 ArchUnit 규칙 3종(헥사고날 경계) + self-test | [↗](platform-archrules/README.md) |
| **resilient-client** | Resilience4j CB+Retry+Timeout+메트릭 (core·metrics·starter) | [↗](resilient-client/README.md) |

> 각 모듈의 역할·확장점·사용 예시는 모듈 README에 있다. 의존하는 법은 아래 **외부에서 가져다 쓰기** 참고.

## 외부에서 가져다 쓰기 (JitPack)

JitPack으로 배포한다. 정식 좌표는 **`com.github.ryu-qqq.spring-platform-commons:<module>:<tag>`**.

`settings.gradle` 또는 루트 `build.gradle`에 JitPack 저장소 추가:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

모듈 의존(artifact = 모듈 디렉토리명, resilient-client는 서브모듈명):

```groovy
// 예: 보안 필터 + 관측성 + 회복탄력 클라이언트
implementation 'com.github.ryu-qqq.spring-platform-commons:platform-security:v0.1.0'
implementation 'com.github.ryu-qqq.spring-platform-commons:platform-web:v0.1.0'
implementation 'com.github.ryu-qqq.spring-platform-commons:resilient-client-spring-boot-starter:v0.1.0'
```

**버전:** 현재 릴리스 태그는 **`v0.1.0`** (고정 권장). `0.2.0`은 아직 미릴리스(Unreleased) —
CHANGELOG 참고. **pre-1.0(0.x)은 minor에서 breaking 변경을 허용**한다(ADR-0004).

> 모노레포 내부(이 레포 안에서)는 `implementation project(':platform-web')` 형태를 쓴다.
> 외부 소비는 위 JitPack 좌표를 쓴다.

## 모노레포 빌드

```bash
./gradlew build          # 전체 빌드·테스트(archrules 포함)
./gradlew :platform-redis:test
```

## Platform SDK 상세 — resilient-client

외부 HTTP에 **Circuit Breaker + Retry + Timeout + 통일 메트릭**을 강제한다. YAML declarative 빈(권장):

```yaml
resilient:
  client:
    auto-register-beans: true
    clients:
      example:
        enabled: true
        base-url: https://upstream.example.com
        timeout: { connect: 3s, read: 10s }
        circuit-breaker: { sliding-window-type: COUNT_BASED }
        retry: { max-attempts: 2 }
```

→ `{clientKey}ResilientClient` 빈 + `ResilientClientRegistry` 자동 등록. adapter-out 패턴은
`adapter-out/client/example-client`(`@DependsOn("resilientClientRegistry")`) 참고.
레포 가이드: [`docs/sdk/resilient-client.md`](docs/sdk/resilient-client.md).

## Harness · Engineering OS · 자가 감사 fleet

| 문서 | 용도 |
|------|------|
| [`docs/engineering-os-runbook.md`](docs/engineering-os-runbook.md) | Notion Task 루프(eo-start → eo-done) |
| [`docs/platform-backlog.md`](docs/platform-backlog.md) | 로드맵·백로그 |
| [`docs/opspilot-feedback-loop.md`](docs/opspilot-feedback-loop.md) | ingest·work-evaluator·proposal |
| `docs/superpowers/audits/` | 자가 감사 스코어카드(autoconfig·observability·versioning) |

`.claude/agents`·`.claude/workflows`에 자가 감사·자율 수정 fleet(감사 → 분류 → 자율수정 → 자동머지)이 있다.

## 기술 스택

- Java 21 · Gradle · Spring Boot 3.5 · Resilience4j 2.2 · Micrometer · ArchUnit
- group `com.ryuqqq.platform` · 배포 JitPack(`com.github.ryu-qqq.spring-platform-commons`)
