# resilient-client SDK — 프로젝트 references 팩

> spring-platform-commons 전용. `resilient-client-dev` skill·`resilience-reviewer`가 Read한다.
> 스택 공통 Resilience4j 원칙은 `.claude/references/java-spring/resilience-patterns.md`와 함께 본다.
> **Wiki SSOT:** `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/resilient-client.md`

## 모듈

| 모듈 | 역할 |
|------|------|
| `resilient-client-core` | ResilientClient 인터페이스·빌더·CB/Retry·예외 분류. Spring 의존성 없음. |
| `resilient-client-metrics` | Micrometer Binder — duration, errors, CB state, retry |
| `resilient-client-spring-boot-starter` | AutoConfiguration, Factory, Properties, **YAML auto-register beans** (v0.2) |

## v0.2 추가 (YAML declarative)

- `ResilientClientBeansConfiguration` — `resilient.client.clients.*` → `{key}ResilientClient` 빈
- `ResilientClientRegistry` — 런타임 lookup
- `ResilientClientRestSupport` / `createRestClientBacked()` — RestClient + timeout wiring
- CB `sliding-window-type`: COUNT_BASED / TIME_BASED
- 조건: `enabled: true` + `base-url` (수동 `@Bean` 있으면 수동 우선)

**adapter-out 배선:** 소비측 adapter-out 빈은 `@DependsOn("resilientClientRegistry")`로 등록 순서 보장.

## 패키지

`com.ryuqqq.platform.resilient` (core) · `...resilient.metrics` · `...resilient.spring`

## 메트릭 이름 (고정)

- `resilient_client_duration_seconds`
- `resilient_client_total`
- `resilient_client_errors_total`
- `resilient_client_circuit_breaker_state`
- `resilient_client_retry_total`

태그: `name`, `outcome`, `method` (+ exception/result where applicable)

## 예외 매핑

`DefaultResponseClassifier` — HTTP status → `ServerException` / `BadRequestException` / `ClientException` / `NetworkException`

## Rate limit

**SDK 범위 밖.** 클러스터 quota/429 → Phase 4 `platform-persistence-redis`. Pod CB와 역할 분리.

## 배포

JitPack: `com.github.ryu-qqq.spring-platform-commons:<module>:v0.2.0` (태그 후)

## 변경 시 검증

```bash
./gradlew :resilient-client:test
./gradlew :adapter-out:client:example-client:test
```

starter 변경 시 `ResilientClientAutoConfigurationTest` 포함.
