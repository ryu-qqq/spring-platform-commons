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
        this.paths = (paths == null) ? new ArrayList<>() : new ArrayList<>(paths);
    }
}
