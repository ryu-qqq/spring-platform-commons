package com.ryuqqq.platform.common.exception;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    private enum TestErrorCode implements ErrorCode {
        NOT_FOUND("TST-001", 404, "리소스를 찾을 수 없습니다");

        private final String code;
        private final int httpStatus;
        private final String message;

        TestErrorCode(String code, int httpStatus, String message) {
            this.code = code;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public int getHttpStatus() {
            return httpStatus;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    @Test
    @DisplayName("ErrorCode + message + args 보존")
    void preservesErrorCodeAndArgs() {
        DomainException ex =
                new DomainException(TestErrorCode.NOT_FOUND, "id=42 없음", Map.of("id", 42L));

        assertThat(ex.errorCode()).isEqualTo(TestErrorCode.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("id=42 없음");
        assertThat(ex.args()).containsEntry("id", 42L);
        assertThat(ex.errorCode().getHttpStatus()).isEqualTo(404);
        assertThat(ex.code()).isEqualTo("TST-001");
        assertThat(ex.httpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("ErrorCode만으로 기본 메시지 사용")
    void defaultMessageFromErrorCode() {
        DomainException ex = new DomainException(TestErrorCode.NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo(TestErrorCode.NOT_FOUND.getMessage());
        assertThat(ex.args()).isEmpty();
    }
}
