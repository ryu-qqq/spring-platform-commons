package com.ryuqqq.platform.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 인증되지 않은 요청에 401 ProblemDetail({@code SERVICE_TOKEN_REQUIRED})을 응답한다. */
public class ServiceTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceTokenAuthenticationEntryPoint.class);
    private static final String CODE = "SERVICE_TOKEN_REQUIRED";

    private final ServiceTokenProblemDetailWriter writer;

    public ServiceTokenAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.writer = new ServiceTokenProblemDetailWriter(objectMapper);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        log.warn("Unauthorized: uri={}, reason={}", request.getRequestURI(), authException.getMessage());
        writer.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Service token is missing or invalid",
                CODE,
                request);
    }
}
