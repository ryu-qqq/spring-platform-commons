package com.ryuqqq.platform.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("성공 응답 — data·timestamp·requestId")
    void successEnvelope() {
        ApiResponse<String> response = ApiResponse.of("ok");

        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.timestamp()).isNotBlank();
        assertThat(response.requestId()).isNotBlank();
    }

    @Test
    @DisplayName("ofSuccess는 data null")
    void ofSuccess() {
        ApiResponse<Void> response = ApiResponse.ofSuccess();

        assertThat(response.data()).isNull();
        assertThat(response.requestId()).isNotBlank();
    }
}
