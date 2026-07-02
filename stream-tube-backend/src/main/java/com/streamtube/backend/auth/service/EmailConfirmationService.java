package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.exception.EmailAlreadyConfirmedException;
import com.streamtube.backend.auth.exception.InvalidConfirmationTokenException;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailConfirmationService {

  private final OpaqueTokenGenerator opaqueTokenGenerator;
  private final EmailTokenRepository emailTokenRepository;
  private final UserRepository userRepository;

  public EmailConfirmationService(
      OpaqueTokenGenerator opaqueTokenGenerator,
      EmailTokenRepository emailTokenRepository,
      UserRepository userRepository) {
    this.opaqueTokenGenerator = opaqueTokenGenerator;
    this.emailTokenRepository = emailTokenRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public void confirm(String rawToken) {
    String hash = opaqueTokenGenerator.hash(rawToken);

    EmailToken token =
        emailTokenRepository
            .findByTokenHash(hash)
            .filter(t -> t.getType() == TokenType.CONFIRM_EMAIL)
            .orElseThrow(InvalidConfirmationTokenException::new);

    if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidConfirmationTokenException();
    }

    User user =
        userRepository
            .findById(token.getUserId().getId())
            .orElseThrow(InvalidConfirmationTokenException::new);

    if (user.getEmailConfirmedAt() != null) {
      throw new EmailAlreadyConfirmedException();
    }

    Instant now = Instant.now();
    user.setEmailConfirmedAt(now);
    user.setUpdatedAt(now);
    token.setUsedAt(now);
    userRepository.save(user);
    emailTokenRepository.save(token);
  }
}
