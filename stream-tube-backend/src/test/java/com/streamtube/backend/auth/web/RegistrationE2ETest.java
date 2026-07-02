package com.streamtube.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamtube.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RegistrationE2ETest {

  @Autowired private MockMvc mockMvc;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.1.1";

  @Test
  void register_validRequest_returns201WithBody() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e2e1@example.com\",\"password\":\"secure123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("e2e1@example.com"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.channel.handle").exists())
        .andExpect(header().exists("Location"));
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    String body = "{\"email\":\"dup2@example.com\",\"password\":\"secure123\"}";
    mockMvc.perform(
        post("/auth/register")
            .header("X-Forwarded-For", TEST_IP)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    mockMvc
        .perform(
            post("/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void register_invalidEmailFormat_returns400() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"secure123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void register_passwordTooShort_returns400() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"short@example.com\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void register_eleventhRequestFromSameIp_returns429() throws Exception {
    String ip = "10.7.1.1";
    for (int i = 0; i < 10; i++) {
      mockMvc.perform(
          post("/auth/register")
              .header("X-Forwarded-For", ip)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"rl" + i + "@example.com\",\"password\":\"secure123\"}"));
    }
    mockMvc
        .perform(
            post("/auth/register")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rl10@example.com\",\"password\":\"secure123\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
  }
}
