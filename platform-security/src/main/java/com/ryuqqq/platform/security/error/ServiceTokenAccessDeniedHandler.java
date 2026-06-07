package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 권한이 없는 요청에 403 ProblemDetail({@code ACCESS_DENIED})을 응답한다. */
public class ServiceTokenAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceTokenAccessDeniedHandler.class);
    private static final String CODE = "ACCESS_DENIED";

    private final ServiceTokenProblemDetailWriter writer;

    public ServiceTokenAccessDeniedHandler(ObjectMapper objectMapper) {
        this.writer = new ServiceTokenProblemDetailWriter(objectMapper);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("AccessDenied: uri={}, reason={}", request.getRequestURI(), accessDeniedException.getMessage());
        writer.write(response, HttpStatus.FORBIDDEN, "Forbidden", "접근 권한이 없습니다", CODE, request);
    }
}
