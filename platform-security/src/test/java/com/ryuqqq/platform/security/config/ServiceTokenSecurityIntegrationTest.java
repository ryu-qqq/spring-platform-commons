package com.ryuqqq.platform.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ryuqqq.platform.security.error.ServiceTokenAccessDeniedHandler;
import com.ryuqqq.platform.security.error.ServiceTokenAuthenticationEntryPoint;
import com.ryuqqq.platform.security.filter.ServiceTokenAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ServiceTokenSecurityIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.service-token.secret=s3cr3t")
class ServiceTokenSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없음 → 401 ProblemDetail")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("x-error-code", "SERVICE_TOKEN_REQUIRED"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_TOKEN_REQUIRED"));
    }

    @Test
    @DisplayName("유효 토큰 → 200")
    void validTokenReturns200() throws Exception {
        mockMvc.perform(get("/ping").header("X-Service-Token", "s3cr3t"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    @DisplayName("잘못된 토큰 → 401")
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/ping").header("X-Service-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                ServiceTokenAuthenticationFilter filter,
                ServiceTokenAuthenticationEntryPoint entryPoint,
                ServiceTokenAccessDeniedHandler accessDeniedHandler)
                throws Exception {
            ServiceTokenSecurity.applyDefaults(http, filter, entryPoint, accessDeniedHandler);
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }

        @RestController
        static class PingController {
            @GetMapping("/ping")
            String ping() {
                return "pong";
            }
        }
    }
}
