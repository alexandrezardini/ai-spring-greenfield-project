package com.streamtube.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LoginE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtDecoder jwtDecoder;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.3.1";

  private static final String EMAIL = "logintest@example.com";
  private static final String PASSWORD = "secure123";

  @BeforeEach
  void setUp() {
    if (userRepository.findByEmail(EMAIL).isEmpty()) {
      User user = User.create(EMAIL, passwordEncoder.encode(PASSWORD), "LoginTest");
      user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
      userRepository.save(user);
    }
  }

  @Test
  void login_validCredentials_returns200WithTokenPair() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", TEST_IP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

    String accessToken =
        com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.accessToken");
    var jwt = jwtDecoder.decode(accessToken);
    assertThat(jwt.getSubject()).isNotBlank();
  }

  @Test
  void login_wrongPassword_returns401() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(EMAIL, "wrongpassword")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
  }

  @Test
  void login_unknownEmail_returns401() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody@example.com", PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
  }

  @Test
  void login_unconfirmedUser_returns403() throws Exception {
    User unconf =
        User.create("unconfirmed_login@example.com", passwordEncoder.encode(PASSWORD), "Unconf");
    userRepository.save(unconf);

    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("unconfirmed_login@example.com", PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("EMAIL_NOT_CONFIRMED"));
  }

  @Test
  void login_eleventhRequestFromSameIp_returns429() throws Exception {
    String ip = "10.9.2.1";
    for (int i = 0; i < 10; i++) {
      mockMvc.perform(
          post("/auth/login")
              .header("X-Forwarded-For", ip)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body("rate" + i + "@example.com", "irrelevant")));
    }
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("rate10@example.com", "irrelevant")))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
  }

  private String body(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }
}
