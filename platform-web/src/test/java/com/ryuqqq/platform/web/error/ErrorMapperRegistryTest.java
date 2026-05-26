package com.ryuqqq.platform.web.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ryuqqq.platform.common.exception.DomainException;
import com.ryuqqq.platform.common.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMapperRegistryTest {

    private enum TestErrorCode implements ErrorCode {
        NOT_FOUND("TST-404", 404, "리소스 없음");

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
    @DisplayName("defaultMapping — status·title·detail·type")
    void defaultMapping() {
        DomainException ex =
                new DomainException(TestErrorCode.NOT_FOUND, "id=1 없음", Map.of("id", 1L));
        ErrorMapperRegistry registry = new ErrorMapperRegistry(List.of());

        ErrorMapper.MappedError mapped = registry.defaultMapping(ex);

        assertThat(mapped.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapped.title()).isEqualTo("Not Found");
        assertThat(mapped.detail()).isEqualTo("id=1 없음");
        assertThat(mapped.type()).isEqualTo(URI.create("about:blank"));
    }

    @Test
    @DisplayName("specialty mapper 우선")
    void specialtyMapperFirst() {
        ErrorMapper specialty = new ErrorMapper() {
            @Override
            public boolean supports(DomainException ex) {
                return ex.errorCode().getCode().startsWith("SPECIAL");
            }

            @Override
            public MappedError map(DomainException ex, Locale locale) {
                return new MappedError(
                        HttpStatus.CONFLICT,
                        "Special",
                        "handled",
                        URI.create("/errors/special"));
            }
        };
        ErrorMapperRegistry registry = new ErrorMapperRegistry(List.of(specialty));
        DomainException ex = new DomainException(
                new ErrorCode() {
                    @Override
                    public String getCode() {
                        return "SPECIAL-001";
                    }

                    @Override
                    public int getHttpStatus() {
                        return 400;
                    }

                    @Override
                    public String getMessage() {
                        return "ignored";
                    }
                },
                "ignored",
                Map.of());

        ErrorMapper.MappedError mapped =
                registry.map(ex, Locale.KOREAN).orElseThrow();

        assertThat(mapped.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapped.type()).hasToString("/errors/special");
    }
}
