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
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        String pathWithinApp = uri;
        return paths.stream().noneMatch(pathWithinApp::startsWith);
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
