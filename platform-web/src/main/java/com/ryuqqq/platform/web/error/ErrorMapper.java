package com.ryuqqq.platform.web.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.ryuqqq.platform.common.exception.DomainException;

/**
 * 도메인 예외 → RFC 7807 {@link org.springframework.http.ProblemDetail} 매핑.
 *
 * <p>per-domain {@code @Component} 구현. default는 {@link ErrorMapperRegistry#defaultMapping}.
 */
public interface ErrorMapper {

    /** 이 mapper가 처리할 예외인지. */
    boolean supports(DomainException ex);

    /** HTTP status·title·detail·type URI 변환. */
    MappedError map(DomainException ex, Locale locale);

    /**
     * ProblemDetail 본문에 쓰일 HTTP 매핑 결과.
     *
     * @param status HTTP status
     * @param title 사람이 읽을 수 있는 요약
     * @param detail 상세 메시지 (ProblemDetail.detail)
     * @param type RFC 7807 type URI
     */
    record MappedError(HttpStatus status, String title, String detail, URI type) {}
}
