# platform-security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** servlet `ServiceTokenAuthenticationFilter` 를 MP·FF가 공유할 수 있는 설정 가능한 superset 모듈 `platform-security` 로 신설한다 (필터 + properties + ProblemDetail 보안 핸들러 + SecurityFilterChain 헬퍼 + zero-config 자동설정).

**Architecture:** 신규 멀티모듈 `platform-security` (패키지 `com.ryuqqq.platform.security`). adapter-in/infra 성격, platform-* 무의존. `@ConfigurationProperties("security.service-token")` 로 헤더모델·role·principal·enabled·경로스코프를 설정. 토큰 비교는 전원 타이밍 안전(`MessageDigest.isEqual`). `@AutoConfiguration` 이 필터·entrypoint·handler 빈을 `@ConditionalOnMissingBean` 으로 등록하되 `SecurityFilterChain` 은 앱이 소유(헬퍼 `ServiceTokenSecurity.applyDefaults` 로 공통부 적용).

**Tech Stack:** Java 21, Spring Boot 3.5.6, Spring Security (web+config), Spring Web servlet filter, Jackson, JUnit5 + AssertJ + Mockito + spring-boot-starter-test. Gradle 멀티모듈(version catalog).

**Spec:** `docs/superpowers/specs/2026-06-07-platform-security-design.md`

---

## File Structure

```
platform-security/
  build.gradle                                          # SDK 모듈 빌드 (Task 1)
  src/main/java/com/ryuqqq/platform/security/
    package-info.java                                   # 모듈 설명 (Task 1)
    config/ServiceTokenProperties.java                  # @ConfigurationProperties (Task 2)
    filter/ServiceTokenAuthenticationFilter.java        # OncePerRequestFilter superset (Task 3)
    error/ServiceTokenProblemDetailWriter.java          # RFC7807 공통 writer (Task 4)
    error/ServiceTokenAuthenticationEntryPoint.java     # 401 핸들러 (Task 4)
    error/ServiceTokenAccessDeniedHandler.java          # 403 핸들러 (Task 4)
    config/PlatformSecurityAutoConfiguration.java       # @AutoConfiguration (Task 5)
    config/ServiceTokenSecurity.java                    # HttpSecurity 헬퍼 (Task 6)
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports   # (Task 5)
  src/test/java/com/ryuqqq/platform/security/
    config/ServiceTokenPropertiesTest.java              # (Task 2)
    filter/ServiceTokenAuthenticationFilterTest.java    # (Task 3)
    error/ServiceTokenErrorHandlerTest.java             # (Task 4)
    config/PlatformSecurityAutoConfigurationTest.java   # (Task 5)
    config/ServiceTokenSecurityIntegrationTest.java     # (Task 6)
settings.gradle                                          # include 추가 (Task 1)
```

루트 `build.gradle` 의 `sdkProjects` 클로저(`:platform-*`)가 java-library·maven-publish·sources/javadoc 를 자동 적용하므로 모듈 `build.gradle` 에 별도 plugin 선언 불필요 (platform-redis 와 동일).

---

## Task 1: 모듈 스캐폴드

**Files:**
- Modify: `settings.gradle`
- Create: `platform-security/build.gradle`
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/package-info.java`

- [ ] **Step 1: settings.gradle 에 모듈 등록**

`include 'platform-scheduler'` 줄 바로 아래에 추가:

```groovy
include 'platform-security'
```

그리고 `project(':platform-scheduler').projectDir = file('platform-scheduler')` 줄 바로 아래에 추가:

```groovy
project(':platform-security').projectDir = file('platform-security')
```

- [ ] **Step 2: build.gradle 작성**

Create `platform-security/build.gradle`:

```groovy
// Adapter-in 보안 SDK — servlet ServiceToken 인증 필터·properties·ProblemDetail 핸들러 + 자동설정.
// SecurityFilterChain 은 소비측이 소유 (ServiceTokenSecurity.applyDefaults 로 공통부 적용).

