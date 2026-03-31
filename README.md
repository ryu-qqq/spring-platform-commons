# Spring Platform Commons

외부 HTTP 호출에 대한 Resilience 패턴을 조직 차원에서 통일하는 공통 SDK.

CB 없이 외부 HTTP를 호출하는 실수가 **구조적으로 불가능**하도록 설계되었으며,
메트릭 이름/태그가 전사 통일되어 Grafana 대시보드 하나로 전체 현황을 파악할 수 있다.

## 모듈 구조

```
resilient-client/
├── resilient-client-core/                ← 핵심 라이브러리 (Spring 의존성 없음)
│   ├── ResilientClient                   (인터페이스 + 빌더)
│   ├── DefaultResilientClient            (CB + Retry + 예외 분류 통합 구현체)
│   ├── RequestSender                     (전송 위임 - RestClient, WebClient 등 자유)
│   ├── ExternalRequest / RawResponse     (요청/응답 모델)
│   ├── ResponseClassifier                (예외 분류기)
│   └── exception/                        (예외 계층)
│
├── resilient-client-metrics/             ← Micrometer 메트릭 통합
│   ├── ResilientClientMetricsBinder      (Binder 패턴 메트릭 기록기)
│   └── CircuitBreakerMetricsBinder       (CB 상태 Gauge)
│
└── resilient-client-spring-boot-starter/ ← Spring Boot 자동 설정
    ├── ResilientClientAutoConfiguration  (MeterRegistry 자동 바인딩)
    ├── ResilientClientFactory            (Properties 기반 팩토리)
    └── ResilientClientProperties         (YAML 바인딩)
```

## 의존성 추가 (JitPack)

**settings.gradle**
```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**build.gradle**
```groovy
// Spring Boot 프로젝트: starter만 추가하면 core + metrics 자동 포함
implementation 'com.github.ryu-qqq.spring-platform-commons:resilient-client-spring-boot-starter:v0.1.0'

// Non-Spring 프로젝트: core만 사용
implementation 'com.github.ryu-qqq.spring-platform-commons:resilient-client-core:v0.1.0'
```

## 사용 예시

### 빌더 패턴 (Non-Spring)

```java
ResilientClient client = ResilientClient.builder()
    .name("payment")
    .sender(request -> {
        // RestClient, WebClient, Apache HC5 등 자유롭게 구현
        ResponseEntity<byte[]> response = restClient
            .method(request.method())
            .uri(URI.create(request.url()))
            .body(request.body())
            .retrieve()
            .toEntity(byte[].class);
        return new RawResponse(
            response.getStatusCode().value(),
            Map.of(),
            response.getBody());
    })
    .circuitBreaker(cb -> cb
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(60)))
    .retry(retry -> retry
        .maxAttempts(3)
        .initialBackoff(Duration.ofMillis(100)))
    .build();

// 사용: CB, Retry, 예외 분류 전부 자동
client.executeVoid(ExternalRequest.post(callbackUrl, payload));
PaymentResult result = client.execute(ExternalRequest.post(url, body), PaymentResult.class);
```

### Spring Boot Starter

**application.yml**
```yaml
resilient:
  client:
    clients:
      callback:
        circuit-breaker:
          failure-rate-threshold: 50
          slow-call-duration-threshold: 3s
          wait-duration-in-open-state: 60s
        retry:
          max-attempts: 3
          initial-backoff: 100ms
          backoff-multiplier: 2.0
```

**Configuration**
```java
@Bean
public ResilientClient callbackClient(ResilientClientFactory factory,
                                      RestClient callbackRestClient) {
    return factory.create("callback", request -> {
        ResponseEntity<byte[]> response = callbackRestClient
            .method(request.method())
            .uri(URI.create(request.url()))
            .body(request.body())
            .retrieve()
            .toEntity(byte[].class);
        return new RawResponse(
            response.getStatusCode().value(), Map.of(), response.getBody());
    });
}
```

**사용**
```java
@Component
public class CallbackNotificationHttpClient {
    private final ResilientClient callbackClient;

    public void notify(String callbackUrl, CallbackPayload payload) {
        callbackClient.executeVoid(ExternalRequest.post(callbackUrl, payload));
        // CB, Retry, 메트릭, 예외 분류 전부 SDK가 처리
    }
}
```

## 메트릭 규격

모든 서비스에서 동일한 이름/태그로 수집된다.

| 메트릭 | 타입 | 태그 | 설명 |
|--------|------|------|------|
| `resilient_client_duration_seconds` | Timer | name, outcome, method | 요청 소요 시간 |
| `resilient_client_total` | Counter | name, outcome, method | 요청 총 건수 |
| `resilient_client_errors_total` | Counter | name, exception, method | 에러 건수 |
| `resilient_client_circuit_breaker_state` | Gauge | name | CB 상태 (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `resilient_client_retry_total` | Counter | name, result | Retry 결과 |

## 예외 계층

```
ExternalCallException (최상위)
├── ServerException          (5xx, CB 기록 O, Retry O)
├── NetworkException         (타임아웃/연결실패, CB 기록 O, Retry O)
├── BadRequestException      (400, CB 기록 X, Retry X)
├── ClientException          (4xx 기타, CB 기록 X, Retry X)
└── CircuitOpenException     (CB OPEN, Retry X)
```

## 기술 스택

- Java 21
- Resilience4j 2.2.0 (CircuitBreaker, Retry)
- Micrometer 1.14.3
- Spring Boot 3.5.6 (Starter 모듈만)
