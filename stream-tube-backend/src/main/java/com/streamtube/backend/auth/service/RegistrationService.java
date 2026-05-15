package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.auth.web.RegisterRequest;
import com.streamtube.backend.auth.web.RegisterResponse;
import com.streamtube.backend.common.email.AppProperties;
import com.streamtube.backend.common.email.EmailService;
import com.streamtube.backend.users.domain.Channel;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.ChannelRepository;
import com.streamtube.backend.users.service.UserRegistrationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@EnableConfigurationProperties(AppProperties.class)
public class RegistrationService {

  private final UserRegistrationService userRegistrationService;
  private final ChannelRepository channelRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailTokenRepository emailTokenRepository;
  private final OpaqueTokenGenerator tokenGenerator;
  private final EmailService emailService;
  private final AppProperties appProperties;

  public RegistrationService(
      UserRegistrationService userRegistrationService,
      ChannelRepository channelRepository,
      PasswordEncoder passwordEncoder,
      EmailTokenRepository emailTokenRepository,
      OpaqueTokenGenerator tokenGenerator,
      EmailService emailService,
      AppProperties appProperties) {
    this.userRegistrationService = userRegistrationService;
    this.channelRepository = channelRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailTokenRepository = emailTokenRepository;
    this.tokenGenerator = tokenGenerator;
    this.emailService = emailService;
    this.appProperties = appProperties;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    String name = deriveNameFromEmail(request.email());
    String passwordHash = passwordEncoder.encode(request.password());
    User user = userRegistrationService.register(request.email(), passwordHash, name);
    Channel channel =
        channelRepository.findByUserId(user.getId()).orElseThrow(IllegalStateException::new);

    OpaqueTokenGenerator.Tokens tokens = tokenGenerator.generate();
    EmailToken emailToken =
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            TokenType.CONFIRM_EMAIL,
            tokens.sha256HexHash(),
            Instant.now().plus(24, ChronoUnit.HOURS));
    emailTokenRepository.save(emailToken);

    String rawToken = tokens.rawHex();
    String link = appProperties.publicUrl() + "/auth/confirm-email?token=" + rawToken;
    scheduleEmail(() -> emailService.sendEmailConfirmation(user.getEmail(), user.getName(), link));

    return new RegisterResponse(
        user.getId(),
        user.getEmail(),
        new RegisterResponse.ChannelView(channel.getId(), channel.getHandle(), channel.getName()));
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

  private String deriveNameFromEmail(String email) {
    String prefix = email.substring(0, email.indexOf('@'));
    String normalized = prefix.toLowerCase().replaceAll("[^a-z0-9_]", "");
    if (normalized.isEmpty()) return "User";
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }
}
