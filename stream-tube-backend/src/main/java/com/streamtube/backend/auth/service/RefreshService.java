package com.streamtube.backend.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.auth.exception.InvalidRefreshTokenException;
import com.streamtube.backend.auth.exception.TokenReuseDetectedException;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import com.streamtube.backend.auth.web.TokenPairResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshService {

  static final long GRACE_PERIOD_SECONDS = 30L;

  private final OpaqueTokenGenerator tokenGenerator;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtAccessTokenService jwtAccessTokenService;
  private final RefreshTokenService refreshTokenService;

  // package-private for tests in the same package
  final Cache<String, TokenPairResponse> graceCache =
      Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).build();

  public RefreshService(
      OpaqueTokenGenerator tokenGenerator,
      RefreshTokenRepository refreshTokenRepository,
      JwtAccessTokenService jwtAccessTokenService,
      RefreshTokenService refreshTokenService) {
    this.tokenGenerator = tokenGenerator;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtAccessTokenService = jwtAccessTokenService;
    this.refreshTokenService = refreshTokenService;
  }

  // noRollbackFor: revokeFamily updates must commit even when theft is detected and the exception
  // propagates
  @Transactional(noRollbackFor = TokenReuseDetectedException.class)
  public TokenPairResponse refresh(String rawToken) {
    String hash = tokenGenerator.hash(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

    if (token.getRevokedAt() != null) {
      refreshTokenRepository.revokeFamily(token.getFamilyId(), "TOKEN_REUSE", Instant.now());
      throw new TokenReuseDetectedException();
    }

    if (token.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidRefreshTokenException();
    }

    if (token.getRotatedAt() != null) {
      Instant graceDeadline = token.getRotatedAt().plusSeconds(GRACE_PERIOD_SECONDS);
      if (!Instant.now().isAfter(graceDeadline)) {
        TokenPairResponse cached = graceCache.getIfPresent(hash);
        if (cached != null) {
          return cached;
        }
        throw new InvalidRefreshTokenException();
      }
      refreshTokenRepository.revokeFamily(token.getFamilyId(), "TOKEN_REUSE", Instant.now());
      throw new TokenReuseDetectedException();
    }

    UUID userId = token.getUserId().getId();
    UUID familyId = token.getFamilyId();

    IssuedRefreshToken newToken = refreshTokenService.issue(userId, familyId);
    token.setRotatedAt(Instant.now());
    token.setRotatedToId(newToken.tokenId());
    refreshTokenRepository.save(token);

    String accessToken = jwtAccessTokenService.issue(userId);
    TokenPairResponse response =
        new TokenPairResponse(
            accessToken,
            newToken.rawHex(),
            "Bearer",
            JwtAccessTokenService.ACCESS_TOKEN_TTL_SECONDS);

    graceCache.put(hash, response);

    return response;
  }
}
