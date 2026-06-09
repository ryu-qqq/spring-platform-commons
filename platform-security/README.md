# platform-security

**Adapter-in 보안 SDK — servlet 내부 서비스 간 Service Token 인증.**

`ServiceTokenAuthenticationFilter` superset 필터 · RFC 7807 `ProblemDetail` 보안 핸들러 · 자동설정을
제공한다. `SecurityFilterChain` 은 모듈이 소유하지 않고 소비측이 소유한다 — 모듈은 재사용 가능한
부품(필터·핸들러·properties)과 공통부 적용 헬퍼(`ServiceTokenSecurity.applyDefaults`)만 제공한다.

## 역할

내부 서비스 간(internal service-to-service) 통신을 공유 비밀(secret)로 인증하는 **servlet 필터 체인
부품**을 제공한다. 게이트웨이/메시 뒤의 신뢰 경계 안에서 서비스가 서로를 호출할 때, 요청 헤더에 실린
service token 을 검증해 `ROLE_SERVICE` 권한을 가진 principal 로 SecurityContext 를 채운다.

- **체인 소유는 소비측** — 모듈이 `SecurityFilterChain` 빈을 등록하지 않으므로 빈 충돌이 없고, 경로
  인가 규칙(`authorizeHttpRequests`)·cors·추가 필터는 소비측이 완전히 통제한다. 공통부(csrf
  disable·stateless·필터 등록·예외 처리)만 `ServiceTokenSecurity.applyDefaults` 로 적용한다.
- **타이밍 안전 비교** — 토큰 검증은 `MessageDigest.isEqual` 로 상수 시간 비교한다(타이밍 공격 회피).
- **인증과 인가의 분리** — 필터는 토큰이 맞으면 인증을 채울 뿐, 실패/누락 시 예외를 던지지 않는다.
  거부(401/403)는 `authorizeHttpRequests` 규칙과 entrypoint/accessDeniedHandler 가 담당한다.
- **ProblemDetail 통일** — 필터체인 예외는 `@RestControllerAdvice` 를 타지 않으므로, 핸들러가
  platform-web `GlobalExceptionHandler` 와 동일한 RFC 7807 포맷으로 직접 응답을 쓴다.

## 인증 흐름

```
요청 → shouldNotFilter(paths prefix 검사)
     → enabled=false ? anonymous 인증 부여 (로컬 개발)
     → tokenHeader 값 == secret (타이밍 안전) ? principal 해석 → ROLE_SERVICE 인증 부여
     → 다음 필터로 (인증 실패해도 통과 — 거부는 인가 단계가 담당)
```

| 단계 | 동작 |
|------|------|
| `shouldNotFilter` | `paths` 가 비면 전 경로 적용. 채우면 해당 prefix 요청에만 필터 적용(contextPath 제거 후 비교). |
| `enabled=false` | 토큰 검증 없이 `anonymous` principal 로 인증 (로컬 개발용). |
| 토큰 일치 | `principal-from-name-header=false` → 정적 `principal`. `true` → `nameHeader` 값(없으면 `unknown`). |
| 인증 미부여 | 예외 없이 통과 — SecurityContext 가 비어 인가 단계에서 401 처리. |

## 확장점

### `ServiceTokenAuthenticationFilter` — 인증 필터

`OncePerRequestFilter` 구현. `ServiceTokenProperties` 로 구성되는 superset 필터다. enabled=true 인데
`secret` 이 비면 생성 시점에 `IllegalArgumentException` 으로 실패한다(fail-fast). 자동설정 빈으로
등록되며 `@ConditionalOnMissingBean` 으로 소비측 재정의를 양보한다.

### `ServiceTokenSecurity.applyDefaults` — 공통부 적용 헬퍼

소비측이 자기 `@Bean SecurityFilterChain` 안에서 호출해 공통부를 적용한다. `authorizeHttpRequests`
는 적용하지 **않는다** — 경로 인가 규칙은 소비측이 정의한다.

```java
@Bean
SecurityFilterChain filterChain(
        HttpSecurity http,
        ServiceTokenAuthenticationFilter filter,
        ServiceTokenAuthenticationEntryPoint entryPoint,
        ServiceTokenAccessDeniedHandler accessDeniedHandler) throws Exception {

    ServiceTokenSecurity.applyDefaults(http, filter, entryPoint, accessDeniedHandler);
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().hasRole("SERVICE"));   // 인가 규칙은 소비측 소유
    return http.build();
}
```

