# resilient-client SDK Guide (v0.2)

> **Wiki SSOT:** `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/resilient-client.md`  
> Harness references: `.claude/references/resilient-client/sdk-overview.md`

레포 내 빠른 참조. 상세·다이어그램은 wiki 페이지를 따른다.

## Gradle

```groovy
// adapter-out client module
implementation project(':resilient-client:resilient-client-spring-boot-starter')
// JitPack (외부 서비스)
implementation 'com.github.ryu-qqq.spring-platform-commons:resilient-client-spring-boot-starter:v0.1.0'
```

## YAML → 빈 자동 등록

```yaml
resilient:
  client:
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

→ `exampleResilientClient` 빈 + `ResilientClientRegistry`.

## 템플릿 예시

`adapter-out/client/example-client` — HT6 reference wiring.

## 2레이어 resilience

1. **Pod:** `resilient-client` (CB/retry/timeout) — 이 SDK  
2. **Cluster:** Redis rate limit — Phase 4 `platform-persistence-redis` (미구현)

## 테스트

```bash
./gradlew :resilient-client:test
```
