package com.streamtube.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import com.streamtube.backend.auth.service.OpaqueTokenGenerator;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LogoutE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private OpaqueTokenGenerator tokenGenerator;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.4.1";

  private static final String EMAIL = "logoute2e@example.com";
  private static final String PASSWORD = "secure123";

  @BeforeEach
  void setUp() {
    if (userRepository.findByEmail(EMAIL).isEmpty()) {
      User user = User.create(EMAIL, passwordEncoder.encode(PASSWORD), "LogoutE2E");
      user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
      userRepository.save(user);
    }
  }

  @Test
  void logout_withoutAuthHeader_returns401() throws Exception {
    mockMvc
        .perform(
            post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutBody("sometoken")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_validTokenAndCredentials_returns204AndRevokesFamily() throws Exception {
    String[] tokens = loginAndGetTokens(EMAIL, PASSWORD);
    String accessToken = tokens[0];
    String refreshToken = tokens[1];

    mockMvc
        .perform(
            post("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutBody(refreshToken)))
        .andExpect(status().isNoContent());

    // Subsequent refresh with the revoked token should return TOKEN_REUSE_DETECTED
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("TOKEN_REUSE_DETECTED"));
  }

  @Test
  void logout_refreshTokenBelongsToDifferentUser_returns401InvalidRefreshToken() throws Exception {
    String[] tokens = loginAndGetTokens(EMAIL, PASSWORD);
    String user1AccessToken = tokens[0];

    // Seed a refresh token for a different user
    User user2 = userRepository.save(User.create("logoutother@example.com", "hash2", "Other"));
    OpaqueTokenGenerator.Tokens otherTokens = tokenGenerator.generate();
    refreshTokenRepository.save(
        RefreshToken.issue(
            AggregateReference.to(user2.getId()),
            UUID.randomUUID(),
            otherTokens.sha256HexHash(),
            Instant.now().plus(30, ChronoUnit.DAYS)));

    mockMvc
        .perform(
            post("/auth/logout")
                .header("Authorization", "Bearer " + user1AccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutBody(otherTokens.rawHex())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
  }

  private String[] loginAndGetTokens(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", TEST_IP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    return new String[] {
      JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken")
    };
  }

  private String logoutBody(String token) {
    return "{\"refreshToken\":\"" + token + "\"}";
  }
}
