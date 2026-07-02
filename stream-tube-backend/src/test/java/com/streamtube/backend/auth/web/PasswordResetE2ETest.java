package com.streamtube.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.auth.service.OpaqueTokenGenerator;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PasswordResetE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailTokenRepository emailTokenRepository;
  @Autowired private OpaqueTokenGenerator tokenGenerator;
  @Autowired private PasswordEncoder passwordEncoder;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.5.1";

  @Test
  void resetPassword_validToken_returns204AndLoginWithNewPasswordSucceeds() throws Exception {
    String oldPassword = "oldpassword1";
    String newPassword = "newpassword1";
    User user =
        userRepository.save(
            confirmedUser("rp1@example.com", passwordEncoder.encode(oldPassword), "Rp1"));

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(resetToken(user.getId(), tokens.sha256HexHash(), future1h()));

    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody(tokens.rawHex(), newPassword)))
        .andExpect(status().isNoContent());

    // Login with new password succeeds
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("rp1@example.com", newPassword)))
        .andExpect(status().isOk());

    // Login with old password fails
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("rp1@example.com", oldPassword)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
  }

  @Test
  void resetPassword_replayedToken_returns404() throws Exception {
    User user =
        userRepository.save(
            confirmedUser("rp2@example.com", passwordEncoder.encode("pass1234"), "Rp2"));

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(resetToken(user.getId(), tokens.sha256HexHash(), future1h()));

    String body = resetBody(tokens.rawHex(), "newpassword2");
    mockMvc.perform(
        post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body));

    mockMvc
        .perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
  }

  @Test
  void resetPassword_expiredToken_returns404() throws Exception {
    User user =
        userRepository.save(
            confirmedUser("rp3@example.com", passwordEncoder.encode("pass1234"), "Rp3"));

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    Instant expired = Instant.now().minus(2, ChronoUnit.HOURS);
    emailTokenRepository.save(resetToken(user.getId(), tokens.sha256HexHash(), expired));

    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody(tokens.rawHex(), "newpassword3")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
  }

  @Test
  void resetPassword_wrongTypeToken_returns404() throws Exception {
    User user =
        userRepository.save(
            confirmedUser("rp4@example.com", passwordEncoder.encode("pass1234"), "Rp4"));

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens.sha256HexHash(),
            future1h()));

    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody(tokens.rawHex(), "newpassword4")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("INVALID_RESET_TOKEN"));
  }

  @Test
  void resetPassword_afterReset_previousRefreshTokensAreRevoked() throws Exception {
    String oldPassword = "oldpassword5";
    String newPassword = "newpassword5";
    User user =
        userRepository.save(
            confirmedUser("rp5@example.com", passwordEncoder.encode(oldPassword), "Rp5"));

    // Login to obtain a refresh token before the reset
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", TEST_IP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody("rp5@example.com", oldPassword)))
            .andExpect(status().isOk())
            .andReturn();
    String oldRefreshToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

    // Reset the password
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(resetToken(user.getId(), tokens.sha256HexHash(), future1h()));
    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody(tokens.rawHex(), newPassword)))
        .andExpect(status().isNoContent());

    // Old refresh token must be revoked
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("TOKEN_REUSE_DETECTED"));
  }

  @Test
  void resetPassword_passwordTooShort_returns400() throws Exception {
    mockMvc
        .perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("sometoken", "short")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  private User confirmedUser(String email, String passwordHash, String name) {
    User user = User.create(email, passwordHash, name);
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    return user;
  }

  private EmailToken resetToken(java.util.UUID userId, String hash, Instant expiresAt) {
    return EmailToken.issue(
        AggregateReference.to(userId), TokenType.RESET_PASSWORD, hash, expiresAt);
  }

  private Instant future1h() {
    return Instant.now().plus(1, ChronoUnit.HOURS);
  }

  private String resetBody(String token, String newPassword) {
    return "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}";
  }

  private String loginBody(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }
}
