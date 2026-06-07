# platform-security 설계 — servlet ServiceToken 인증 공통화

- **일자:** 2026-06-07
- **프로젝트:** spring-platform-commons (공통 자산 build-out)
- **백로그:** vault `wiki/projects/spring-platform-commons/build-out-backlog.md` — P1 `platform-security`
- **상태:** 설계 승인됨 (구현 대기)

## 1. 배경·문제

활성 4서버 중 **MarketPlace·FileFlow** 가 servlet 기반 내부 서비스 간 인증으로
`ServiceTokenAuthenticationFilter` 를 각자 보유한다 (Gateway는 reactive라 제외, servlet 한정).

백로그는 "동일 필터 = 복붙 확정"으로 적었으나, **실측 결과 두 구현은 상당히 갈라져 있다.**

| 측면 | MarketPlace | FileFlow |
|---|---|---|
| 헤더 | `X-Service-Token`만 | `X-Service-Name` + `X-Service-Token` |
| 경로 스코프 | 필터 내부 하드코딩 (`shouldNotFilter` → `/api/v1/market/internal/`, `/api/v1/legacy/`) | 없음 (SecurityConfig matcher에 위임) |
| 토큰 비교 | **타이밍 안전** `MessageDigest.isEqual` | 평문 `.equals()` (타이밍 취약) |
| Role / Principal | `ROLE_INTERNAL_SERVICE` / 정적 `"INTERNAL_SERVICE"` | `ROLE_SERVICE` / 동적 serviceName |
| enabled 플래그 | 없음 (항상 강제) | 있음 (`false`면 anonymous bypass, 로컬 개발용) |
| 로깅 | 없음 | SLF4J debug |
| 설정 | 생성자 raw String(`@Value`) + non-blank 검증 | `@ConfigurationProperties` 객체 |

또한 두 서버의 `SecurityFilterChain`(경로 matcher, MP의 Gateway 필터들, ProblemDetail 포맷)은
**앱마다 다르다**. 단, 401/403을 RFC7807 ProblemDetail로 쓰는 코드는 거의 동일하게 중복된다.

따라서 본 작업의 본질은 "복붙 추출"이 아니라 **갈라진 두 구현을 설정 가능한 하나로 수렴**시키는 것이다.

## 2. 결정 (승인됨)

1. **통합 전략 = 설정 가능한 superset.** properties로 헤더모델·role·principal·enabled·경로스코프를
   선택. MP·FF 둘 다 동작변경 0으로 입양 가능. 단 **보안 개선(타이밍 안전 비교)은 전원 기본값**으로 강제.
2. **모듈 범위 = 3개:** (a) 필터 + properties, (b) ProblemDetail 보안 에러 핸들러, (c) SecurityFilterChain 헬퍼.
3. **와이어링 = zero-config `@AutoConfiguration`** (platform-web ADR-0002·redis와 동일 컨벤션).
   단 `SecurityFilterChain` 자체는 자동등록하지 않는다 (앱 소유, 빈 충돌 회피).

## 3. 모듈·배치

- 새 모듈 `platform-security`, 패키지 `com.ryuqqq.platform.security` (기존 소스 q 3개 규칙 일치).
- 성격: **adapter-in / infra** (servlet 필터 + Spring Security). 도메인 타입 없음 → `platform-*` 무의존.
- 의존성:
  - `implementation` spring-boot-dependencies(platform), spring-boot-autoconfigure,
    `spring-security-web`, `spring-security-config`, spring-web, jackson-databind, slf4j-api
  - `testImplementation` junit-bom(platform), bundles.testing, spring-boot-starter-test, spring-security-test
  - `testRuntimeOnly` junit-platform-launcher
- `settings.gradle`: `include 'platform-security'` + `project(':platform-security').projectDir = file('platform-security')`.
- 루트 `build.gradle`의 `sdkProjects`(`:platform-*`)에 자동 포함 → java-library·maven-publish·sources/javadoc jar.

## 4. 컴포넌트

### 4.1 `config/ServiceTokenProperties` (`@ConfigurationProperties("security.service-token")`)

| 키 | 타입 | 기본값 | 의미·출처 |
|---|---|---|---|
| `enabled` | boolean | `true` | FF. `false`면 토큰 검증 없이 anonymous 부여 (로컬용) |
| `secret` | String | `""` | 공통. 기대 토큰 |
| `token-header` | String | `X-Service-Token` | 공통 |
| `name-header` | String | `X-Service-Name` | FF |
| `role` | String | `ROLE_SERVICE` | 부여 권한. MP는 `ROLE_INTERNAL_SERVICE`로 override |
| `principal` | String | `INTERNAL_SERVICE` | 정적 principal 이름 (MP) |
| `principal-from-name-header` | boolean | `false` | true면 principal = name-header 값 (fallback `principal` → "unknown") (FF) |
| `paths` | List<String> | `[]` | 필터 적용 경로 prefix. 비면 전 경로(스코프는 FilterChain matcher 위임), 채우면 MP식 `shouldNotFilter` |

### 4.2 `filter/ServiceTokenAuthenticationFilter extends OncePerRequestFilter`

