package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.exception.InvalidRefreshTokenException;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
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
class LogoutServiceTest {

  @Mock private OpaqueTokenGenerator tokenGenerator;
  @Mock private RefreshTokenRepository refreshTokenRepository;

  private LogoutService logoutService;

  private static final String RAW_TOKEN =
      "aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd";
  private static final String HASH =
      "hashvalue00000000000000000000000000000000000000000000000000000000";

  @BeforeEach
  void setUp() {
    when(tokenGenerator.hash(RAW_TOKEN)).thenReturn(HASH);
    logoutService = new LogoutService(tokenGenerator, refreshTokenRepository);
  }

  @Test
  void logout_validTokenOwnedByUser_revokesFamily() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = activeToken(userId, familyId);

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    logoutService.logout(userId, RAW_TOKEN);

    verify(refreshTokenRepository).revokeFamily(eq(familyId), eq("USER_LOGOUT"), any());
  }

  @Test
  void logout_unknownToken_throwsInvalidRefreshToken() {
    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> logoutService.logout(UUID.randomUUID(), RAW_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void logout_tokenOwnedByDifferentUser_throwsInvalidRefreshToken() {
    UUID ownerId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    RefreshToken token = activeToken(ownerId, UUID.randomUUID());

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> logoutService.logout(callerId, RAW_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void logout_alreadyRevokedToken_noopSuccess() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = activeToken(userId, familyId);
    token.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));
    token.setRevokedReason("USER_LOGOUT");

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    logoutService.logout(userId, RAW_TOKEN);

    verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
  }

  @Test
  void logout_calledTwice_idempotent() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshToken token = activeToken(userId, familyId);
    token.setRevokedAt(Instant.now());
    token.setRevokedReason("USER_LOGOUT");

    when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    logoutService.logout(userId, RAW_TOKEN);
    logoutService.logout(userId, RAW_TOKEN);

    verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
  }

  private RefreshToken activeToken(UUID userId, UUID familyId) {
    RefreshToken token = new RefreshToken();
    token.setId(UUID.randomUUID());
    token.setUserId(AggregateReference.<User, UUID>to(userId));
    token.setFamilyId(familyId);
    token.setTokenHash(HASH);
    token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    return token;
  }
}
