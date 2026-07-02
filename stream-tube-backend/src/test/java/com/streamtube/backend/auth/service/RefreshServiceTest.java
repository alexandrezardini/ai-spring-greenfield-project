package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.exception.InvalidRefreshTokenException;
import com.streamtube.backend.auth.exception.TokenReuseDetectedException;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import com.streamtube.backend.auth.web.TokenPairResponse;
import com.streamtube.backend.users.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.mapping.AggregateReference;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

  @Mock private OpaqueTokenGenerator tokenGenerator;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtAccessTokenService jwtAccessTokenService;
  @Mock private RefreshTokenService refreshTokenService;

  private RefreshService refreshService;

  private static final String RAW_TOKEN =
      "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd";
  private static final String HASH =
      "hashvalue00000000000000000000000000000000000000000000000000000000";

  @BeforeEach
  void setUp() {
    when(tokenGenerator.hash(RAW_TOKEN)).thenReturn(HASH);
    refreshService =
        new RefreshService(
            tokenGenerator, refreshTokenRepository, jwtAccessTokenService, refreshTokenService);
  }

  @Test
  void refresh_validToken_returnsNewPairAndRotatesOldToken() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = validToken(userId, familyId);

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));
    when(jwtAccessTokenService.issue(userId)).thenReturn("new.jwt.token");
    IssuedRefreshToken issued =
        new IssuedRefreshToken(
            UUID.randomUUID(), "newrawhex", Instant.now().plus(30, ChronoUnit.DAYS), familyId);
    when(refreshTokenService.issue(userId, familyId)).thenReturn(issued);

    TokenPairResponse response = refreshService.refresh(RAW_TOKEN);

    assertThat(response.accessToken()).isEqualTo("new.jwt.token");
    assertThat(response.refreshToken()).isEqualTo("newrawhex");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(900L);
    assertThat(token.getRotatedAt()).isNotNull();
    assertThat(token.getRotatedToId()).isEqualTo(issued.tokenId());
    verify(refreshTokenRepository).save(token);
    verify(refreshTokenService).issue(userId, familyId);
  }

  @Test
  void refresh_unknownToken_throwsInvalidRefreshToken() {
    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshService.refresh(RAW_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenService, never()).issue(any(), any());
  }

  @Test
  void refresh_expiredToken_throwsInvalidRefreshToken() {
    UUID userId = UUID.randomUUID();
    RefreshToken token = validToken(userId, UUID.randomUUID());
    token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> refreshService.refresh(RAW_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenService, never()).issue(any(), any());
  }

  @Test
  void refresh_revokedToken_throwsTokenReuseDetectedAndRevokesFamily() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = validToken(userId, familyId);
    token.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> refreshService.refresh(RAW_TOKEN))
        .isInstanceOf(TokenReuseDetectedException.class);

    verify(refreshTokenRepository).revokeFamily(eq(familyId), eq("TOKEN_REUSE"), any());
    verify(refreshTokenService, never()).issue(any(), any());
  }

  @Test
  void refresh_rotatedWithinGrace_cacheHit_returnsCachedPair() {
    UUID userId = UUID.randomUUID();
    RefreshToken token = validToken(userId, UUID.randomUUID());
    token.setRotatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    TokenPairResponse cached = new TokenPairResponse("cached.jwt", "cachedrefresh", "Bearer", 900L);
    refreshService.graceCache.put(HASH, cached);

    TokenPairResponse response = refreshService.refresh(RAW_TOKEN);

    assertThat(response).isEqualTo(cached);
    verify(refreshTokenService, never()).issue(any(), any());
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void refresh_rotatedWithinGrace_cacheMiss_throwsInvalidRefreshToken() {
    UUID userId = UUID.randomUUID();
    RefreshToken token = validToken(userId, UUID.randomUUID());
    token.setRotatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));
    // cache is empty — no put

    assertThatThrownBy(() -> refreshService.refresh(RAW_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void refresh_rotatedAfterGrace_throwsTokenReuseDetectedAndRevokesFamily() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = validToken(userId, familyId);
    token.setRotatedAt(Instant.now().minus(31, ChronoUnit.SECONDS));

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> refreshService.refresh(RAW_TOKEN))
        .isInstanceOf(TokenReuseDetectedException.class);

    verify(refreshTokenRepository).revokeFamily(eq(familyId), eq("TOKEN_REUSE"), any());
    verify(refreshTokenService, never()).issue(any(), any());
  }

  private RefreshToken validToken(UUID userId, UUID familyId) {
    RefreshToken token = new RefreshToken();
    token.setId(UUID.randomUUID());
    token.setUserId(AggregateReference.<User, UUID>to(userId));
    token.setFamilyId(familyId);
    token.setTokenHash(HASH);
    token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    return token;
  }
}
