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
        props.setTokenHeader("X-Internal-Token");
        props.setNameHeader("X-Internal-Name");
        props.setRole("ROLE_INTERNAL_SERVICE");
        props.setPrincipal("MARKETPLACE");
        props.setPrincipalFromNameHeader(true);
        props.setPaths(java.util.List.of("/api/v1/market/internal/"));

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getSecret()).isEqualTo("s3cr3t");
        assertThat(props.getTokenHeader()).isEqualTo("X-Internal-Token");
        assertThat(props.getNameHeader()).isEqualTo("X-Internal-Name");
        assertThat(props.getRole()).isEqualTo("ROLE_INTERNAL_SERVICE");
        assertThat(props.getPrincipal()).isEqualTo("MARKETPLACE");
        assertThat(props.isPrincipalFromNameHeader()).isTrue();
        assertThat(props.getPaths()).containsExactly("/api/v1/market/internal/");
    }

    @Test
    @DisplayName("setPaths 는 방어적 복사 — 원본 리스트 변경이 내부에 전파되지 않는다")
    void setPathsDefensiveCopy() {
        ServiceTokenProperties props = new ServiceTokenProperties();
        java.util.List<String> source = new java.util.ArrayList<>();
        source.add("/api/v1/internal/");
        props.setPaths(source);

        source.add("/should-not-leak/");

        assertThat(props.getPaths()).containsExactly("/api/v1/internal/");
    }

    @Test
    @DisplayName("setPaths(null) 은 빈 리스트로 정규화한다")
    void setPathsNullBecomesEmpty() {
        ServiceTokenProperties props = new ServiceTokenProperties();
        props.setPaths(null);

        assertThat(props.getPaths()).isEmpty();
    }
}