publishing {
    publications {
        maven(MavenPublication) {
            pom {
                name = 'Platform Security'
                description = 'Adapter-in 보안 SDK — servlet ServiceTokenAuthenticationFilter superset + ProblemDetail 핸들러 + 자동설정.'
            }
        }
    }
}

dependencies {
    implementation platform(libs.spring.boot.dependencies)
    implementation libs.spring.boot.autoconfigure
    implementation 'org.springframework.security:spring-security-web'
    implementation 'org.springframework.security:spring-security-config'
    implementation 'org.springframework:spring-web'
    implementation libs.jackson.databind
    implementation libs.slf4j.api

    testImplementation platform(libs.junit.bom)
    testImplementation libs.bundles.testing
    testImplementation libs.spring.boot.starter.test
    testImplementation libs.spring.boot.starter.web
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

> 비고: production 의존성에는 `spring-boot-starter-web`(tomcat)을 넣지 않는다 — 라이브러리 경량 유지. MockMvc 통합 테스트(Task 6)를 위해 **test 스코프로만** starter-web 을 추가한다.

- [ ] **Step 3: package-info.java 작성**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/package-info.java`:

```java
/**
 * Platform Security — 내부 서비스 간 servlet 인증 공통 모듈.
 *
 * <p>{@code X-Service-Token} 기반 {@link
 * com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter} 를 설정 가능한 superset 으로
 * 제공한다. 헤더모델·role·principal·enabled·경로스코프는 {@code security.service-token.*} 로 설정한다.
 *
 * <p>{@code SecurityFilterChain} 은 소비측이 소유하며, {@link
 * com.ryuqqq.platform.security.config.ServiceTokenSecurity#applyDefaults} 로 공통부(csrf disable·
 * stateless·필터 등록·예외처리)를 적용한다.
 */
package com.ryuqqq.platform.security;
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :platform-security:compileJava`
Expected: `BUILD SUCCESSFUL` (소스 없음 → 통과). settings.gradle 이 모듈을 인식하는지 검증.

- [ ] **Step 5: 커밋**

```bash
git add settings.gradle platform-security/build.gradle platform-security/src/main/java/com/ryuqqq/platform/security/package-info.java
git commit -m "feat(security): platform-security 모듈 스캐폴드 (build-out P1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: ServiceTokenProperties

**Files:**
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenProperties.java`
- Test: `platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenPropertiesTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenPropertiesTest.java`:

```java
package com.ryuqqq.platform.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceTokenPropertiesTest {

    @Test
    @DisplayName("기본값: enabled=true, 표준 헤더, ROLE_SERVICE, 정적 principal, 빈 경로")
    void defaults() {
        ServiceTokenProperties props = new ServiceTokenProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getSecret()).isEmpty();
        assertThat(props.getTokenHeader()).isEqualTo("X-Service-Token");
        assertThat(props.getNameHeader()).isEqualTo("X-Service-Name");
        assertThat(props.getRole()).isEqualTo("ROLE_SERVICE");
        assertThat(props.getPrincipal()).isEqualTo("INTERNAL_SERVICE");
        assertThat(props.isPrincipalFromNameHeader()).isFalse();
        assertThat(props.getPaths()).isEmpty();
    }

    @Test
    @DisplayName("setter 로 값을 변경할 수 있다")
    void setters() {
        ServiceTokenProperties props = new ServiceTokenProperties();
        props.setEnabled(false);
        props.setSecret("s3cr3t");
        props.setRole("ROLE_INTERNAL_SERVICE");
        props.setPrincipalFromNameHeader(true);
        props.setPaths(java.util.List.of("/api/v1/market/internal/"));

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getSecret()).isEqualTo("s3cr3t");
        assertThat(props.getRole()).isEqualTo("ROLE_INTERNAL_SERVICE");
        assertThat(props.isPrincipalFromNameHeader()).isTrue();
        assertThat(props.getPaths()).containsExactly("/api/v1/market/internal/");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenPropertiesTest'`
Expected: FAIL — `ServiceTokenProperties` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenProperties.java`:

```java
package com.ryuqqq.platform.security.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service Token 인증 설정 — servlet 내부 서비스 간 인증의 superset 파라미터.
 *
 * <pre>{@code
 * security:
 *   service-token:
 *     enabled: true
 *     secret: ${SECURITY_SERVICE_TOKEN_SECRET}
 *     role: ROLE_SERVICE
 *     principal: INTERNAL_SERVICE
 *     principal-from-name-header: false
 *     paths: []            # 비면 전 경로, 채우면 해당 prefix 에만 필터 적용
 * }</pre>
 */
@ConfigurationProperties(prefix = "security.service-token")
public class ServiceTokenProperties {

    /** false 면 토큰 검증 없이 anonymous 로 인증 (로컬 개발용). */
    private boolean enabled = true;

    /** 기대 토큰. enabled=true 면 비어 있으면 안 된다. */
    private String secret = "";

    /** 토큰을 읽을 요청 헤더 이름. */
    private String tokenHeader = "X-Service-Token";

    /** principal-from-name-header=true 일 때 principal 을 읽을 헤더 이름. */
    private String nameHeader = "X-Service-Name";

    /** 인증 성공 시 부여할 권한. */
    private String role = "ROLE_SERVICE";

    /** 정적 principal 이름 (principal-from-name-header=false 일 때 사용). */
    private String principal = "INTERNAL_SERVICE";

    /** true 면 principal 을 nameHeader 값에서 동적으로 해석한다 (fallback "unknown"). */
    private boolean principalFromNameHeader = false;

    /** 필터를 적용할 경로 prefix 목록. 비면 모든 경로에 적용한다. */
    private List<String> paths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getNameHeader() {
        return nameHeader;
    }

    public void setNameHeader(String nameHeader) {
        this.nameHeader = nameHeader;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public boolean isPrincipalFromNameHeader() {
        return principalFromNameHeader;
    }

    public void setPrincipalFromNameHeader(boolean principalFromNameHeader) {
        this.principalFromNameHeader = principalFromNameHeader;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenPropertiesTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenProperties.java platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenPropertiesTest.java
git commit -m "feat(security): ServiceTokenProperties superset 설정 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: ServiceTokenAuthenticationFilter

**Files:**
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilter.java`
- Test: `platform-security/src/test/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilterTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-security/src/test/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilterTest.java`:

```java
package com.ryuqqq.platform.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ryuqqq.platform.security.config.ServiceTokenProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceTokenAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ServiceTokenProperties props(String secret) {
        ServiceTokenProperties p = new ServiceTokenProperties();
        p.setSecret(secret);
        return p;
    }

    private void invoke(ServiceTokenAuthenticationFilter filter, MockHttpServletRequest req)
            throws Exception {
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("유효 토큰 → SecurityContext 에 role 권한으로 인증 설정")
    void validTokenAuthenticates() throws Exception {
        ServiceTokenProperties p = props("s3cr3t");
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.addHeader("X-Service-Token", "s3cr3t");

        invoke(filter, req);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("INTERNAL_SERVICE");
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_SERVICE");
    }

    @Test
    @DisplayName("토큰 누락 → 인증 미설정 (체인은 진행)")
    void missingTokenNoAuth() throws Exception {
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(props("s3cr3t"));

        invoke(filter, new MockHttpServletRequest("GET", "/api/v1/x"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("토큰 불일치 → 인증 미설정")
    void wrongTokenNoAuth() throws Exception {
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(props("s3cr3t"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.addHeader("X-Service-Token", "wrong");

        invoke(filter, req);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("enabled=false → 토큰 없이 anonymous 로 인증")
    void disabledGrantsAnonymous() throws Exception {
        ServiceTokenProperties p = new ServiceTokenProperties();
        p.setEnabled(false);
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);

        invoke(filter, new MockHttpServletRequest("GET", "/api/v1/x"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("principal-from-name-header=true → name 헤더 값이 principal")
    void principalFromNameHeader() throws Exception {
        ServiceTokenProperties p = props("s3cr3t");
        p.setPrincipalFromNameHeader(true);
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.addHeader("X-Service-Token", "s3cr3t");
        req.addHeader("X-Service-Name", "marketplace");

        invoke(filter, req);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("marketplace");
    }

    @Test
    @DisplayName("principal-from-name-header=true 인데 name 헤더 없으면 unknown")
    void principalFromNameHeaderFallback() throws Exception {
        ServiceTokenProperties p = props("s3cr3t");
        p.setPrincipalFromNameHeader(true);
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.addHeader("X-Service-Token", "s3cr3t");

        invoke(filter, req);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("unknown");
    }

    @Test
    @DisplayName("paths 설정 시 매칭 경로만 필터링 (비매칭 경로는 유효토큰이어도 인증 미설정)")
    void pathScopingSkipsNonMatching() throws Exception {
        ServiceTokenProperties p = props("s3cr3t");
        p.setPaths(List.of("/api/v1/market/internal/"));
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/public/x");
        req.addHeader("X-Service-Token", "s3cr3t");

        invoke(filter, req);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("paths 설정 시 매칭 경로는 정상 인증")
    void pathScopingFiltersMatching() throws Exception {
        ServiceTokenProperties p = props("s3cr3t");
        p.setPaths(List.of("/api/v1/market/internal/"));
        ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(p);
        MockHttpServletRequest req =
                new MockHttpServletRequest("GET", "/api/v1/market/internal/orders");
        req.addHeader("X-Service-Token", "s3cr3t");

        invoke(filter, req);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("enabled=true 인데 secret 공백이면 생성자에서 예외")
    void blankSecretWhenEnabledThrows() {
        ServiceTokenProperties p = new ServiceTokenProperties(); // enabled=true, secret=""
        assertThatThrownBy(() -> new ServiceTokenAuthenticationFilter(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenAuthenticationFilterTest'`
Expected: FAIL — `ServiceTokenAuthenticationFilter` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilter.java`:

```java
package com.ryuqqq.platform.security.filter;

import com.ryuqqq.platform.security.config.ServiceTokenProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 내부 서비스 간 통신용 Service Token 인증 필터 (설정 가능한 superset).
 *
 * <ul>
 *   <li>{@code enabled=false}: 토큰 검증 없이 anonymous 로 인증 (로컬 개발용)
 *   <li>{@code enabled=true}: {@code tokenHeader} 값을 {@code secret} 과 타이밍 안전 비교
 *   <li>{@code paths} 가 비어 있지 않으면 해당 prefix 요청에만 필터를 적용한다
 * </ul>
 *
 * <p>인증 실패/누락 시 예외를 던지지 않는다 — 인가 거부는 {@code SecurityFilterChain} 의
 * {@code authorizeHttpRequests} 와 entrypoint 가 담당한다.
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceTokenAuthenticationFilter.class);

    private static final String ANONYMOUS_PRINCIPAL = "anonymous";
    private static final String UNKNOWN_PRINCIPAL = "unknown";

    private final ServiceTokenProperties properties;

    public ServiceTokenAuthenticationFilter(ServiceTokenProperties properties) {
        if (properties.isEnabled()
                && (properties.getSecret() == null || properties.getSecret().isBlank())) {
            throw new IllegalArgumentException(
                    "security.service-token.secret must not be blank when enabled=true");
        }
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        List<String> paths = properties.getPaths();
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        String uri = request.getRequestURI();
        return paths.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            grantServiceAccess(ANONYMOUS_PRINCIPAL);
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(properties.getTokenHeader());
        if (token != null && matchesSecret(token)) {
            String principal = resolvePrincipal(request);
            grantServiceAccess(principal);
            log.debug("Service authenticated: principal={}, uri={}", principal, request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesSecret(String token) {
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private String resolvePrincipal(HttpServletRequest request) {
        if (properties.isPrincipalFromNameHeader()) {
            String name = request.getHeader(properties.getNameHeader());
            return (name != null && !name.isBlank()) ? name : UNKNOWN_PRINCIPAL;
        }
        return properties.getPrincipal();
    }

    private void grantServiceAccess(String principal) {
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(properties.getRole())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenAuthenticationFilterTest'`
Expected: PASS (9 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-security/src/main/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilter.java platform-security/src/test/java/com/ryuqqq/platform/security/filter/ServiceTokenAuthenticationFilterTest.java
git commit -m "feat(security): ServiceTokenAuthenticationFilter superset (타이밍 안전 비교)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: ProblemDetail 보안 에러 핸들러

**Files:**
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenProblemDetailWriter.java`
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenAuthenticationEntryPoint.java`
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenAccessDeniedHandler.java`
- Test: `platform-security/src/test/java/com/ryuqqq/platform/security/error/ServiceTokenErrorHandlerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-security/src/test/java/com/ryuqqq/platform/security/error/ServiceTokenErrorHandlerTest.java`:

```java
package com.ryuqqq.platform.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ServiceTokenErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("entrypoint → 401 ProblemDetail, code=SERVICE_TOKEN_REQUIRED, x-error-code 헤더")
    void entryPointWrites401() throws Exception {
        ServiceTokenAuthenticationEntryPoint entryPoint =
                new ServiceTokenAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new BadCredentialsException("nope"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).startsWith("application/problem+json");
        assertThat(res.getHeader("x-error-code")).isEqualTo("SERVICE_TOKEN_REQUIRED");
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SERVICE_TOKEN_REQUIRED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("access denied handler → 403 ProblemDetail, code=ACCESS_DENIED")
    void accessDeniedWrites403() throws Exception {
        ServiceTokenAccessDeniedHandler handler =
                new ServiceTokenAccessDeniedHandler(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.handle(req, res, new AccessDeniedException("denied"));

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getHeader("x-error-code")).isEqualTo("ACCESS_DENIED");
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("status").asInt()).isEqualTo(403);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenErrorHandlerTest'`
Expected: FAIL — 핸들러 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 공통 writer 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenProblemDetailWriter.java`:

```java
package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * 보안 필터체인 예외(401/403)를 RFC 7807 {@link ProblemDetail} 로 직렬화한다.
 *
 * <p>platform-web {@code GlobalExceptionHandler} 와 동일한 포맷(title·type·instance·
 * timestamp·code·MDC traceId/spanId·{@code x-error-code} 헤더)을 사용한다. 필터체인 예외는
 * {@code @RestControllerAdvice} 를 타지 않으므로 여기서 직접 응답을 작성한다.
 */
final class ServiceTokenProblemDetailWriter {

    private final ObjectMapper objectMapper;

    ServiceTokenProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request)
            throws IOException {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));

        String uri = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uri = uri + "?" + request.getQueryString();
        }
        pd.setInstance(URI.create(uri));

        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("code", code);

        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        if (spanId != null) {
            pd.setProperty("spanId", spanId);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("x-error-code", code);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
```

- [ ] **Step 4: entrypoint 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenAuthenticationEntryPoint.java`:

```java
package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 인증되지 않은 요청에 401 ProblemDetail({@code SERVICE_TOKEN_REQUIRED})을 응답한다. */
public class ServiceTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceTokenAuthenticationEntryPoint.class);
    private static final String CODE = "SERVICE_TOKEN_REQUIRED";

    private final ServiceTokenProblemDetailWriter writer;

    public ServiceTokenAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.writer = new ServiceTokenProblemDetailWriter(objectMapper);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        log.warn("Unauthorized: uri={}, reason={}", request.getRequestURI(), authException.getMessage());
        writer.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Service token is missing or invalid",
                CODE,
                request);
    }
}
```

- [ ] **Step 5: access denied handler 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/error/ServiceTokenAccessDeniedHandler.java`:

```java
package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 권한이 없는 요청에 403 ProblemDetail({@code ACCESS_DENIED})을 응답한다. */
public class ServiceTokenAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceTokenAccessDeniedHandler.class);
    private static final String CODE = "ACCESS_DENIED";

    private final ServiceTokenProblemDetailWriter writer;

    public ServiceTokenAccessDeniedHandler(ObjectMapper objectMapper) {
        this.writer = new ServiceTokenProblemDetailWriter(objectMapper);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("AccessDenied: uri={}, reason={}", request.getRequestURI(), accessDeniedException.getMessage());
        writer.write(response, HttpStatus.FORBIDDEN, "Forbidden", "접근 권한이 없습니다", CODE, request);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenErrorHandlerTest'`
Expected: PASS (2 tests).

- [ ] **Step 7: 커밋**

```bash
git add platform-security/src/main/java/com/ryuqqq/platform/security/error/ platform-security/src/test/java/com/ryuqqq/platform/security/error/
git commit -m "feat(security): ProblemDetail 보안 에러 핸들러(401/403) — platform-web 포맷 일관

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: PlatformSecurityAutoConfiguration

**Files:**
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfiguration.java`
- Create: `platform-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `platform-security/src/test/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfigurationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

Create `platform-security/src/test/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfigurationTest.java`:

```java
package com.ryuqqq.platform.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withConfiguration(
                            AutoConfigurations.of(PlatformSecurityAutoConfiguration.class))
                    .withPropertyValues("security.service-token.secret=test-secret");

    @Test
    @DisplayName("필터·entrypoint·handler 빈이 자동 등록된다")
    void registersBeans() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(ServiceTokenAuthenticationFilter.class)
                .hasSingleBean(ServiceTokenAuthenticationEntryPoint.class)
                .hasSingleBean(ServiceTokenAccessDeniedHandler.class));
    }

    @Test
    @DisplayName("소비측이 동일 타입 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnFilter() {
        runner.withBean(
                        "customFilter",
                        ServiceTokenAuthenticationFilter.class,
                        () -> {
                            ServiceTokenProperties p = new ServiceTokenProperties();
                            p.setSecret("test-secret");
                            return new ServiceTokenAuthenticationFilter(p);
                        })
                .run(context -> {
                    assertThat(context).hasSingleBean(ServiceTokenAuthenticationFilter.class);
                    assertThat(context.getBeanNamesForType(ServiceTokenAuthenticationFilter.class))
                            .containsExactly("customFilter");
                });
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-security:test --tests '*PlatformSecurityAutoConfigurationTest'`
Expected: FAIL — `PlatformSecurityAutoConfiguration` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 자동설정 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfiguration.java`:

```java
package com.ryuqqq.platform.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Platform Security 자동 설정 — Service Token 인증 컴포넌트를 등록한다.
 *
 * <p>{@code SecurityFilterChain} 은 등록하지 않는다 — 소비측이 자기 체인을 소유하고, {@link
 * ServiceTokenSecurity#applyDefaults} 로 공통부를 적용한 뒤 본 자동설정의 필터·entrypoint·handler 빈을
 * 주입한다. 소비측이 동일 타입 빈을 정의하면 {@link ConditionalOnMissingBean} 으로 양보한다.
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(
            ServiceTokenProperties properties) {
        return new ServiceTokenAuthenticationFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAuthenticationEntryPoint serviceTokenAuthenticationEntryPoint(
            ObjectMapper objectMapper) {
        return new ServiceTokenAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenAccessDeniedHandler serviceTokenAccessDeniedHandler(
            ObjectMapper objectMapper) {
        return new ServiceTokenAccessDeniedHandler(objectMapper);
    }
}
```

- [ ] **Step 4: imports 파일 작성**

Create `platform-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.ryuqqq.platform.security.config.PlatformSecurityAutoConfiguration
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :platform-security:test --tests '*PlatformSecurityAutoConfigurationTest'`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add platform-security/src/main/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfiguration.java platform-security/src/main/resources/META-INF/spring/ platform-security/src/test/java/com/ryuqqq/platform/security/config/PlatformSecurityAutoConfigurationTest.java
git commit -m "feat(security): PlatformSecurityAutoConfiguration zero-config 빈 등록

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: ServiceTokenSecurity 헬퍼 + 통합 검증

**Files:**
- Create: `platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenSecurity.java`
- Test: `platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenSecurityIntegrationTest.java`

- [ ] **Step 1: 실패 통합 테스트 작성**

이 테스트는 실제 `SecurityFilterChain` 을 `ServiceTokenSecurity.applyDefaults` 로 구성하고 MockMvc 로 호출하여 필터+헬퍼+entrypoint 가 함께 동작하는지 검증한다 (헬퍼 클래스를 컴파일 의존으로 끌어와 TDD 를 강제).

Create `platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenSecurityIntegrationTest.java`:

```java
package com.ryuqqq.platform.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ServiceTokenSecurityIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.service-token.secret=s3cr3t")
class ServiceTokenSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없음 → 401 ProblemDetail")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("x-error-code", "SERVICE_TOKEN_REQUIRED"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_TOKEN_REQUIRED"));
    }

    @Test
    @DisplayName("유효 토큰 → 200")
    void validTokenReturns200() throws Exception {
        mockMvc.perform(get("/ping").header("X-Service-Token", "s3cr3t"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/ping").header("X-Service-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                ServiceTokenAuthenticationFilter filter,
                ServiceTokenAuthenticationEntryPoint entryPoint,
                ServiceTokenAccessDeniedHandler accessDeniedHandler)
                throws Exception {
            ServiceTokenSecurity.applyDefaults(http, filter, entryPoint, accessDeniedHandler);
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }

        @RestController
        static class PingController {
            @GetMapping("/ping")
            String ping() {
                return "pong";
            }
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenSecurityIntegrationTest'`
Expected: FAIL — `ServiceTokenSecurity` 클래스 없음 (컴파일 에러).

- [ ] **Step 3: 헬퍼 구현**

Create `platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenSecurity.java`:

```java
package com.ryuqqq.platform.security.config;

import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Service Token 인증을 위한 {@code HttpSecurity} 공통 설정 헬퍼.
 *
 * <p>소비측은 자기 {@code @Bean SecurityFilterChain} 안에서 {@link #applyDefaults} 로 공통부(csrf
 * disable·stateless·필터 등록·예외처리)를 적용한 뒤, 자신의 {@code authorizeHttpRequests} 경로 규칙과
 * 부가 설정(cors·추가 필터)을 더한다. 모듈이 {@code SecurityFilterChain} 을 직접 등록하지 않으므로 빈
 * 충돌이 없고 소비측이 체인을 완전히 통제한다.
 */
public final class ServiceTokenSecurity {

    private ServiceTokenSecurity() {}

    /**
     * csrf disable · stateless 세션 · ServiceToken 필터 등록 ·
     * ProblemDetail entrypoint/accessDeniedHandler 예외처리를 {@code http} 에 적용한다.
     *
     * <p>{@code authorizeHttpRequests} 는 적용하지 않는다 — 경로 인가 규칙은 소비측이 정의한다.
     */
    public static void applyDefaults(
            HttpSecurity http,
            ServiceTokenAuthenticationFilter filter,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :platform-security:test --tests '*ServiceTokenSecurityIntegrationTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add platform-security/src/main/java/com/ryuqqq/platform/security/config/ServiceTokenSecurity.java platform-security/src/test/java/com/ryuqqq/platform/security/config/ServiceTokenSecurityIntegrationTest.java
git commit -m "feat(security): ServiceTokenSecurity HttpSecurity 헬퍼 + MockMvc 통합 검증

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 전체 빌드 검증 + 백로그 갱신

**Files:**
- Modify: `/Users/ryu-qqq/Documents/ryu-qqq-wiki/wiki/projects/spring-platform-commons/build-out-backlog.md`

- [ ] **Step 1: 모듈 전체 테스트**

Run: `./gradlew :platform-security:test`
Expected: PASS (전 테스트 — properties 2 + filter 9 + error 2 + autoconfig 2 + integration 3 = 18).

- [ ] **Step 2: 전체 빌드 (archrules·기존 arch 테스트 포함)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. platform-archrules 및 기존 모든 모듈 테스트 통과.

> 만약 실패하면 systematic-debugging 으로 원인 분석 후 수정 — 가정으로 넘어가지 말 것.

- [ ] **Step 3: 백로그 P1 항목 완료 반영**

`build-out-backlog.md` 의 우선순위 섹션에서 `platform-security` 줄을 완료로 갱신:

기존:
```markdown
- **P1** `platform-security` (servlet `ServiceTokenAuthenticationFilter`+properties) ← **다음**
```

변경:
```markdown
- **P1** `platform-security` — ✅ **완료**. 갈라진 MP·FF `ServiceTokenAuthenticationFilter` 를 설정 가능한 superset(`security.service-token.*`)으로 수렴. 필터+properties·ProblemDetail 보안 핸들러(401/403, platform-web 포맷)·`ServiceTokenSecurity.applyDefaults` 헬퍼·zero-config 자동설정. 토큰 비교는 전원 타이밍 안전. `SecurityFilterChain` 은 앱 소유. **다음=`platform-outbox`**.
```

그리고 "다음" 표시를 outbox 로 이동:
```markdown
- **P1** `platform-outbox` (relay template + adapter SPI; FileFlow Template 참조) ← **다음**
```

변경 이력에 한 줄 추가:
```markdown
- 2026-06-07: P1 `platform-security` 완료 반영. MP·FF 필터가 실측상 갈라져 있어 "복붙"이 아닌 superset 수렴으로 설계. 다음=`platform-outbox`.
```

- [ ] **Step 4: 백로그 커밋 (vault repo)**

```bash
cd /Users/ryu-qqq/Documents/ryu-qqq-wiki && git add wiki/projects/spring-platform-commons/build-out-backlog.md && git commit -m "docs(platform): platform-security build-out 완료 반영"
```

> vault 는 별도 repo 이므로 commit 위치에 주의. 실패 시 vault repo 가 git 관리 대상인지 먼저 확인.

- [ ] **Step 5: 완료 보고**

`work-evaluator` 4축(가정금지·최소·범위·검증) self-check 후 완료 보고. 입양(MP·FF 마이그레이션)은 build-out 완료 후 별도임을 명시.

---

## Self-Review (작성자 점검 결과)

- **Spec coverage:** §3 모듈배치→Task1, §4.1 properties→Task2, §4.2 필터→Task3, §4.3 에러핸들러→Task4, §4.5 자동설정→Task5, §4.4 헬퍼→Task6, §6 테스트(단위·슬라이스·에러·통합)→Task3/5/4/6, §7 검증기준→Task7. 전 항목 매핑됨.
- **Placeholder scan:** TBD/TODO 없음. 모든 코드 스텝은 완전한 소스 포함.
- **Type consistency:** `ServiceTokenProperties` getter/setter 명이 필터·자동설정·테스트에서 일치(`isEnabled`·`getSecret`·`getTokenHeader`·`getNameHeader`·`getRole`·`getPrincipal`·`isPrincipalFromNameHeader`·`getPaths`). 핸들러 코드 상수(`SERVICE_TOKEN_REQUIRED`·`ACCESS_DENIED`)가 테스트 기대값과 일치. `ServiceTokenSecurity.applyDefaults` 시그니처가 통합 테스트 호출과 일치.
- **의존성 주의:** production 에 starter-web 미포함, test 스코프로만 추가 — 통합 테스트(MockMvc)용. 라이브러리 경량 유지.
