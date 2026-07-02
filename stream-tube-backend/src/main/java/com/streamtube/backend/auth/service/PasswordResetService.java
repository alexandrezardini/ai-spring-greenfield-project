package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.exception.InvalidResetTokenException;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import com.streamtube.backend.auth.web.ResetPasswordRequest;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final EmailTokenRepository emailTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public PasswordResetService(
      OpaqueTokenGenerator opaqueTokenGenerator,
      EmailTokenRepository emailTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.opaqueTokenGenerator = opaqueTokenGenerator;
    this.emailTokenRepository = emailTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void reset(ResetPasswordRequest request) {
    String hash = opaqueTokenGenerator.hash(request.token());

    EmailToken token =
        emailTokenRepository
            .findByTokenHash(hash)
            .filter(t -> t.getType() == TokenType.RESET_PASSWORD)
            .orElseThrow(InvalidResetTokenException::new);

    if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidResetTokenException();
    }

    User user =
        userRepository
            .findById(token.getUserId().getId())
            .orElseThrow(InvalidResetTokenException::new);

    Instant now = Instant.now();
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(now);
    userRepository.save(user);

    token.setUsedAt(now);
    emailTokenRepository.save(token);

    refreshTokenRepository.revokeAllForUser(user.getId(), "PASSWORD_RESET", now);
  }
}
