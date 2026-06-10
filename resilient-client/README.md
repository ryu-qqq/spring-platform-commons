# resilient-client

**Resilience4j 기반 외부 호출 회복탄력(resilience) 클라이언트 SDK.**

외부 시스템 호출에 Circuit Breaker · Retry · 메트릭 수집 · 예외 분류를 일관되게 입힌다.
실제 HTTP 전송은 SDK 밖(소비측 `RequestSender`)으로 밀어내어 어떤 HTTP 스택(RestClient, WebClient,
Apache HC5 등)과도 결합 없이 쓰도록 설계했다. 여러 도메인의 외부 연동을 같은 회복탄력 정책 위로
수렴시키는 것이 목표다.

## 역할

- **회복탄력 공통 흐름** — 한 번의 `execute` 호출에 Retry → CircuitBreaker → 전송 → 응답 분류 →
  메트릭 기록의 데코레이션 체인을 자동 적용한다.
- **전송 중립** — HTTP 클라이언트를 모듈 밖으로 분리한다. SDK는 특정 HTTP 인프라에 직접 의존하지
  않고, 소비측이 주입한 `RequestSender`에만 위임한다.
- **예외 계층 일원화** — 외부 호출 실패를 retriable / record-failure 의미를 가진 단일 예외 계층으로
  정규화한다. Retry 대상 여부와 CB 실패 기록 여부가 예외 자체에 인코딩된다.
- **메트릭 optional** — `MeterRegistry`가 있으면 Micrometer 메트릭을 자동 연동하고, 없으면 no-op.
  로깅·전이는 메트릭에 의존하지 않는다.

## 모듈 구성

| 모듈 | 좌표 | 역할 |
|------|------|------|
| core | `resilient-client:resilient-client-core` | `ResilientClient` API, 빌더, 설정, 예외 계층, Resilience4j 데코레이션. 프레임워크 비의존. |
| metrics | `resilient-client:resilient-client-metrics` | Micrometer 기반 `MetricsRecorder` 구현 (`ResilientClientMetricsBinder`, `CircuitBreakerMetricsBinder`). |
| spring-boot-starter | `resilient-client:resilient-client-spring-boot-starter` | 자동설정 · `@ConfigurationProperties` 바인딩 · YAML declarative client 빈 등록. |

core는 Spring 없이도 단독 사용 가능하다. metrics·starter는 선택적 통합이다.

## 핵심 추상

### `ResilientClient` — 진입점

```java
<T> T execute(ExternalRequest request, Class<T> responseType);
<T> T execute(ExternalRequest request, Class<T> responseType,
              Function<ExternalCallException, T> fallback);
void executeVoid(ExternalRequest request);
```

`execute`는 정상 응답을 역직렬화해 반환하고, 실패 시 예외 계층을 던진다. fallback 오버로드는
`ExternalCallException` 계열 발생 시 람다를 호출해 호출 시점에 예외별 분기를 작성하게 한다
(메트릭은 fallback 호출 여부와 무관하게 failure로 이미 기록된다).

### 확장점 (SPI)

소비측이 구현·주입하는 지점이다.

| 확장점 | 인터페이스 | 설명 |
|--------|-----------|------|
| 전송 | `RequestSender` | `RawResponse send(ExternalRequest)`. 실제 HTTP 전송. 함수형. **필수.** |
| 응답 분류 | `ResponseClassifier` | 응답·전송예외 → SDK 예외 변환. 미지정 시 `DefaultResponseClassifier`. |
| 메트릭 | `MetricsRecorder` | 성공·실패·CB 상태 기록. 미지정 시 `MetricsRecorder.NOOP`. |

### 빌더

```java
ResilientClient client = ResilientClient.builder()
    .name("review-api")                  // 메트릭 태그·CB 이름에 사용
    .sender(req -> restClientSend(req))   // RequestSender (필수)
    .circuitBreaker(cb -> cb
        .failureRateThreshold(50)
        .slidingWindowSize(20)
        .waitDurationInOpenState(Duration.ofSeconds(60)))
    .retry(r -> r
        .maxAttempts(3)
        .initialBackoff(Duration.ofMillis(100))
        .backoffMultiplier(2.0))
    .responseClassifier(myClassifier)     // optional
    .metricsRecorder(myRecorder)          // optional, 기본 NOOP
    .build();
```

`name`과 `sender`만 필수이며, 나머지는 기본값(아래 설정 표)으로 동작한다.

## 예외 계층

