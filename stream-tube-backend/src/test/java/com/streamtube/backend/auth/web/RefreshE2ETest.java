package com.streamtube.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefreshE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private OpaqueTokenGenerator tokenGenerator;

  // Unique IP for this class so its rate-limit counter does not bleed into other E2E test classes
  // that share the same Spring context.
  private static final String TEST_IP = "10.201.6.1";

  private static final String EMAIL = "refreshe2e@example.com";
  private static final String PASSWORD = "secure123";

  @BeforeEach
  void setUp() {
    if (userRepository.findByEmail(EMAIL).isEmpty()) {
      User user = User.create(EMAIL, passwordEncoder.encode(PASSWORD), "RefreshE2E");
      user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
      userRepository.save(user);
    }
  }

  @Test
  void refresh_validToken_returns200WithNewDistinctPair() throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", TEST_IP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

    String oldRefreshToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

    String newRefreshToken =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

    String newAccessToken =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");
    var jwt = jwtDecoder.decode(newAccessToken);
    assertThat(jwt.getSubject()).isNotBlank();
  }

  @Test
  void refresh_sameTokenTwiceRapidly_returnsIdenticalGraceCachedResponse() throws Exception {
    User user = userRepository.findByEmail(EMAIL).orElseThrow();
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    RefreshToken rt =
        RefreshToken.issue(
            AggregateReference.to(user.getId()),
            UUID.randomUUID(),
            tokens.sha256HexHash(),
            Instant.now().plus(30, ChronoUnit.DAYS));
    refreshTokenRepository.save(rt);

    String body = refreshBody(tokens.rawHex());

    MvcResult first =
        mockMvc
            .perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult second =
        mockMvc
            .perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

    String firstAccess = JsonPath.read(first.getResponse().getContentAsString(), "$.accessToken");
    String secondAccess = JsonPath.read(second.getResponse().getContentAsString(), "$.accessToken");
    String firstRefresh = JsonPath.read(first.getResponse().getContentAsString(), "$.refreshToken");
    String secondRefresh =
        JsonPath.read(second.getResponse().getContentAsString(), "$.refreshToken");

    assertThat(secondAccess).isEqualTo(firstAccess);
    assertThat(secondRefresh).isEqualTo(firstRefresh);
  }

  @Test
  void refresh_rotatedTokenAfterGrace_returns401TokenReuseDetectedAndRevokesFamily()
      throws Exception {
    User user = userRepository.findByEmail(EMAIL).orElseThrow();
    UUID familyId = UUID.randomUUID();

    OpaqueTokenGenerator.Tokens siblingTokens = tokenGenerator.generate();
    RefreshToken siblingToken =
        RefreshToken.issue(
            AggregateReference.to(user.getId()),
            familyId,
            siblingTokens.sha256HexHash(),
            Instant.now().plus(30, ChronoUnit.DAYS));
    RefreshToken savedSibling = refreshTokenRepository.save(siblingToken);

    OpaqueTokenGenerator.Tokens oldTokens = tokenGenerator.generate();
    RefreshToken oldToken =
        RefreshToken.issue(
            AggregateReference.to(user.getId()),
            familyId,
            oldTokens.sha256HexHash(),
            Instant.now().plus(30, ChronoUnit.DAYS));
    oldToken.setRotatedAt(Instant.now().minus(31, ChronoUnit.SECONDS));
    oldToken.setRotatedToId(savedSibling.getId());
    refreshTokenRepository.save(oldToken);

    // Present the already-rotated token after grace — theft detected, family revoked
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(oldTokens.rawHex())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("TOKEN_REUSE_DETECTED"));

    // Sibling token is now revoked — should also return TOKEN_REUSE_DETECTED
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(siblingTokens.rawHex())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("TOKEN_REUSE_DETECTED"));
  }

  @Test
  void refresh_unknownToken_returns401InvalidRefreshToken() throws Exception {
    OpaqueTokenGenerator.Tokens unknown = tokenGenerator.generate();
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(unknown.rawHex())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
  }

  private String loginBody(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  private String refreshBody(String token) {
    return "{\"refreshToken\":\"" + token + "\"}";
  }
}
