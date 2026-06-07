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
