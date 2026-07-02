package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@ExtendWith(MockitoExtension.class)
class JwtAccessTokenServiceTest {

  @Mock private JwtEncoder jwtEncoder;

  @InjectMocks private JwtAccessTokenService jwtAccessTokenService;

  @Test
  void issue_producesJwtWithCorrectClaims() {
    UUID userId = UUID.randomUUID();
    Jwt mockJwt = mock(Jwt.class);
    when(mockJwt.getTokenValue()).thenReturn("mock.jwt.token");
    when(jwtEncoder.encode(any())).thenReturn(mockJwt);

    Instant before = Instant.now();
    String tokenValue = jwtAccessTokenService.issue(userId);
    Instant after = Instant.now().plusSeconds(1);

    assertThat(tokenValue).isEqualTo("mock.jwt.token");

    ArgumentCaptor<JwtEncoderParameters> captor =
        ArgumentCaptor.forClass(JwtEncoderParameters.class);
    verify(jwtEncoder).encode(captor.capture());
    JwtClaimsSet claims = captor.getValue().getClaims();

    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getClaims().get(JwtClaimNames.ISS)).isEqualTo("streamtube");
    assertThat(claims.getIssuedAt()).isBetween(before, after);
    assertThat(Duration.between(claims.getIssuedAt(), claims.getExpiresAt()).getSeconds())
        .isEqualTo(JwtAccessTokenService.ACCESS_TOKEN_TTL_SECONDS);
    assertThat(claims.getId()).isNotBlank();
  }

  @Test
  void issue_consecutiveTokens_haveDistinctJti() {
    UUID userId = UUID.randomUUID();
    Jwt mockJwt = mock(Jwt.class);
    when(mockJwt.getTokenValue()).thenReturn("mock.jwt.token");
    when(jwtEncoder.encode(any())).thenReturn(mockJwt);

    jwtAccessTokenService.issue(userId);
    jwtAccessTokenService.issue(userId);

    ArgumentCaptor<JwtEncoderParameters> captor =
        ArgumentCaptor.forClass(JwtEncoderParameters.class);
    verify(jwtEncoder, times(2)).encode(captor.capture());
    List<JwtEncoderParameters> allParams = captor.getAllValues();

    String jti1 = allParams.get(0).getClaims().getId();
    String jti2 = allParams.get(1).getClaims().getId();
    assertThat(jti1).isNotEqualTo(jti2);
  }
}
