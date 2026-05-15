package com.streamtube.backend.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamtube.backend.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  TestcontainersConfiguration.class,
  SecurityFilterChainIntegrationTest.SecuredController.class
})
class SecurityFilterChainIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;

  @Test
  void actuatorHealth_isAccessibleWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void authEndpoint_isAccessibleWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/auth/stub")).andExpect(status().isOk());
  }

  @Test
  void protectedEndpoint_returns401WithoutAuthentication() throws Exception {
    mockMvc.perform(get("/secured/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_returns200WithValidBearerToken() throws Exception {
    String token = issueToken(UUID.randomUUID());
    mockMvc
        .perform(get("/secured/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  private String issueToken(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("streamtube")
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @RestController
  static class SecuredController {
    @GetMapping("/secured/ping")
    String ping() {
      return "pong";
    }

    @GetMapping("/auth/stub")
    String authStub() {
      return "ok";
    }
  }
}
