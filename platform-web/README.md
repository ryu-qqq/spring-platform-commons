# platform-web

**헥사고날 adapter-in 웹 코어 SDK.**

REST 어댑터 계층에서 반복되는 표준 응답 봉투·전역 예외 처리·요청 추적 컨텍스트를 한 모듈로 묶는다.
소비측은 `platform-web` 의존만 추가하면 자동설정으로 표준 동작을 받고, 도메인별로 갈리는 예외 매핑만
`ErrorMapper` 확장점으로 제공한다.

## 역할

웹 어댑터가 공통으로 떠안던 세 가지 책임을 모듈로 수렴시킨다.

- **표준 응답 봉투** — 성공은 `ApiResponse`(payload + timestamp + requestId), 실패는 RFC 7807
  `ProblemDetail`. 컨트롤러마다 응답 포맷을 다시 짜지 않는다.
- **전역 예외 처리** — `GlobalExceptionHandler`(`@RestControllerAdvice`)가 도메인 예외·검증 실패·프레임워크
  예외를 일관된 `ProblemDetail`로 변환한다. `traceId`/`spanId`/`code`를 본문과 `x-error-code` 헤더에 싣는다.
- **요청 추적 컨텍스트** — `RequestContextFilter`가 게이트웨이 전달 헤더에서 `traceId`·`userId`·`tenantId`를
  MDC에 채우고, 없으면 `traceId`를 생성해 응답 헤더로 echo 한다.

이 모듈은 특정 웹 프레임워크 빈 스캔에 의존하지 않는다(`@ComponentScan` 미사용). 라이브러리 모범을 따라
`@AutoConfiguration` + 명시적 `@Bean`으로 등록하고, 모든 빈은 `@ConditionalOnMissingBean`이라 소비측이
동일 타입 빈으로 override 할 수 있다.

## 자동설정 — `PlatformWebAutoConfiguration`

`@ConditionalOnWebApplication(SERVLET)` 조건에서 다음 빈을 등록한다.

| 빈 | 역할 |
|----|------|
| `ErrorMapperRegistry` | 소비측이 등록한 `ErrorMapper` 들을 모아 도메인 예외 매핑을 위임. |
| `GlobalExceptionHandler` | 전역 예외 → `ProblemDetail` 변환 advice. |
| `RequestContextFilter` | trace context 헤더 → MDC 채움(`HIGHEST_PRECEDENCE`). |

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 에 등록되어 있어
`platform-web` 의존만 추가하면 zero-config로 활성화된다.

## 확장점 — `ErrorMapper` / `ErrorMapperRegistry`

도메인 예외(`com.ryuqqq.platform.common.exception.DomainException`)를 HTTP 응답으로 바꾸는 규칙을
소비측이 per-domain `@Component`로 제공한다.

```java
public interface ErrorMapper {
    boolean supports(DomainException ex);              // 이 mapper가 처리할 예외인지
    MappedError map(DomainException ex, Locale locale); // HTTP status·title·detail·type URI 변환
}
```

- `ErrorMapperRegistry`가 등록된 mapper들을 순회해 **첫 매칭 mapper**를 사용한다.
- 매칭되는 mapper가 없으면 `ErrorMapperRegistry.defaultMapping` — `ErrorCode`의 httpStatus 기반 기본 매핑으로
  폴백한다(해석 불가 시 `500 INTERNAL_SERVER_ERROR`).

### 사용 예

```java
@Component
class OrderNotFoundMapper implements ErrorMapper {
    @Override
    public boolean supports(DomainException ex) {
        return ex.code().equals("ORDER_NOT_FOUND");
    }

    @Override
    public MappedError map(DomainException ex, Locale locale) {
        return new MappedError(
                HttpStatus.NOT_FOUND,
                "주문을 찾을 수 없습니다",
                ex.getMessage(),
                URI.create("https://errors.example.com/order-not-found"));
    }
}
```

소비측은 `ErrorMapper` 빈만 등록하면 된다 — 레지스트리 배선은 자동설정이 처리한다.

## 응답 DTO

| 타입 | 용도 |
|------|------|
| `ApiResponse<T>` | 단건/일반 성공 응답 봉투(`data` + `timestamp` + `requestId`). |
| `PageApiResponse<T>` | 페이지네이션 응답(`content` + `PageMeta`). |
| `SliceApiResponse<T, C>` | 커서/슬라이스 응답(`content` + `SliceMeta`). |

`requestId`는 MDC `traceId`를 우선 사용하고 없으면 UUID로 채운다 — `RequestContextFilter`가 채운 추적
컨텍스트와 자연스럽게 연결된다.

## 예외 처리 매핑표 — `GlobalExceptionHandler`

| 예외 | HTTP | code |
|------|------|------|
| `DomainException` | `ErrorMapper` 매핑(폴백 = ErrorCode httpStatus) | `ex.code()` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` (필드 오류를 `errors`에 수록) |
| `MissingRequestHeaderException` | 400 | `MISSING_HEADER` |
| `IllegalArgumentException` | 400 | `INVALID_ARGUMENT` |
| `NoResourceFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` (`Allow` 헤더 포함) |
| `IllegalStateException` | 409 | `STATE_CONFLICT` |
| `OptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_CONFLICT` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| `Exception` (그 외 전부) | 500 | `INTERNAL_ERROR` |

모든 응답은 `application/problem+json` 미디어 타입과 `x-error-code` 헤더를 싣는다. 5xx는 `error`,
404는 `debug`, 그 외 클라이언트 오류는 `warn`으로 로깅한다.

## 추적 컨텍스트 — `RequestContextFilter`

게이트웨이가 전달하는 헤더를 MDC로 옮긴다. 키·헤더 이름은 `platform-common-domain`의
`com.ryuqqq.platform.common.observability.MdcKeys` SSOT를 따른다.

- `X-Trace-Id` → MDC `traceId`. 없으면 32자리 소문자 hex(W3C Trace Context/OTel 호환)로 생성하고 응답
  헤더로 echo 한다.
- `userId`·`tenantId` 헤더는 존재할 때만 MDC에 채운다.
- `spanId`는 추적 계측(tracing instrumentation) 소유라 이 필터에서 set 하지 않는다.
- 요청 종료 시 `MDC.clear()`로 스레드 누수를 방지한다(`OncePerRequestFilter`).

## 의존

- `platform-common-domain` — `DomainException` · `ErrorCode` · `MdcKeys` SSOT.
- `spring-boot-starter-web` · `spring-boot-autoconfigure` — 서블릿 웹 + 자동설정.
- `spring-security-core` — `AccessDeniedException` 처리.

## 사용

```kotlin
// build.gradle(.kts)
dependencies {
    implementation("com.ryuqqq:platform-web:<version>")
}
```

서블릿 웹 애플리케이션이면 별도 설정 없이 표준 응답·예외·추적 빈이 활성화된다. 도메인 예외 매핑이 필요하면
`ErrorMapper` 빈을 추가하고, 기본 동작을 바꾸려면 동일 타입 빈을 직접 정의해 override 한다.
