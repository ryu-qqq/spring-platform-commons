package com.ryuqqq.platform.web.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ryuqqq.platform.common.exception.DomainException;
import com.ryuqqq.platform.common.exception.ErrorCode;
import com.ryuqqq.platform.web.dto.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MDC.clear();
        ErrorMapper specialtyMapper = new ErrorMapper() {
            @Override
            public boolean supports(DomainException ex) {
                return ex.errorCode().getCode().equals("SPECIAL-001");
            }

            @Override
            public MappedError map(DomainException ex, Locale locale) {
                return new MappedError(
                        HttpStatus.CONFLICT,
                        "Special Error",
                        ex.getMessage(),
                        URI.create("/errors/special"));
            }
        };
        ErrorMapperRegistry registry = new ErrorMapperRegistry(List.of(specialtyMapper));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(registry);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("DomainException → ProblemDetail + x-error-code")
    void domainException() throws Exception {
        MDC.put("traceId", "trace-1");
        MDC.put("spanId", "span-1");

        mockMvc.perform(get("/test/domain").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(header().string("x-error-code", "TST-404"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TST-404"))
                .andExpect(jsonPath("$.detail").value("missing"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.spanId").value("span-1"));
    }

    @Test
    @DisplayName("specialty ErrorMapper type URI 반영")
    void specialtyMapper() throws Exception {
        mockMvc.perform(get("/test/special").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/errors/special"))
                .andExpect(jsonPath("$.title").value("Special Error"));
    }

    @Test
    @DisplayName("validation 실패 → errors map")
    void validationErrors() throws Exception {
        mockMvc.perform(
                        post("/test/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    @Test
    @DisplayName("403 AccessDenied")
    void accessDenied() throws Exception {
        mockMvc.perform(get("/test/forbidden").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isForbidden())
                .andExpect(header().string("x-error-code", "ACCESS_DENIED"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("404 NoResourceFound")
    void resourceNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(header().string("x-error-code", "RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("405 Method Not Allowed + Allow 헤더")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/domain").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("x-error-code", "METHOD_NOT_ALLOWED"))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    @DisplayName("409 IllegalStateException")
    void stateConflict() throws Exception {
        mockMvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isConflict())
                .andExpect(header().string("x-error-code", "STATE_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("already exists"));
    }

    @Test
    @DisplayName("500 catch-all Exception")
    void internalError() throws Exception {
        mockMvc.perform(get("/test/boom").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("x-error-code", "INTERNAL_ERROR"));
    }

    record ValidateRequest(@NotBlank String name) {}

    @RestController
    static class TestController {

        @GetMapping("/test/domain")
        ApiResponse<Void> domain() {
            throw new DomainException(
                    new ErrorCode() {
                        @Override
                        public String getCode() {
                            return "TST-404";
                        }

                        @Override
                        public int getHttpStatus() {
                            return 404;
                        }

                        @Override
                        public String getMessage() {
                            return "not found";
                        }
                    },
                    "missing",
                    Map.of());
        }

        @GetMapping("/test/special")
        ApiResponse<Void> special() {
            throw new DomainException(
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
                            return "conflict detail";
                        }
                    },
                    "conflict detail",
                    Map.of("id", 1L));
        }

        @PostMapping("/test/validate")
        ApiResponse<Void> validate(@Valid @RequestBody ValidateRequest request) {
            return ApiResponse.ofSuccess();
        }

        @GetMapping("/test/forbidden")
        ApiResponse<Void> forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/not-found")
        ApiResponse<Void> notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/not-found");
        }

        @GetMapping("/test/conflict")
        ApiResponse<Void> conflict() {
            throw new IllegalStateException("already exists");
        }

        @GetMapping("/test/boom")
        ApiResponse<Void> boom() {
            throw new RuntimeException("boom");
        }
    }
}
