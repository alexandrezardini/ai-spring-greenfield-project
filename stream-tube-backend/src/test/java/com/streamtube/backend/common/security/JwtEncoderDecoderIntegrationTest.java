package com.streamtube.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.backend.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JwtEncoderDecoderIntegrationTest {

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtDecoder jwtDecoder;

  @Test
  void encodeThenDecode_subClaimRoundTrips() {
    UUID userId = UUID.randomUUID();
    String token = encodeToken(userId, Instant.now().plusSeconds(900));

    Jwt decoded = jwtDecoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    assertThat(decoded.getClaimAsString("iss")).isEqualTo("streamtube");
  }

  @Test
  void twoTokens_haveDistinctJti() {
    UUID userId = UUID.randomUUID();
    Instant exp = Instant.now().plusSeconds(900);
    String token1 = encodeToken(userId, exp);
    String token2 = encodeToken(userId, exp);

    Jwt decoded1 = jwtDecoder.decode(token1);
    Jwt decoded2 = jwtDecoder.decode(token2);

    assertThat(decoded1.getId()).isNotEqualTo(decoded2.getId());
  }

  @Test
  void expiredToken_throwsJwtException() {
    Instant issuedAt = Instant.now().minusSeconds(7200);
    Instant expiresAt = Instant.now().minusSeconds(3600);
    String expiredToken = encodeToken(UUID.randomUUID(), issuedAt, expiresAt);

    assertThatThrownBy(() -> jwtDecoder.decode(expiredToken)).isInstanceOf(JwtException.class);
  }

  private String encodeToken(UUID userId, Instant expiresAt) {
    return encodeToken(userId, Instant.now(), expiresAt);
  }

  private String encodeToken(UUID userId, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("streamtube")
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
