package com.streamtube.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EmailConfirmationE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailTokenRepository emailTokenRepository;
  @Autowired private OpaqueTokenGenerator tokenGenerator;

  @Test
  void confirmEmail_validToken_returns204AndSetsEmailConfirmedAt() throws Exception {
    User user = userRepository.save(User.create("ce1@example.com", "hash", "Ce1"));
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens.sha256HexHash(),
            Instant.now().plus(24, ChronoUnit.HOURS)));

    mockMvc
        .perform(
            post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + tokens.rawHex() + "\"}"))
        .andExpect(status().isNoContent());

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updated.getEmailConfirmedAt()).isNotNull();
  }

  @Test
  void confirmEmail_replayedToken_returns404() throws Exception {
    User user = userRepository.save(User.create("ce2@example.com", "hash", "Ce2"));
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens.sha256HexHash(),
            Instant.now().plus(24, ChronoUnit.HOURS)));

    String body = "{\"token\":\"" + tokens.rawHex() + "\"}";
    mockMvc.perform(
        post("/auth/confirm-email").contentType(MediaType.APPLICATION_JSON).content(body));

    mockMvc
        .perform(post("/auth/confirm-email").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("INVALID_CONFIRMATION_TOKEN"));
  }

  @Test
  void confirmEmail_expiredToken_returns404() throws Exception {
    User user = userRepository.save(User.create("ce3@example.com", "hash", "Ce3"));
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens.sha256HexHash(),
            Instant.now().minus(1, ChronoUnit.HOURS)));

    mockMvc
        .perform(
            post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + tokens.rawHex() + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("INVALID_CONFIRMATION_TOKEN"));
  }

  @Test
  void confirmEmail_alreadyConfirmedUser_returns409() throws Exception {
    User user = userRepository.save(User.create("ce4@example.com", "hash", "Ce4"));

    OpaqueTokenGenerator.Tokens tokens1 = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens1.sha256HexHash(),
            Instant.now().plus(24, ChronoUnit.HOURS)));

    mockMvc.perform(
        post("/auth/confirm-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + tokens1.rawHex() + "\"}"));

    OpaqueTokenGenerator.Tokens tokens2 = tokenGenerator.generate();
    emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens2.sha256HexHash(),
            Instant.now().plus(24, ChronoUnit.HOURS)));

    mockMvc
        .perform(
            post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + tokens2.rawHex() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_CONFIRMED"));
  }
}