`ExternalCallException`(abstract)을 정점으로 하며, 각 예외는 `isRetriable()`(Retry 대상)과
`shouldRecordFailure()`(CB 실패로 기록)를 인코딩한다.

| 예외 | retriable | recordFailure | 발생 상황 |
|------|:---------:|:-------------:|-----------|
| `CircuitOpenException` | X | X | CB가 OPEN 상태 (호출 차단). |
| `BadRequestException` | X | X | 4xx 등 클라이언트 오류 (재시도·기록 무의미). |
| `ClientException` | X | X | 분류된 클라이언트 측 오류. |
| `ServerException` | O | O | 5xx 등 서버 오류. 재시도·CB 기록 대상. |
| `NetworkException` | O | O | 연결 실패·타임아웃 등 전송 예외. |

Retry는 `isRetriable()`이 true인 예외만 재시도하고, CircuitBreaker는 `shouldRecordFailure()`가
true인 예외만 실패율에 반영한다(`recordException` 조건).

## 설정 기본값

### CircuitBreaker (`CircuitBreakerConfig`)

| 항목 | 기본값 |
|------|--------|
| `failureRateThreshold` | 50 (%) |
| `slowCallDurationThreshold` | 3s |
| `slowCallRateThreshold` | 80 (%) |
| `slidingWindowSize` | 20 |
| `slidingWindowType` | `COUNT_BASED` |
| `waitDurationInOpenState` | 60s |
| `permittedCallsInHalfOpenState` | 5 |
| `minimumNumberOfCalls` | 10 |

### Retry (`RetryConfig`)

| 항목 | 기본값 |
|------|--------|
| `maxAttempts` | 3 |
| `initialBackoff` | 100ms |
| `backoffMultiplier` | 2.0 (지수 backoff) |

## Spring Boot 통합

`resilient-client-spring-boot-starter`를 의존하면 `ResilientClientAutoConfiguration`이 활성화된다.

- `ResilientClientProperties` 바인딩 (`resilient.client.*`)
- `MeterRegistry` 빈이 있으면 `ResilientClientMetricsBinder`를 `MetricsRecorder`로 자동 등록,
  없으면 `MetricsRecorder.NOOP`
- `ResilientClientFactory` 빈 등록
- `ResilientClientBeansConfiguration` — YAML declarative client를 Spring 빈으로 등록

### YAML declarative client

`enabled`이며 `base-url`이 비어있지 않은 클라이언트를 자동으로 빈 등록한다
(`autoRegisterBeans: false`로 끌 수 있다).

```yaml
resilient:
  client:
    clients:
      callback:
        enabled: true
        base-url: https://api.example.com
        default-headers:
          X-Service-Name: my-service
        circuit-breaker:
          failure-rate-threshold: 50
          slow-call-duration-threshold: 3s
          sliding-window-size: 20
          wait-duration-in-open-state: 60s
          minimum-number-of-calls: 10
        retry:
          max-attempts: 3
          initial-backoff: 100ms
          backoff-multiplier: 2.0
        timeout:
          connect: 3s
          read: 10s
```

## 메트릭

`ResilientClientMetricsBinder`(Micrometer)가 노출하는 미터. 공통 태그 `name`(클라이언트 이름),
`method`(HTTP 메서드), `outcome`(`success`/`error`) 등을 단다.

| 미터 | 타입 | 설명 |
|------|------|------|
| `resilient_client_duration_seconds` | Timer | 요청 소요 시간 |
| `resilient_client_total` | Counter | 요청 총 건수 |
| `resilient_client_errors_total` | Counter | 에러 건수 (`exception` 태그) |
| `resilient_client_retry_total` | Counter | Retry 건수 (`result` 태그) |
| `resilient_client_circuit_breaker_state` | Gauge | CircuitBreaker 상태 |

## 설계 메모

- core는 Resilience4j(`circuitbreaker`, `retry`)에만 의존하고 Spring·Micrometer를 끌어오지 않는다.
  메트릭 인터페이스(`MetricsRecorder`)도 core에 두어 metrics 모듈을 선택적으로 만든다.
- 데코레이션 순서는 Retry가 CircuitBreaker를 감싸 CB OPEN(`CircuitOpenException`, non-retriable)이
  재시도 없이 즉시 전파되도록 한다.
- 응답 → 예외 변환은 `ResponseClassifier`로 분리해, HTTP 의미 체계가 다른 외부 시스템마다 분류
  정책을 교체할 수 있게 한다.
