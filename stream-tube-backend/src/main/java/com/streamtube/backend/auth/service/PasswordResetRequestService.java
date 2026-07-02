package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.common.email.AppProperties;
import com.streamtube.backend.common.email.EmailService;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@EnableConfigurationProperties(AppProperties.class)
public class PasswordResetRequestService {

  private final UserRepository userRepository;
  private final EmailTokenRepository emailTokenRepository;
  private final OpaqueTokenGenerator tokenGenerator;
  private final EmailService emailService;
  private final AppProperties appProperties;

  public PasswordResetRequestService(
      UserRepository userRepository,
      EmailTokenRepository emailTokenRepository,
      OpaqueTokenGenerator tokenGenerator,
      EmailService emailService,
      AppProperties appProperties) {
    this.userRepository = userRepository;
    this.emailTokenRepository = emailTokenRepository;
    this.tokenGenerator = tokenGenerator;
    this.emailService = emailService;
    this.appProperties = appProperties;
  }

  @Transactional
  public void requestReset(String email) {
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null || user.getEmailConfirmedAt() == null) {
      return;
    }

    Instant now = Instant.now();
    emailTokenRepository.invalidatePreviousFor(user.getId(), TokenType.RESET_PASSWORD, now);

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    EmailToken emailToken =
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.RESET_PASSWORD,
            tokens.sha256HexHash(),
            now.plus(1, ChronoUnit.HOURS));
    emailTokenRepository.save(emailToken);

    String rawToken = tokens.rawHex();
    String link = appProperties.publicUrl() + "/auth/reset-password?token=" + rawToken;
    scheduleEmail(() -> emailService.sendPasswordReset(user.getEmail(), user.getName(), link));
  }

  private void scheduleEmail(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
