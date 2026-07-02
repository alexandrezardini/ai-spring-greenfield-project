package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  static final long REFRESH_TOKEN_TTL_DAYS = 30L;

  private final OpaqueTokenGenerator tokenGenerator;
  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenService(
      OpaqueTokenGenerator tokenGenerator, RefreshTokenRepository refreshTokenRepository) {
    this.tokenGenerator = tokenGenerator;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public IssuedRefreshToken issue(UUID userId, UUID familyId) {
    UUID effectiveFamilyId = familyId != null ? familyId : UUID.randomUUID();
    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
    RefreshToken token =
        RefreshToken.issue(
            AggregateReference.to(userId), effectiveFamilyId, tokens.sha256HexHash(), expiresAt);
    RefreshToken saved = refreshTokenRepository.save(token);
    return new IssuedRefreshToken(saved.getId(), tokens.rawHex(), expiresAt, effectiveFamilyId);
  }
}