- **생성자:** `ServiceTokenProperties` 주입. `enabled=true && secret.isBlank()` → `IllegalArgumentException` fail-fast (MP 검증 보존).
- **`shouldNotFilter`:** `paths` 가 비어있지 않은데 요청 URI가 어떤 prefix와도 매칭 안 되면 `true`(skip). 비어있으면 항상 `false`(전 경로 통과).
- **`doFilterInternal`:**
  - `!enabled` → `grantServiceAccess(anonymousPrincipal)` 후 체인 진행 (FF 로컬 bypass).
  - else: token-header 값을 읽어 secret과 **`MessageDigest.isEqual`(타이밍 안전, UTF-8 bytes)** 비교.
    일치 시 principal 해석(`principal-from-name-header`면 name-header 값/fallback) 후 `PreAuthenticatedAuthenticationToken(principal, null, [role])` 를 `SecurityContextHolder` 에 설정 + debug 로그.
  - 불일치/누락이어도 예외 던지지 않고 체인 진행 (인가 거부는 SecurityFilterChain `authorizeHttpRequests` + entrypoint가 담당 — 기존 동작 보존).

### 4.3 `error/` ProblemDetail 보안 에러 핸들러

- `ServiceTokenAuthenticationEntryPoint implements AuthenticationEntryPoint` → 401, code `SERVICE_TOKEN_REQUIRED`.
- `ServiceTokenAccessDeniedHandler implements AccessDeniedHandler` → 403, code `ACCESS_DENIED`.
- 둘 다 **platform-web `GlobalExceptionHandler` 와 동일 RFC7807 포맷**:
  `ProblemDetail.forStatusAndDetail`, title, type `about:blank`, instance(uri+query),
  properties `timestamp`·`code`·(MDC) `traceId`/`spanId`, 헤더 `x-error-code`, contentType `application/problem+json`.
- 공통 작성 로직은 private writer로 공유. `ObjectMapper` 주입 (응답 직접 write — 필터체인 예외는 `@RestControllerAdvice` 미적용).

### 4.4 `config/ServiceTokenSecurity` (HttpSecurity 헬퍼)

- 자동 `SecurityFilterChain` 빈 ❌ — 앱이 자기 체인을 소유하고 빈 충돌을 피하기 위함.
- static `applyDefaults(HttpSecurity http, ServiceTokenAuthenticationFilter filter, AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler)`:
  csrf disable · sessionManagement STATELESS · `addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)` · exceptionHandling(entryPoint, accessDeniedHandler) 적용.
- 앱은 자기 `authorizeHttpRequests`(경로 matcher) + 부가(MP cors disable·Gateway 필터)만 추가.

### 4.5 `config/PlatformSecurityAutoConfiguration`

```java
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class PlatformSecurityAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(ServiceTokenProperties p) { ... }
    @Bean @ConditionalOnMissingBean
    ServiceTokenAuthenticationEntryPoint serviceTokenAuthenticationEntryPoint(ObjectMapper om) { ... }
    @Bean @ConditionalOnMissingBean
    ServiceTokenAccessDeniedHandler serviceTokenAccessDeniedHandler(ObjectMapper om) { ... }
}
```
- `SecurityFilterChain` 은 등록하지 않는다 (앱 소유).
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 에 등록.

## 5. 입양 적합성 (deferred — 본 작업 범위 아님, 설계 검증용)

- **MP:** `role=ROLE_INTERNAL_SERVICE`, `principal=INTERNAL_SERVICE`, `paths=[/api/v1/market/internal/, /api/v1/legacy/]` → 동작변경 0. 기존 `@Value` 생성자 빈 제거, 자동설정 필터 주입 + `ServiceTokenSecurity.applyDefaults` 호출, 나머지 체인 유지.
- **FF:** `enabled` 플래그 유지, `role=ROLE_SERVICE`, `principal-from-name-header=true` → 동작변경 0. + 평문 비교 → 타이밍 안전 비교로 **보안만 개선**.

## 6. 테스트 (피라미드)

- **단위** (`MockHttpServletRequest`/Mockito):
  - 유효 토큰 → SecurityContext에 `PreAuthenticatedAuthenticationToken`(role) 설정
  - 무효·누락 토큰 → 인증 없음(체인은 진행)
  - `enabled=false` → anonymous principal 부여
  - `paths` 설정 시 매칭/비매칭 `shouldNotFilter` 동작
  - `principal-from-name-header=true` → name 헤더 값이 principal
  - `enabled=true && secret` 공백 → 생성자 예외
  - 타이밍 안전 비교 경로 동작 (동일/상이 토큰)
- **슬라이스** (`ApplicationContextRunner`): 자동설정이 필터·entrypoint·handler 빈 등록, 소비측 동일 타입 빈 정의 시 `@ConditionalOnMissingBean` 양보 (web/redis 자동설정 테스트와 동일 방식).
- **에러핸들러**: entrypoint/handler가 401/403 status·`code`·`x-error-code` 헤더·ProblemDetail 바디를 정확히 write.

## 7. 검증·완료 기준

- [ ] `./gradlew :platform-security:test` green
- [ ] `./gradlew build` (전체) green — archrules·기존 arch 테스트 포함 통과
- [ ] 모듈이 `com.ryuqqq.platform.security` 패키지·기존 SDK 컨벤션 준수
- [ ] superset properties로 MP·FF 양쪽 동작이 표현 가능함을 입양 스케치로 확인
- [ ] 범위 준수: `SecurityFilterChain` 자동등록 안 함, platform-* 무의존, 서버 코드 미변경(입양은 별도)

## 8. 비목표

- 서버(MP·FF) 실제 마이그레이션(입양) — build-out 완료 후 별도.
- reactive(Gateway) 변형 — servlet 한정.
- JWT/Gateway 헤더 인증(`GatewayAuthenticationFilter` 등) 공통화 — 별도 후보.
- rate-limit·idempotency 등 백로그의 다른 P1/P2.
