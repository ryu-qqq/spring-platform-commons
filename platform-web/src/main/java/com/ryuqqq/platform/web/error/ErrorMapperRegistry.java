package com.ryuqqq.platform.web.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ryuqqq.platform.common.exception.DomainException;

/**
 * {@link ErrorMapper} 구현체 레지스트리. 첫 매칭 mapper 사용, 없으면 {@link #defaultMapping}.
 */
@Component
public class ErrorMapperRegistry {

    private final List<ErrorMapper> mappers;

    public ErrorMapperRegistry(List<ErrorMapper> mappers) {
        this.mappers = mappers;
    }

    public Optional<ErrorMapper.MappedError> map(DomainException ex, Locale locale) {
        return mappers.stream()
                .filter(mapper -> mapper.supports(ex))
                .findFirst()
                .map(mapper -> mapper.map(ex, locale));
    }

    /** ErrorCode httpStatus 기반 default 매핑. */
    public ErrorMapper.MappedError defaultMapping(DomainException ex) {
        HttpStatus status = HttpStatus.resolve(ex.errorCode().getHttpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail =
                ex.getMessage() != null ? ex.getMessage() : ex.errorCode().getMessage();
        return new ErrorMapper.MappedError(
                status, status.getReasonPhrase(), detail, URI.create("about:blank"));
    }
}
