package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.common.email.AppProperties;
import com.streamtube.backend.common.email.EmailService;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetRequestServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private EmailTokenRepository emailTokenRepository;
  @Mock private OpaqueTokenGenerator tokenGenerator;
  @Mock private EmailService emailService;

  private final AppProperties appProperties = new AppProperties("http://localhost:8080");

  private PasswordResetRequestService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordResetRequestService(
            userRepository, emailTokenRepository, tokenGenerator, emailService, appProperties);
  }

  @Test
  void requestReset_confirmedUser_invalidatesPreviousTokensAndSendsEmail() {
    UUID userId = UUID.randomUUID();
    User user = User.create("alice@example.com", "hash", "Alice");
    user.setId(userId);
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));

    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(tokenGenerator.generate())
        .thenReturn(new OpaqueTokenGenerator.Tokens("a".repeat(64), "b".repeat(64)));
    when(emailTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatCode(() -> service.requestReset("alice@example.com")).doesNotThrowAnyException();

    verify(emailTokenRepository)
        .invalidatePreviousFor(eq(userId), eq(TokenType.RESET_PASSWORD), any());
    verify(emailTokenRepository).save(argThat(t -> t.getType() == TokenType.RESET_PASSWORD));
    verify(emailService)
        .sendPasswordReset(
            eq("alice@example.com"),
            eq("Alice"),
            argThat(link -> link.contains("reset-password?token=" + "a".repeat(64))));
  }

  @Test
  void requestReset_unknownEmail_noTokenPersistedNoEmailSent() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    assertThatCode(() -> service.requestReset("nobody@example.com")).doesNotThrowAnyException();

    verify(emailTokenRepository, never()).invalidatePreviousFor(any(), any(), any());
    verify(emailTokenRepository, never()).save(any());
    verify(emailService, never()).sendPasswordReset(any(), any(), any());
  }

  @Test
  void requestReset_unconfirmedUser_noTokenPersistedNoEmailSent() {
    UUID userId = UUID.randomUUID();
    User user = User.create("unconfirmed@example.com", "hash", "Unconfirmed");
    user.setId(userId);

    when(userRepository.findByEmail("unconfirmed@example.com")).thenReturn(Optional.of(user));

    assertThatCode(() -> service.requestReset("unconfirmed@example.com"))
        .doesNotThrowAnyException();

    verify(emailTokenRepository, never()).invalidatePreviousFor(any(), any(), any());
    verify(emailTokenRepository, never()).save(any());
    verify(emailService, never()).sendPasswordReset(any(), any(), any());
  }
}
