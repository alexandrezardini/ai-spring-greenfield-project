package com.streamtube.backend.auth.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtAccessTokenService {

  static final long ACCESS_TOKEN_TTL_SECONDS = 900L;

  private final JwtEncoder jwtEncoder;

  public JwtAccessTokenService(JwtEncoder jwtEncoder) {
    this.jwtEncoder = jwtEncoder;
  }

  public String issue(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("streamtube")
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS))
            .id(UUID.randomUUID().toString())
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }
}
