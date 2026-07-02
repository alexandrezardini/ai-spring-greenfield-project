package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.exception.InvalidRefreshTokenException;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

  private final OpaqueTokenGenerator tokenGenerator;
  private final RefreshTokenRepository refreshTokenRepository;

  public LogoutService(
      OpaqueTokenGenerator tokenGenerator, RefreshTokenRepository refreshTokenRepository) {
    this.tokenGenerator = tokenGenerator;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public void logout(UUID authenticatedUserId, String rawRefreshToken) {
    String hash = tokenGenerator.hash(rawRefreshToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

    if (!token.getUserId().getId().equals(authenticatedUserId)) {
      throw new InvalidRefreshTokenException();
    }

    if (token.getRevokedAt() != null) {
      return;
    }

    refreshTokenRepository.revokeFamily(token.getFamilyId(), "USER_LOGOUT", Instant.now());
  }
}