`applyDefaults` 가 적용하는 것: csrf disable · `SessionCreationPolicy.STATELESS` ·
`addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)` ·
exceptionHandling(entryPoint + accessDeniedHandler).

### ProblemDetail 보안 핸들러

필터체인 예외(401/403)를 RFC 7807 `ProblemDetail` JSON 으로 직렬화한다. platform-web 와 동일한
포맷(`title`·`type`·`instance`·`timestamp`·`code`·MDC `traceId`/`spanId`·`x-error-code` 헤더)을 쓴다.

| 핸들러 | 트리거 | status | code |
|--------|--------|--------|------|
| `ServiceTokenAuthenticationEntryPoint` | 미인증 요청 | 401 | `SERVICE_TOKEN_REQUIRED` |
| `ServiceTokenAccessDeniedHandler` | 권한 부족 요청 | 403 | `ACCESS_DENIED` |

둘 다 자동설정 빈이고 `@ConditionalOnMissingBean` 으로 양보한다. `instance` 는 raw 클라이언트 입력인
URI 로 채우되 불정 URI 면 `/` 로 방어한다(500 방지). 응답이 이미 커밋되었으면 조기 리턴한다.

## 설정 — `security.service-token.*`

`@ConfigurationProperties(prefix = "security.service-token")` 으로 바인딩된다.

```yaml
security:
  service-token:
    enabled: true                       # false 면 토큰 검증 없이 anonymous (로컬 개발)
    secret: ${SECURITY_SERVICE_TOKEN_SECRET}   # enabled=true 면 비면 안 됨 (fail-fast)
    token-header: X-Service-Token       # 토큰을 읽을 헤더
    name-header: X-Service-Name         # principal-from-name-header=true 일 때 principal 헤더
    role: ROLE_SERVICE                  # 인증 성공 시 부여 권한
    principal: INTERNAL_SERVICE         # 정적 principal (from-name-header=false)
    principal-from-name-header: false   # true 면 name-header 값에서 principal 동적 해석
    paths: []                           # 비면 전 경로, 채우면 해당 prefix 에만 필터 적용
```

| 키 | 기본값 | 의미 |
|----|--------|------|
| `enabled` | `true` | false 면 토큰 검증 없이 `anonymous` 인증 (로컬 개발용). |
| `secret` | `""` | 기대 토큰. enabled=true 면 비어 있으면 생성 시 실패. |
| `token-header` | `X-Service-Token` | 토큰을 읽을 요청 헤더 이름. |
| `name-header` | `X-Service-Name` | `principal-from-name-header=true` 일 때 principal 을 읽을 헤더. |
| `role` | `ROLE_SERVICE` | 인증 성공 시 부여할 권한. |
| `principal` | `INTERNAL_SERVICE` | 정적 principal 이름. |
| `principal-from-name-header` | `false` | true 면 principal 을 `name-header` 값에서 해석 (fallback `unknown`). |
| `paths` | `[]` | 필터를 적용할 경로 prefix 목록. 비면 모든 경로. |

## 자동 설정

`PlatformSecurityAutoConfiguration` 이 인증 컴포넌트를 등록한다.

- `@ConditionalOnClass(OncePerRequestFilter.class)` — servlet web 스택 존재 시에만.
- `@EnableConfigurationProperties(ServiceTokenProperties.class)`.
- 등록 빈: `ServiceTokenAuthenticationFilter` · `ServiceTokenAuthenticationEntryPoint` ·
  `ServiceTokenAccessDeniedHandler` (entrypoint/handler 는 `ObjectMapper` 주입).
- 모든 빈에 `@ConditionalOnMissingBean` — 소비측 재정의 가능.
- **`SecurityFilterChain` 은 등록하지 않는다** — 체인은 소비측이 소유한다.

## 의존성

```groovy
implementation project(':platform-security')
```

`platform-common-domain`(MDC `MdcKeys` traceId/spanId 키) · spring-security-web/config · jackson 에
의존한다. servlet API 는 `compileOnly` — 런타임에 소비측 starter-web 컨테이너가 제공한다.
