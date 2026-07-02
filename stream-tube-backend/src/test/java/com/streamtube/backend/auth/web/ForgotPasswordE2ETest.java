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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ForgotPasswordE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.2.1";

  @Test
  void forgotPassword_unknownEmail_returns204() throws Exception {
    mockMvc
        .perform(
            post("/auth/forgot-password")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody_fp@example.com\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void forgotPassword_unconfirmedUser_returns204WithoutCreatingToken() throws Exception {
    User user = userRepository.save(User.create("fp_unconf@example.com", "hash", "FpUnconf"));

    mockMvc
        .perform(
            post("/auth/forgot-password")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fp_unconf@example.com\"}"))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_tokens WHERE user_id = :userId",
            Map.of("userId", user.getId()),
            Integer.class);
    assertThat(count).isZero();
  }

  @Test
  void forgotPassword_confirmedUser_returns204AndCreatesResetToken() throws Exception {
    User user = User.create("fp_conf@example.com", "hash", "FpConf");
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    user = userRepository.save(user);

    mockMvc
        .perform(
            post("/auth/forgot-password")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fp_conf@example.com\"}"))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_tokens"
                + " WHERE user_id = :userId AND type = 'RESET_PASSWORD' AND used_at IS NULL",
            Map.of("userId", user.getId()),
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void forgotPassword_secondRequestForSameUser_supersededPreviousToken() throws Exception {
    User user = User.create("fp_second@example.com", "hash", "FpSecond");
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    user = userRepository.save(user);
    String body = "{\"email\":\"fp_second@example.com\"}";

    mockMvc.perform(
        post("/auth/forgot-password")
            .header("X-Forwarded-For", TEST_IP)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    mockMvc.perform(
        post("/auth/forgot-password")
            .header("X-Forwarded-For", TEST_IP)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));

    Integer usedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_tokens"
                + " WHERE user_id = :userId AND type = 'RESET_PASSWORD' AND used_at IS NOT NULL",
            Map.of("userId", user.getId()),
            Integer.class);
    Integer activeCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_tokens"
                + " WHERE user_id = :userId AND type = 'RESET_PASSWORD' AND used_at IS NULL",
            Map.of("userId", user.getId()),
            Integer.class);
    assertThat(usedCount).isEqualTo(1);
    assertThat(activeCount).isEqualTo(1);
  }

  @Test
  void forgotPassword_eleventhRequestFromSameIp_returns429() throws Exception {
    String ip = "10.11.1.1";
    for (int i = 0; i < 10; i++) {
      mockMvc.perform(
          post("/auth/forgot-password")
              .header("X-Forwarded-For", ip)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"fprl" + i + "@example.com\"}"));
    }
    mockMvc
        .perform(
            post("/auth/forgot-password")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fprl10@example.com\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
  }
}
