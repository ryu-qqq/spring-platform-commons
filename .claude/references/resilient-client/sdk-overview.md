# resilient-client SDK — 프로젝트 references 팩

> spring-platform-commons 전용. `resilient-client-dev` skill·`resilience-reviewer`가 Read한다.
> 스택 공통 Resilience4j 원칙은 `.claude/references/java-spring/resilience-patterns.md`와 함께 본다.

## 모듈

| 모듈 | 역할 |
|------|------|
| `resilient-client-core` | ResilientClient 인터페이스·빌더·CB/Retry·예외 분류. Spring 의존성 없음. |
| `resilient-client-metrics` | Micrometer Binder — duration, errors, CB state, retry |
| `resilient-client-spring-boot-starter` | AutoConfiguration, Factory, Properties |

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

## 배포

JitPack: `com.github.ryu-qqq.spring-platform-commons:<module>:vX.Y.Z`

## 변경 시 검증

```bash
./gradlew test
```

모듈별 테스트 필수. starter 변경 시 `ResilientClientAutoConfigurationTest` 포함.
