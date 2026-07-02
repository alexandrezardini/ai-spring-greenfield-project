package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.exception.InvalidResetTokenException;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.auth.persistence.RefreshTokenRepository;
import com.streamtube.backend.auth.web.ResetPasswordRequest;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock private OpaqueTokenGenerator opaqueTokenGenerator;
  @Mock private EmailTokenRepository emailTokenRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private PasswordResetService passwordResetService;

  private static final String RAW = "r".repeat(64);
  private static final String HASH = "h".repeat(64);
  private static final String OLD_PASSWORD = "oldpassword";
  private static final String NEW_PASSWORD = "newpassword";
  private static final String NEW_HASH = "newhash";

  @Test
  void reset_validToken_replacesPasswordHashAndRevokesAllSessions() {
    UUID userId = UUID.randomUUID();
    User user = User.create("alice@example.com", OLD_PASSWORD, "Alice");
    user.setId(userId);
    EmailToken token = buildToken(userId, TokenType.RESET_PASSWORD, null, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(emailTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    passwordResetService.reset(new ResetPasswordRequest(RAW, NEW_PASSWORD));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(NEW_HASH);
    assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(OLD_PASSWORD);

    ArgumentCaptor<EmailToken> tokenCaptor = ArgumentCaptor.forClass(EmailToken.class);
    verify(emailTokenRepository).save(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();

    verify(refreshTokenRepository).revokeAllForUser(eq(userId), eq("PASSWORD_RESET"), any());
  }

  @Test
  void reset_unknownToken_throwsInvalidResetTokenException() {
    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> passwordResetService.reset(new ResetPasswordRequest(RAW, NEW_PASSWORD)))
        .isInstanceOf(InvalidResetTokenException.class);

    verify(userRepository, never()).save(any());
    verify(refreshTokenRepository, never()).revokeAllForUser(any(), any(), any());
  }

  @Test
  void reset_wrongTypeToken_throwsInvalidResetTokenException() {
    UUID userId = UUID.randomUUID();
    EmailToken token = buildToken(userId, TokenType.CONFIRM_EMAIL, null, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(
            () -> passwordResetService.reset(new ResetPasswordRequest(RAW, NEW_PASSWORD)))
        .isInstanceOf(InvalidResetTokenException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void reset_usedToken_throwsInvalidResetTokenException() {
    UUID userId = UUID.randomUUID();
    EmailToken token = buildToken(userId, TokenType.RESET_PASSWORD, Instant.now(), future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(
            () -> passwordResetService.reset(new ResetPasswordRequest(RAW, NEW_PASSWORD)))
        .isInstanceOf(InvalidResetTokenException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void reset_expiredToken_throwsInvalidResetTokenException() {
    UUID userId = UUID.randomUUID();
    Instant expired = Instant.now().minus(2, ChronoUnit.HOURS);
    EmailToken token = buildToken(userId, TokenType.RESET_PASSWORD, null, expired);

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(
            () -> passwordResetService.reset(new ResetPasswordRequest(RAW, NEW_PASSWORD)))
        .isInstanceOf(InvalidResetTokenException.class);

    verify(userRepository, never()).save(any());
  }

  private EmailToken buildToken(UUID userId, TokenType type, Instant usedAt, Instant expiresAt) {
    EmailToken token = EmailToken.issue(AggregateReference.to(userId), type, HASH, expiresAt);
    token.setUsedAt(usedAt);
    return token;
  }

  private Instant future() {
    return Instant.now().plus(1, ChronoUnit.HOURS);
  }
}
