package com.ryuqqq.platform.web.error;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ryuqqq.platform.common.exception.DomainException;

/**
 * 글로벌 REST 예외 처리 — 성공 {@link com.ryuqqq.platform.web.dto.ApiResponse}, 실패 RFC 7807
 * {@link ProblemDetail}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String MISSING_HEADER = "MISSING_HEADER";
    private static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    private static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    private static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    private static final String STATE_CONFLICT = "STATE_CONFLICT";
    private static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";
    private static final String ACCESS_DENIED = "ACCESS_DENIED";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final ErrorMapperRegistry errorMapperRegistry;

    public GlobalExceptionHandler(ErrorMapperRegistry errorMapperRegistry) {
        this.errorMapperRegistry = errorMapperRegistry;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(
            DomainException ex, HttpServletRequest req, Locale locale) {
        ErrorMapper.MappedError mapped =
                errorMapperRegistry.map(ex, locale).orElseGet(() -> errorMapperRegistry.defaultMapping(ex));

        ResponseEntity<ProblemDetail> response =
                build(mapped.status(), mapped.title(), mapped.detail(), ex.code(), req);
        ProblemDetail body = response.getBody();
        assert body != null;
        body.setType(mapped.type());
        if (!ex.args().isEmpty()) {
            body.setProperty("args", ex.args());
        }

        if (mapped.status().is5xxServerError()) {
            log.error(
                    "DomainException (Server Error): code={}, status={}, detail={}, args={}",
                    ex.code(),
                    mapped.status().value(),
                    mapped.detail(),
                    ex.args(),
                    ex);
        } else if (mapped.status() == HttpStatus.NOT_FOUND) {
            log.debug(
                    "DomainException (Not Found): code={}, status={}, detail={}, args={}",
                    ex.code(),
                    mapped.status().value(),
                    mapped.detail(),
                    ex.args());
        } else {
            log.warn(
                    "DomainException (Client Error): code={}, status={}, detail={}, args={}",
                    ex.code(),
                    mapped.status().value(),
                    mapped.detail(),
                    ex.args());
        }

        return ResponseEntity.status(mapped.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("x-error-code", ex.code())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ResponseEntity<ProblemDetail> response =
                build(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        "Validation failed for request",
                        VALIDATION_FAILED,
                        req);
        ProblemDetail body = response.getBody();
        assert body != null;
        body.setProperty("errors", errors);
        log.warn("MethodArgumentNotValid: code={}, errors={}", VALIDATION_FAILED, errors);
        return response;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest req) {
        String detail = "Required header missing: " + ex.getHeaderName();
        log.warn("MissingRequestHeader: code={}, header={}", MISSING_HEADER, ex.getHeaderName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", detail, MISSING_HEADER, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        String detail = Optional.ofNullable(ex.getMessage()).orElse("Invalid argument");
        log.warn("IllegalArgument: code={}, message={}", INVALID_ARGUMENT, detail);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", detail, INVALID_ARGUMENT, req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest req) {
        log.debug(
                "NoResourceFound: code={}, resourcePath={}", RESOURCE_NOT_FOUND, ex.getResourcePath());
        return build(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "요청한 리소스를 찾을 수 없습니다",
                RESOURCE_NOT_FOUND,
                req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        String method = Optional.ofNullable(ex.getMethod()).orElse("UNKNOWN");
        Set<HttpMethod> supported =
                Optional.ofNullable(ex.getSupportedHttpMethods()).orElse(Collections.emptySet());
        String supportedStr =
                supported.isEmpty()
                        ? "없음"
                        : supported.stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        String message = "%s 메서드는 지원하지 않습니다. 지원되는 메서드: %s".formatted(method, supportedStr);

        ResponseEntity<ProblemDetail> entity =
                build(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "Method Not Allowed",
                        message,
                        METHOD_NOT_ALLOWED,
                        req);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.add("x-error-code", METHOD_NOT_ALLOWED);
        if (!supported.isEmpty()) {
            headers.setAllow(supported);
        }

        log.warn(
                "MethodNotAllowed: code={}, method={}, supported={}",
                METHOD_NOT_ALLOWED,
                method,
                supportedStr);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(entity.getBody());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        String detail = Optional.ofNullable(ex.getMessage()).orElse("State conflict");
        log.warn("IllegalState: code={}, message={}", STATE_CONFLICT, detail);
        return build(HttpStatus.CONFLICT, "Conflict", detail, STATE_CONFLICT, req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("OptimisticLockingFailure: code={}, message={}", OPTIMISTIC_LOCK_CONFLICT, ex.getMessage());
        return build(
                HttpStatus.CONFLICT,
                "Conflict",
                "다른 트랜잭션에서 리소스가 수정되었습니다. 다시 조회 후 시도해주세요.",
                OPTIMISTIC_LOCK_CONFLICT,
                req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        log.warn("AccessDenied: code={}, message={}", ACCESS_DENIED, ex.getMessage());
        return build(
                HttpStatus.FORBIDDEN, "Forbidden", "접근 권한이 없습니다", ACCESS_DENIED, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGlobal(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error occurred: code={}", INTERNAL_ERROR, ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                INTERNAL_ERROR,
                req);
    }

    private ResponseEntity<ProblemDetail> build(
            HttpStatus status, String title, String detail, String errorCode, HttpServletRequest req) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title != null ? title : status.getReasonPhrase());
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setProperty("timestamp", Instant.now().toString());
        problemDetail.setProperty("code", errorCode);

        if (req != null) {
            String uri = req.getRequestURI();
            if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                uri = uri + "?" + req.getQueryString();
            }
            problemDetail.setInstance(URI.create(uri));
        }

        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (traceId != null) {
            problemDetail.setProperty("traceId", traceId);
        }
        if (spanId != null) {
            problemDetail.setProperty("spanId", spanId);
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("x-error-code", errorCode)
                .body(problemDetail);
    }
}
