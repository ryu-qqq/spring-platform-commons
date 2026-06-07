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
