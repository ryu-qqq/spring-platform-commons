package com.ryuqqq.platform.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ServiceTokenErrorHandlerTest {

    // 런타임에 주입되는 Spring 구성 ObjectMapper 와 동일하게 — ProblemDetail mixin 으로 커스텀
    // property(code·timestamp)를 top-level 로 평탄화한다 (플레인 ObjectMapper 는 properties 하위에 중첩).
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("entrypoint → 401 ProblemDetail, code=SERVICE_TOKEN_REQUIRED, x-error-code 헤더")
    void entryPointWrites401() throws Exception {
        ServiceTokenAuthenticationEntryPoint entryPoint =
                new ServiceTokenAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new BadCredentialsException("nope"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).startsWith("application/problem+json");
        assertThat(res.getHeader("x-error-code")).isEqualTo("SERVICE_TOKEN_REQUIRED");
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SERVICE_TOKEN_REQUIRED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("access denied handler → 403 ProblemDetail, code=ACCESS_DENIED")
    void accessDeniedWrites403() throws Exception {
        ServiceTokenAccessDeniedHandler handler =
                new ServiceTokenAccessDeniedHandler(objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.handle(req, res, new AccessDeniedException("denied"));

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getHeader("x-error-code")).isEqualTo("ACCESS_DENIED");
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("status").asInt()).isEqualTo(403);
    }
}
