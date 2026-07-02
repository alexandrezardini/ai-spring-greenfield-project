package com.streamtube.backend.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamtube.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  TestcontainersConfiguration.class,
  RateLimitInterceptorIntegrationTest.TestRateLimitedController.class,
  RateLimitInterceptorIntegrationTest.TestSecurityConfig.class
})
class RateLimitInterceptorIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @BeforeAll
  static void registerPaths(@Autowired RateLimitedPaths rateLimitedPaths) {
    rateLimitedPaths.addPattern("/auth/test/rate-limited/**");
  }

  @Test
  void tenRequestsFromSameIp_succeed_eleventhReturns429() throws Exception {
    String ip = "10.0.1.1";
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(get("/auth/test/rate-limited/ping").header("X-Forwarded-For", ip))
          .andExpect(status().isOk());
    }
    mockMvc
        .perform(get("/auth/test/rate-limited/ping").header("X-Forwarded-For", ip))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.statusCode").value(429));
  }

  @Test
  void differentIp_getsOwnQuotaAfterFirstIpIsExhausted() throws Exception {
    String exhaustedIp = "10.0.1.2";
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(get("/auth/test/rate-limited/ping").header("X-Forwarded-For", exhaustedIp))
          .andExpect(status().isOk());
    }
    mockMvc
        .perform(get("/auth/test/rate-limited/ping").header("X-Forwarded-For", exhaustedIp))
        .andExpect(status().isTooManyRequests());

    String freshIp = "10.0.1.3";
    mockMvc
        .perform(get("/auth/test/rate-limited/ping").header("X-Forwarded-For", freshIp))
        .andExpect(status().isOk());
  }

  @Test
  void xForwardedFor_keysOnFirstHopIp() throws Exception {
    String firstHop = "10.0.1.4";
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              get("/auth/test/rate-limited/ping").header("X-Forwarded-For", firstHop + ", 5.6.7.8"))
          .andExpect(status().isOk());
    }
    mockMvc
        .perform(
            get("/auth/test/rate-limited/ping").header("X-Forwarded-For", firstHop + ", 9.9.9.9"))
        .andExpect(status().isTooManyRequests());

    mockMvc
        .perform(
            get("/auth/test/rate-limited/ping").header("X-Forwarded-For", "10.0.1.5, " + firstHop))
        .andExpect(status().isOk());
  }

  @Test
  void nonRateLimitedPath_isNeverBlocked() throws Exception {
    String ip = "10.0.1.6";
    for (int i = 0; i < 15; i++) {
      mockMvc
          .perform(get("/actuator/health").header("X-Forwarded-For", ip))
          .andExpect(status().isOk());
    }
  }

  @RestController
  static class TestRateLimitedController {

    @GetMapping("/auth/test/rate-limited/ping")
    String ping() {
      return "ok";
    }
  }

  // Permits /auth/test/** anonymously in this test's Spring context only.
  // Needed because SI-02.11 changed SecurityFilterChain from wildcard /auth/** to explicit paths.
  @TestConfiguration
  static class TestSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain testRateLimitPermitChain(HttpSecurity http) throws Exception {
      return http.securityMatcher("/auth/test/**")
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .csrf(AbstractHttpConfigurer::disable)
          .build();
    }
  }
}
